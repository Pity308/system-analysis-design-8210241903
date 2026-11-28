# 部署：Linux + Docker Compose

对应报告 6.14.1 末尾的部署形态。三个容器：`web`（nginx 静态站 + 反代）、`api`（Spring Boot）、`db`（PostgreSQL 16）。
**只有 web 对外开端口**，数据库与后端都在 compose 内部网络里。

> 本机是 Windows，没有装 Docker，所以这套编排**没有在本机跑过**，
> 但每个文件都是按能跑的标准写的：镜像分层、健康检查、非 root 运行、密钥注入、日志滚动。
> 到云服务器上执行一次 `docker compose up -d --build` 即可验证。

## 一、服务器

| 项 | 建议 | 理由 |
| --- | --- | --- |
| 规格 | 2 核 4G，40G 系统盘 | 三个容器 + 数据库，2G 会很紧 |
| 系统 | Ubuntu 22.04 LTS | Docker 官方源支持最好 |
| 地域 | 靠近诊所（华南用广州/深圳） | 前台每次操作都是同步请求 |
| 带宽 | 3M 起步 | 页面 gzip 后主包约 100KB |

## 二、装 Docker

```bash
# 官方脚本，装完 docker 与 compose 插件
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker

# 让当前用户免 sudo（重新登录生效）
sudo usermod -aG docker $USER

docker --version && docker compose version
```

## 三、放代码、起服务

```bash
# 方式一：从 GitHub 拉（仓库里只有课程报告，代码用方式二传）
# 方式二：本地打包上传
#   Windows 上：scp -r D:\ADC root@<服务器IP>:/opt/adc
cd /opt/adc

cp .env.example .env
# 三处密钥必须改，别用示例值
sed -i "s|change-me-please$|$(openssl rand -base64 24)|" .env
vi .env

docker compose up -d --build
docker compose ps
```

首次启动会自动跑 Flyway 的 V1–V8，建 22 张表并写入种子数据（15 家诊所、8 个治疗项目、6 个员工账号）。

验证：

```bash
curl -s http://127.0.0.1/api/health          # 应为 {"status":"UP",...}
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/   # 应为 200
```

浏览器打开 `http://<服务器IP>/`，用 `linxiaoyu` 加交接文档 15.1 里的生产口令登录。

## 四、域名与 HTTPS

中国大陆的服务器**必须备案**才能用域名对外提供 HTTP 服务，这一步绕不过去，提前办（约 7–20 个工作日）。

备案下来后：

```bash
sudo apt install -y certbot
# 先停掉占用 80 的 web 容器，让 certbot 走 standalone 验证
docker compose stop web
sudo certbot certonly --standalone -d adc.example.com
docker compose start web
```

然后改两处：

1. `deploy/nginx.conf`：加 443 server 块，证书挂 `/etc/letsencrypt`（compose 里把该目录只读挂进 web）。
2. `docker-compose.yml`：`ports` 增加 `"443:443"`，并把 `HTTP_PORT` 改成 `127.0.0.1:8080`，让 80 只留给跳转与续期。

续期靠 `certbot renew`，加一条 cron 即可。

## 五、小程序上线的前置条件

微信要求 `request 合法域名` 必须是 **https + 已备案域名**，不能是 IP。所以：

- 现在只能在开发者工具里跑（勾“不校验合法域名”），或用**体验版**给老师演示；
- 域名备案 + HTTPS 就绪后，在 `apps/adc-miniapp/config.js` 把 `BASE_URL` 改成 `https://adc.example.com`，
  小程序后台配上该域名，再上传体验版。

个人主体的小程序没有医疗类目资质，无法正式发布——这点在 README 的“已知取舍”里已写明。

## 六、日常运维

```bash
# 看日志（api 的日志滚动到 5×20MB，不会撑爆磁盘）
docker compose logs -f api --tail=200

# 升级：改完代码重新构建，只重启变化的容器
git pull && docker compose up -d --build

# 进库排障
docker compose exec db psql -U adc -d adc
```

### 备份与恢复

两个脚本都在 `deploy/` 下。第 116 行那条“备份先验证恢复”不能停在口号上，所以脚本里内置了校验。

```bash
chmod +x deploy/*.sh          # 从 Windows 上传后权限会丢，先补一下

# 备份：默认写到 /backup，保留 14 天
./deploy/backup.sh
./deploy/backup.sh 30         # 改成保留 30 天

# 恢复：会先校验文件、自动备份现库、停 api、重建库、起服务、验健康
./deploy/restore.sh /backup/adc-20261123-030000.dump
```

每次备份在 `/backup/manifest.txt` 追加一行：时间、文件名、大小、sha256、表数。恢复出问题时先看这张清单对不对得上。

`backup.sh` 做三件容易被省掉的事：

1. `pg_restore --list` 把刚写出的文件读一遍——截断的 dump 在这一步就会暴露，不会等到真要恢复时才发现；
2. 数一遍 dump 里的 `TABLE DATA` 条目，少于 22 张表判定为不完整，直接删掉坏文件并非零退出；
3. 磁盘剩余不足 2GB 就不开始，避免写半个文件。

`restore.sh` 的顺序是“停 api → 校验 → 备份现库 → DROP + CREATE → `pg_restore --single-transaction` → 起 api → 打 `/api/health`”。
用重建库而不是 `pg_restore --clean`，是因为 `--clean` 在对象依赖错乱时会漏删，留下旧表；重建是唯一能保证“库里只有备份内容”的做法。
恢复失败时脚本会把回退命令打出来，现场不丢。

每天凌晨跑一次备份：

```bash
# crontab -e
0 3 * * * cd /opt/adc && ./deploy/backup.sh >> /var/log/adc-backup.log 2>&1
```

异地存一份：`rclone`、`ossutil` 或最简单的 `scp` 到另一台机器。备份和数据在同一块盘上，等于没有备份。

### 恢复演练

交付前至少做一次，别在生产库上试：

```bash
# 在测试机上起一套，把生产备份恢复进去，然后核对
./deploy/restore.sh /backup/adc-20261123-030000.dump --yes
docker compose exec db psql -U adc -d adc -c "SELECT count(*) FROM patient;"
docker compose exec db psql -U adc -d adc -c "SELECT max(version) FROM flyway_schema_history;"
```

## 七、几个容易踩的点

- **不要 `docker compose down -v`**：`-v` 会删数据卷，等于删库。日常只用 `down` 或 `restart`。
- **密钥别写进 compose 文件**：只在 `.env` 里，`.env` 已在 `.dockerignore` 与 `.gitignore` 里。
- **时区**：容器统一 `Asia/Shanghai`，否则预约时间会整体偏 8 小时——这是牙科系统最致命的错。
- **数据库不映射端口**：线上不要为了图方便把 5432 开到公网，弱口令会在几小时内被扫到。
- **备份先验证恢复**：只备份不试恢复，等于没备份。
