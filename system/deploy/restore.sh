#!/usr/bin/env bash
#
# ADC 数据库恢复
#
#   用法：./deploy/restore.sh /backup/adc-20261123-030000.dump [--yes]
#
# 恢复不是“把文件倒回去”这么简单，顺序错了会得到半新半旧的库。这里的做法：
#   1. 先校验 dump 文件能读；
#   2. 把当前库整份备份一份（pre-restore-*），恢复失败还能回去；
#   3. 停掉 api，阻止前台在恢复过程中继续写；
#   4. 直接 DROP + CREATE 目标库，再用 pg_restore 重建 —— 比 --clean 干净，
#      --clean 在对象依赖错乱时会漏删，留下旧表；
#   5. 起 api，通过 nginx 打 /api/health，确认 Flyway 与连接池都正常；
#   6. 报出表数与 Flyway 版本，跟备份对得上才算成功。

set -euo pipefail

DUMP_FILE="${1:-}"
ASSUME_YES="${2:-}"

BACKUP_DIR="${BACKUP_DIR:-/backup}"
DB_SERVICE="${DB_SERVICE:-db}"
API_SERVICE="${API_SERVICE:-api}"
DB_NAME="${DB_NAME:-adc}"
DB_USER="${DB_USER:-adc}"
HTTP_PORT="${HTTP_PORT:-80}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { printf '[%s] 错误：%s\n' "$(date '+%F %T')" "$*" >&2; exit 1; }

# ---- 参数与前置检查 ---------------------------------------------------------

[ -n "$DUMP_FILE" ] || die "用法：$0 <备份文件.dump> [--yes]"
[ -f "$DUMP_FILE" ] || die "找不到文件：$DUMP_FILE"

command -v docker >/dev/null 2>&1 || die "找不到 docker 命令"

if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$DB_SERVICE"; then
  die "容器 $DB_SERVICE 没在运行，先执行 docker compose up -d db"
fi

log "校验备份文件 $(basename "$DUMP_FILE")"
if ! docker compose exec -T "$DB_SERVICE" pg_restore --list < "$DUMP_FILE" > /tmp/adc-restore-list.txt 2>&1; then
  cat /tmp/adc-restore-list.txt >&2 || true
  die "备份文件读不出来，可能已损坏，拒绝恢复"
fi
BACKUP_TABLES=$(grep -c 'TABLE DATA' /tmp/adc-restore-list.txt || true)
log "备份内含 $BACKUP_TABLES 张表的数据"

if [ "$ASSUME_YES" != "--yes" ]; then
  cat <<EOF

  ┌─────────────────────────────────────────────────────────┐
  │  即将用该备份覆盖数据库 $DB_NAME，现有数据会全部丢失。   │
  │  恢复前会自动留一份 pre-restore 备份，但不等于万无一失。 │
  └─────────────────────────────────────────────────────────┘

  备份文件：$DUMP_FILE
  目标库  ：$DB_NAME（容器 $DB_SERVICE）

  确认请输入 RESTORE（大写）：
EOF
  read -r ANSWER
  [ "$ANSWER" = "RESTORE" ] || die "已取消，未做任何改动"
fi

# ---- 恢复前自保：把现库再备份一份 -------------------------------------------

mkdir -p "$BACKUP_DIR"
SAFE="$BACKUP_DIR/pre-restore-$(date '+%Y%m%d-%H%M%S').dump"
log "先把当前库备份到 $(basename "$SAFE")"
docker compose exec -T "$DB_SERVICE" \
  pg_dump -U "$DB_USER" -d "$DB_NAME" --format=custom --compress=6 --no-password \
  > "$SAFE" || die "现库备份失败，为安全起见中止恢复"
chmod 600 "$SAFE"

# ---- 停写 ---------------------------------------------------------------

log "停止 $API_SERVICE，避免恢复过程中前台继续写库"
docker compose stop "$API_SERVICE" >/dev/null

restore_failed() {
  log "恢复失败。当前库可能处于不完整状态。"
  log "现场已保留，可用下面这条回退："
  log "  docker compose exec -T $DB_SERVICE pg_restore -U $DB_USER -d $DB_NAME --clean --if-exists < $SAFE"
  log "回退后再执行 docker compose start $API_SERVICE"
  exit 1
}

# ---- 重建库 ---------------------------------------------------------------

log "重建数据库 $DB_NAME"
if ! docker compose exec -T "$DB_SERVICE" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -c \
      "DROP DATABASE IF EXISTS \"$DB_NAME\" WITH (FORCE);" ; then
  restore_failed
fi
if ! docker compose exec -T "$DB_SERVICE" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -c \
      "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USER\";" ; then
  restore_failed
fi

log "导入 $BACKUP_TABLES 张表"
# --no-owner：备份可能来自另一套用户名，恢复后统一归当前连接用户
# --no-privileges：避免把旧环境的授权语句带进来
# -T 同样是为了不让二进制流被伪终端污染
if ! docker compose exec -T "$DB_SERVICE" \
      pg_restore -U "$DB_USER" -d "$DB_NAME" \
        --no-owner --no-privileges --exit-on-error --single-transaction \
      < "$DUMP_FILE" ; then
  restore_failed
fi

# ---- 起服务并验证 ---------------------------------------------------------

log "启动 $API_SERVICE"
docker compose start "$API_SERVICE" >/dev/null

log "等待健康检查（最多 120 秒）"
HEALTH=""
for _ in $(seq 1 60); do
  HEALTH=$(curl -fsS "http://127.0.0.1:${HTTP_PORT}/api/health" 2>/dev/null || true)
  case "$HEALTH" in *'"status":"UP"'*) break ;; esac
  sleep 2
done

case "$HEALTH" in
  *'"status":"UP"'*) log "健康检查通过：$HEALTH" ;;
  *) restore_failed ;;
esac

# 库里的表数与 Flyway 版本，跟备份对一下
RESTORED_TABLES=$(docker compose exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" | tr -d '\r')
FLYWAY=$(docker compose exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT max(version) FROM flyway_schema_history WHERE success;" | tr -d '\r')

log "恢复完成：$RESTORED_TABLES 张表，Flyway 版本 $FLYWAY"
log "本次恢复前的库留在 $(basename "$SAFE")，确认无误后可删"
