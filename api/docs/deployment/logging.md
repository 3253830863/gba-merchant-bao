# JSON 日志与测试隔离

Logback 使用 Spring Boot 内置 StructuredLogEncoder 的 logstash JSON 格式，每行一个 JSON 事件。
MDC 的 requestId、module、errorCode（产生事件时提供）保留为字段；HTTP 完成日志记录状态与耗时。
响应 X-Request-Id 与响应体 requestId 保持既有约定。手机号在展示和日志调用前 maskPhone，不能把原始手机号、token、验证码或配置口令拼入日志。

| 文件 | 活动文件大小 | 归档保留 | 归档总量 |
| --- | --- | --- | --- |
| application.json | 100MB | 最多7天 | 2GB |
| audit.json | 100MB | 最多30天 | 1GB |

按日期和序号滚动成 .json.gz。审计 logger 不传播至 application，但继续输出控制台；llm logger 也保留 stdout。ERROR 留在 application.json，不创建 error.json。
归档总量不包括活动文件，不是目录硬配额，需关注宿主机可用空间。

dev 的 IDEA 工作目录 api，因此文件只落 api/.runtime/logs。prod 容器 /app/logs bind mount 到 ECS /opt/gba/logs/app/，容器重建不会删除宿主机日志。
MySQL/Redis 数据用命名卷；应用日志用 bind mount，两者不同。
两套 Compose 的所有服务使用 json-file，每文件20MB、最多5份。dev 的这项限制只针对容器中间件，不能限制 IDEA 应用日志；Docker stdout 轮转也不能限制 MySQL binlog。

Surefire/Failsafe 在启动测试 JVM 前将 LOG_DIR 强制设为 api/target/test-logs 的绝对路径，Logback 只创建该目录的文件 appender。
不会先落 .runtime 再切换，也不会双写开发目录。测试报告分别位于 target/surefire-reports、target/failsafe-reports；clean 会清除旧 target。
IDEA 单独运行测试需在测试运行配置 JVM options 中提供 `-DLOG_DIR=<仓库绝对路径>/api/target/test-logs`；应用运行配置不要使用这个参数。
已有开发历史 .log 文件无需删除或修改；本次测试验证其内容和时间戳不变。
