#!/usr/bin/env bash
#
# ADC 数据库备份
#
#   用法：./deploy/backup.sh [保留天数]
#   默认：/backup/adc-YYYYmmdd-HHMMSS.dump，保留 14 天
#
# 为什么用 -Fc（自定义格式）而不是 .sql.gz：
#   1. 自定义格式自带压缩，且能只恢复某张表；
#   2. 文件头有校验，pg_restore --list 能立刻判断备份是否完整；
#   3. 纯文本 dump 遇到中途截断时看不出坏在哪，恢复时才发现。
#
# 备份等于没备份的唯一原因是不试恢复。本脚本每次写完都会用 pg_restore --list
# 把备份过一遍，读不出来就删掉并以非零码退出，让 cron 的失败邮件叫醒人。

set -euo pipefail

RETENTION_DAYS="${1:-14}"
BACKUP_DIR="${BACKUP_DIR:-/backup}"
DB_SERVICE="${DB_SERVICE:-db}"
DB_NAME="${DB_NAME:-adc}"
DB_USER="${DB_USER:-adc}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { printf '[%s] 错误：%s\n' "$(date '+%F %T')" "$*" >&2; exit 1; }

# ---- 前置检查 ---------------------------------------------------------------

command -v docker >/dev/null 2>&1 || die "找不到 docker 命令"

if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$DB_SERVICE"; then
  die "容器 $DB_SERVICE 没在运行，先执行 docker compose up -d"
fi

mkdir -p "$BACKUP_DIR"
[ -w "$BACKUP_DIR" ] || die "$BACKUP_DIR 不可写，换个目录或调权限"

# 备份文件小、数量少，但磁盘满时 pg_dump 会写到一半失败，留下一个坏文件。
# 先看剩余空间，少于 2GB 就不开始。
AVAIL_MB=$(df -Pm "$BACKUP_DIR" | awk 'NR==2 {print $4}')
if [ "${AVAIL_MB:-0}" -lt 2048 ]; then
  die "$BACKUP_DIR 所在分区剩余 ${AVAIL_MB}MB，不足 2GB，先清磁盘"
fi

# ---- 备份 -------------------------------------------------------------------

STAMP="$(date '+%Y%m%d-%H%M%S')"
TARGET="$BACKUP_DIR/adc-$STAMP.dump"
TMP="$TARGET.part"

log "开始备份 $DB_NAME → $TARGET"

# -T 关掉伪终端：否则 docker compose exec 会在二进制流里掺入控制字符，dump 直接损坏。
# 单事务快照（pg_dump 默认）保证备份内的表之间是一致的，不会出现“预约有、患者没有”。
if ! docker compose exec -T "$DB_SERVICE" \
      pg_dump -U "$DB_USER" -d "$DB_NAME" \
        --format=custom --compress=6 --no-password \
      > "$TMP"; then
  rm -f "$TMP"
  die "pg_dump 失败（退出码非 0），未生成备份"
fi

# ---- 校验 -------------------------------------------------------------------

# pg_restore --list 会读文件头与目录表。截断、空文件、gzip 混进来都会在这里暴露。
if ! docker compose exec -T "$DB_SERVICE" pg_restore --list < "$TMP" > /dev/null 2>"$TMP.err"; then
  cat "$TMP.err" >&2 || true
  rm -f "$TMP" "$TMP.err"
  die "备份校验失败（pg_restore --list 读不出来），已删除坏文件"
fi

# 目录表里 TABLE DATA 的条数，用来跟库里的表数对一下，防止只 dump 了结构
TABLE_COUNT=$(docker compose exec -T "$DB_SERVICE" pg_restore --list < "$TMP" \
  | grep -c 'TABLE DATA' || true)

if [ "${TABLE_COUNT:-0}" -lt 20 ]; then
  rm -f "$TMP" "$TMP.err"
  die "备份里只有 ${TABLE_COUNT} 张表的数据，少于预期的 22 张，判定为不完整"
fi

mv "$TMP" "$TARGET"
rm -f "$TMP.err"
chmod 600 "$TARGET"

SIZE=$(du -h "$TARGET" | awk '{print $1}')
SHA=$(sha256sum "$TARGET" | awk '{print $1}')

# 一行清单，出问题时先看这里：哪个时间点、多大、多少表、指纹
printf '%s  %s  %s  %s  tables=%s\n' \
  "$STAMP" "$(basename "$TARGET")" "$SIZE" "$SHA" "$TABLE_COUNT" \
  >> "$BACKUP_DIR/manifest.txt"

log "备份完成：$SIZE，$TABLE_COUNT 张表，sha256=${SHA:0:16}…"

# ---- 清理过期 ---------------------------------------------------------------

# 只删本脚本产出的 adc-*.dump，别的文件一概不碰
DELETED=$(find "$BACKUP_DIR" -maxdepth 1 -name 'adc-*.dump' -type f \
            -mtime "+$RETENTION_DAYS" -print -delete | wc -l)
[ "$DELETED" -gt 0 ] && log "清理 $RETENTION_DAYS 天前的备份 $DELETED 个"

log "现有备份 $(find "$BACKUP_DIR" -maxdepth 1 -name 'adc-*.dump' -type f | wc -l) 个，保留策略 ${RETENTION_DAYS} 天"
