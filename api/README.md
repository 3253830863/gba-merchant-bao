# 商户宝后端

`api/` 是单 Maven 后端工程，使用 JDK 21、Spring Boot 3.5.16。新根包为 `com.gba.merchantbao`，旧餐饮实现位于 `legacy` 子包。

## 文档入口

- [包结构与兼容边界](docs/architecture/packages.md)
- [自动化测试、容器隔离和日志目录](docs/testing/README.md)
- [本地持久联调与 IDEA/Apifox](docs/deployment/local-development.md)
- [Flyway 空库与旧库接管](docs/deployment/flyway-baseline.md)
- [生产准备与未来手动发布](docs/deployment/production.md)
- [日志](docs/deployment/logging.md) / [备份恢复](docs/deployment/backup-recovery.md)
- [基础运行环境验收记录](docs/deployment/verification.md)

## 验证入口

从仓库根目录运行：

```powershell
mvn -f api/pom.xml test
mvn -f api/pom.xml clean verify
```

完整验收前启动 Docker Desktop 的 Linux 容器引擎。测试自动创建和清理隔离 MySQL/Redis 容器，无需手动启动固定中间件容器。

## 目录约定

`src/` 保存实现和测试，`docs/` 保存后端维护文档，`target/` 保存构建产物、测试报告和测试日志，`.runtime/` 保存忽略的本地运行文件。开发运行的工作目录设为 `api/`，日志在 `.runtime/logs`；生产通过 `LOG_DIR` 指定外部路径。当前 dev Compose 提供 MySQL/Redis，后续中间件随首次使用的技术模块添加。

