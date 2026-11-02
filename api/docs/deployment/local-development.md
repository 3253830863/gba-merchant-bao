# 本地持久联调

开发环境统一叫 dev；test 专用于自动化测试，prod 用于未来生产。Spring profile 与 Compose 的 deploy profile 是两种概念。

## 首次配置（仓库根 PowerShell）

```powershell
Copy-Item .env.example .env
Copy-Item api/src/main/resources/application-dev.yml.example api/src/main/resources/application-dev.yml
```

编辑被忽略的实际文件：根 `.env` 中 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD 初始均填 `123456`；dev YAML 的 merchant.datasource.password 同样填此值。
两处保持数据库 gba_dev、用户 dev_admin、MySQL 端口 3307、Redis 端口 6380 一致。端口被占用时，修改 .env 的 MYSQL_PORT/REDIS_PORT 并同步 YAML。
dev Redis 无密码；.env 不添加 REDIS_PASSWORD，YAML 不添加 Redis password。AOF 与命名卷负责持久化。
`.env` 只给 Compose 插值，Spring Boot 不会读取它；IDEA 不需要加载 .env 或注入额外环境变量。

日常启动只需要在仓库根执行下面的命令；`docker compose up` 会读取并解析 Compose 配置。

```powershell
docker compose up -d
docker compose ps
```

首次创建 Compose 文件、修改 Compose 配置或排查配置问题时，可以单独执行
`docker compose config --quiet`。它是配置检查命令，不是每天启动 dev 的前置步骤。

当前 dev Compose 只包含 MySQL 和 Redis，没有需要显式激活的 Compose profile。
后续某个技术模块首次引入 RabbitMQ、登录会话 Redis 等中间件时，才为该模块声明独立
profile，并使用明确的名称启动，例如 `docker compose --profile login up -d`。
不要使用 `docker compose --profile "*" up -d`，以免把所有尚未验收的模块依赖一起启动。

IDEA 运行 MerchantApplication，JDK 21，工作目录设为仓库的 api，Active profiles 只填 dev。
也可从 api 目录运行：

```powershell
mvn spring-boot:run '-Dspring-boot.run.arguments=--spring.profiles.active=dev'
```

首次启动空库自动执行 V1→V2→V3 和适用种子，无需 baseline。仅激活 dev 就能运行，无需个人旧库或云端 OSS/微信凭据；占位外部配置不表示相应业务可用。
前端及 Apifox 的 API base URL 为 http://localhost:8080，健康检查 GET /api/v1/health；文档 /swagger-ui.html、/v3/api-docs.yaml。
数据库工具连接 127.0.0.1:3307/gba_dev，dev_admin；Redis 工具连接 127.0.0.1:6380，无认证。前端调用 API，不直接连接数据库。
多个前端/后端开发者各自建立空库；共享联调如需 API 远程可达，单独约定主机地址。中间件保持 loopback。

## 日常启动和已有卷

```powershell
docker compose up -d --wait
docker compose restart mysql redis
# 可重建容器，保留命名卷
docker compose up -d --force-recreate --wait
# 停止时保留数据
docker compose down
```

如果需要等待健康检查完成，可在确认配置后使用 `docker compose up -d --wait`；它是可选的
等待步骤。Docker Desktop 启动后，推荐顺序是：启动 Compose 中间件、在 IDEA 激活 Spring
`dev`、再启动后端。后端通过 `dev_admin` 连接数据库，Flyway 会自动执行待执行迁移，随后即可
进行断点调试。前端和 Apifox 使用同一 dev API，不连接 Testcontainers。

项目名 gba-dev，对应 gba-dev_mysql_data / gba-dev_redis_data，与 prod 的 gba 分离。
正常流程不使用 down -v。改变 .env 或初始化脚本不会修改已有 MySQL 卷账号；管理员连接正确实例显式 ALTER USER/授权，再同步实际 YAML 和 .env，不能删卷来改密码。
例：先用管理员会话执行 `ALTER USER 'dev_admin'@'%' IDENTIFIED BY '<新密码>';`，确认新连接可用再更新应用。不要把口令写到命令历史、日志或提交中。
改数据库名也不会自动为已有卷创建新库，需先规划迁移并由管理员处理。

持久化验收应在明确的 dev 目标内创建独立临时表/键，重启和重建后读取，最后仅定向清理自己的表/键；不要对联调业务表 TRUNCATE。
自动化集成测试使用动态端口的 Testcontainers，可与 dev 同时运行；它不挂载这些卷。

## 配置与凭据边界

根 .env、实际 dev YAML、backups/、api/.runtime/ 和实际生产 .env 均忽略 Git。
dev YAML 示例位于 `api/src/main/resources/application-dev.yml.example`，主资源 application-prod.yml 仅保留环境变量占位符，可以提交并进入 JAR。
生产文件统一维护在 `api/docs/prod-profile/opt/gba/`：Compose、.env.example、Dockerfile、.dockerignore 和账号初始化脚本可提交；实际 .env、app.jar、db/ 下的发布 SQL 和备份仍被忽略。`api/.gitignore` 按文件放行，不再维护 deployment/examples/prod 生产环境副本。
`api/docs/prod-profile/Docker开发和生产环境.txt` 是可提交的操作手稿。任何含实际凭据的额外配置不得放入放行列表。

```powershell
git check-ignore .env api/src/main/resources/application-dev.yml api/docs/prod-profile/opt/gba/.env backups/check.sql
git check-ignore api/docs/prod-profile/opt/gba/app/app.jar api/docs/prod-profile/opt/gba/db/migration/V1__legacy_schema_baseline.sql
# 上述两条命令应输出被忽略路径，发布产物即使存在也不进入 Git。
git check-ignore api/docs/prod-profile/opt/gba/docker-compose.yml api/docs/prod-profile/opt/gba/.env.example api/docs/prod-profile/opt/gba/app/Dockerfile api/docs/prod-profile/opt/gba/app/.dockerignore api/docs/prod-profile/opt/gba/docker/mysql/init/01-create-users.sh api/docs/prod-profile/Docker开发和生产环境.txt
# 最后一条预期无输出、退出码 1，说明这些文件可提交。
```

历史配置中曾出现的真实数据库、OSS、微信等凭据，应到各服务提供方轮换；删除当前文件中的旧值不会让历史凭据失效。
开发日志 api/.runtime/logs，Maven 测试只写 api/target/test-logs，详见 [日志](logging.md)、[测试](../testing/README.md)。
