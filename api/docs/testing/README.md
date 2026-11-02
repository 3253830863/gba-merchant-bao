# 后端自动化测试说明

环境要求：JDK 21、Maven 3.9.11，以及已启动 Linux 容器引擎的 Docker Desktop。首次运行时需要能够下载 Maven 依赖和指定版本的容器镜像。

在仓库根目录执行以下命令：

```powershell
# 快速单元测试和 HTTP、数据库结构契约检查，不需要 Docker。
mvn -f api/pom.xml test

# 完整验收，包含独立 MySQL、Redis 容器的集成测试。
mvn -f api/pom.xml clean verify
```

完整测试由 Testcontainers 自动创建和清理容器。你只需要启动 Docker Desktop，无需手动创建或启动测试用 MySQL、Redis 容器。宿主机端口动态分配，测试不使用本机安装的 MySQL、已有数据库或固定 Redis 实例，容器复用保持关闭。

测试不调用或复用开发、生产环境的 `docker-compose.yml`，不挂载业务数据目录或命名数据卷，不加入业务网络，也不指定业务容器名称或固定宿主机端口。`withExposedPorts(6379)` 声明的是测试容器内部的 Redis 端口，`getMappedPort(6379)` 获取的是随机分配的宿主机端口。代码已显式关闭容器复用。测试与开发容器共用 Docker 镜像缓存、引擎的 CPU 和内存资源，但中间件数据相互独立。自动化测试应在开发用 Docker Desktop 引擎上运行。

dev Compose 项目 gba-dev 通过 loopback 3307/6380 提供持久 MySQL/Redis，供 IDEA API、前端与 Apifox 联调。这些固定端口不会作为自动化测试的默认连接端口。详见 [本地运行](../deployment/local-development.md)。

加载 Spring 应用上下文的测试显式使用 test。`application-test.yml` 只位于 src/test/resources，不进入主 JAR，也无需个人 application-dev.yml。容器集成测试经 @DynamicPropertySource 注入 testcontainers 连接属性和实际动态端口；缺失属性没有指向 dev 的默认连接。HTTP 契约测试禁用 Flyway，并显式指向不可达的端口1，不使用中间件。Docker 不可用时，完整验收失败并提示启动 Docker Desktop。仅 mvn test 成功不能代表完整验收通过。

Surefire 的单元和契约测试报告位于 `api/target/surefire-reports`，Failsafe 的集成测试报告位于 `api/target/failsafe-reports`。两类 XML 报告均记录实际测试总数、失败数、错误数和跳过数。生成的报告被 Git 忽略，测试源码需要随实现一并提交。

Maven 的 Surefire、Failsafe 会将 `LOG_DIR` 设置为 `api/target/test-logs` 的绝对路径，因此测试日志不会追加到开发或生产日志中。执行 Maven 的 `clean` 阶段时，这些测试日志会被清理。应用运行时通过 `logging.file.path` 或 `LOG_DIR` 配置日志位置：工作目录设为 `api/` 时，开发日志默认写入 `api/.runtime/logs`；生产环境需要将 `LOG_DIR` 指向外部可写日志目录或部署挂载目录。在 IDE 中直接运行测试时，可以通过 JVM 系统属性 `LOG_DIR` 指定输出目录。

MySQL 测试覆盖空库迁移、重复迁移、租户测试数据的幂等性，以及模拟旧数据库结构的显式 baseline 和升级，并验证旧测试记录得到保留。Redis 测试使用保留的旧序列化方式和配置，通过实际断言验证字符串、哈希、列表、集合、有序集合和键操作。

上述测试使用构造的测试数据。生产数据库的备份、恢复演练和结构对比仍需在发布流程中单独执行，不能用这些测试替代。

## 数据分层与 fixture

| 目录 | dev 应用 | test | prod CLI |
| --- | --- | --- | --- |
| db/migration | 是 | 是 | 是 |
| db/seed/common | 是 | 是 | 是 |
| db/seed/nonprod | 是 | 是 | 否 |
| db/devdata | 是 | 否 | 否 |
| src/test/resources/db/fixture | 否 | @Sql 按用例 | 否 |

common 当前没有必需记录；nonprod 是 M_3C_DEMO 和 M_ISOLATION_TEST 两个稳定租户根，生产不加载。
商品/SKU、订单、物流、知识和会话演示数据按模块增量加入 devdata，本 change 不预建模块表。
FoundationApplicationIT 用 @Sql 加载独立的 M_FIXTURE_RUNTIME 租户，按稳定编码定位，并在 @AfterTransaction 验证事务回滚；不依赖业务表总数或空表，不清空共享种子。
HTTP/异步测试的 fixture 无法由测试事务回滚时，必须按本用例稳定业务标识限定清理。

默认 baseline-version=1 不会使空库跳过 V1；所有普通集成测试从空库按序执行 V1/V2/V3。另保留一个短小旧库接管场景，先验证非空库拒绝，再显式 baseline1 后迁移 V2/V3。
ProductionInfrastructureIT 复用生产初始化脚本验证 DML/迁移权限分离、已有数据目录不重跑 init、Redis 认证和结构＋数据导出恢复；使用临时密码和可销毁实例，不连接实际生产。

QA 可选择在 dev profile 环境人工验收，通过后再进入未来 prod；没有 QA 也可直接进入 prod，无 qa profile。本次没有云发布或 CI/tag 流水线。
资源移动后务必运行 clean verify，清除 target/classes 中残留的 application-local/test 及旧 devdata SQL，再执行 Surefire 和 Failsafe。

test profile 关闭旧业务定时调度（merchant.scheduling.enabled=false），HTTP 契约测试不会在整分钟后台连接中间件；dev/prod 保持原调度行为。
