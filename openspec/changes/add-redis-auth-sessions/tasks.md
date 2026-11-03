# Tasks

## 1. 业务需求与 API 契约

- [ ] 1.1 交付 `api/docs/auth/requirements.md`：写清消费者显式注册/登录、商户内员工开通/独立登录、当前身份/退出用例，冻结两类角色、商户内消费者模型及合并员工共享开通权；逐项对照 `user-auth` 场景，确认没有匿名员工注册、自动消费者注册或平台管理员实现。
- [ ] 1.2 交付 `api/docs/auth/api-design.md`，按 design 的 9 个操作登记请求/响应、校验、权限、201 Location、Cache-Control、HTTP/业务码与失败例；核对消费者/员工开通均不自动签 token，商户/主体/角色字段不可由客户端注入。
- [ ] 1.3 在旧接口迁移记录登记新旧认证头、路径、响应、token 不互通、旧会话需重新登录及 echo 开始返回 401 的变化；核对旧 `/admin`、`/user` 仍保留契约且不宣称已具有新租户授权。

## 2. Flyway 表结构、索引与数据边界

- [ ] 2.1 新增 `V4__auth_accounts.sql` 创建 consumer_account 与 merchant_staff_account，包含非空租户、租户内唯一手机号/用户名、组合资源键、员工创建者同租户引用、状态/时间/版本字段；用 MySQL Testcontainers 检查约束和跨租户关系拒绝，验证已发布 V1/V2/V3 校验和不变。
- [ ] 2.2 新增最小 Snowflake ID 生成边界及明确 worker-id 配置，供认证新记录使用；单元验证并发唯一、时钟回退拒绝及字符串化 API ID，文档说明多实例 worker 不可重复。
- [ ] 2.3 新增 auth Mapper/XML 与租户解析边界，更新新旧 Mapper 扫描和递归 XML 路径；以真实索引查询验证消费者手机号、员工用户名/手机号及租户资源定位，确认更新/删除与 created_by 写入不能跨租户。
- [ ] 2.4 在 `src/test/resources/db/fixture/auth_accounts.sql` 提供独立测试账号：两个租户各有员工、样板租户有两名消费者、对照租户有复用手机号的消费者；由测试 `@Sql` 显式加载，独立于 devdata 和演示密码，按商户编码解析租户，验证唯一性、跨租户隔离与资源归属，fixture 事务回滚或按稳定标识清理，不清空业务表。
- [ ] 2.5 扩展迁移集成验证为空库 V1→V4、已登记 V3→V4、重复 migrate 不重建/改写数据；修正基础启动测试硬编码迁移条数为版本/成功状态检查，继续保留未登记非空旧库拒绝与显式 baseline 场景。
- [ ] 2.6 交付 `api/docs/auth/data-design.md` 的表/字段/索引部分，解释每个索引服务的查询、规范化手机号原值与展示掩码边界、旧表不迁入、迁移备份和向前修复；核对 migration 仅 DDL，认证演示账号未放 common/nonprod，测试账号未放任何 Flyway seed。

## 3. JWT、运行配置与兼容密钥

- [ ] 3.1 新增 auth 自有 JWT 配置和启动校验，属性使用 `merchant.auth.jwt.merchant-staff-secret-base64` 与 `consumer-secret-base64`；单元覆盖缺失、占位、非法编码、少于 32 字节和两 key 相同均启动失败，错误信息不含密钥值。
- [ ] 3.2 实现显式 HS256 签名/验证、issuer/audience、sub/merchantId/role/sid/jti/iat/exp 与固定 7200 秒有效期；单元覆盖伪造签名、算法降级/换算法、错误受众/发行者、过期、未知角色、缺失/不一致 claims，禁止未验签角色建立身份。
- [ ] 3.3 移除共享 YAML 的弱默认，将旧 merchant.jwt 配置桥接到新来源；legacy JwtUtil 同步为 Base64 解码与 HS256，去掉短口令补长；验证旧协议正常签验、新旧 token 交叉使用均被拒绝，并检查旧路径/头/envelope 不变。
- [ ] 3.4 更新 dev YAML 示例、test-only 配置、prod YAML 与 prod YAML 示例、生产 `.env.example` 注释；test 使用独立测试 key，prod 保留 ADMIN_JWT_SECRET/USER_JWT_SECRET 变量映射；配置测试确认实际 dev 配置不进 JAR，prod 无 dev fallback，JWT 不进入根 dev `.env`/Compose。
- [ ] 3.5 在被忽略的实际 `api/src/main/resources/application-dev.yml` 中本地生成两把不同的安全随机 32 字节 Base64 key；保留已有合规值及其他用户配置，不在输出中打印 key；通过只输出成功状态的校验和 `git check-ignore` 确认强度、区分及未跟踪。
- [ ] 3.6 更新 `api/docs/deployment/local-development.md` 与根 README 的 JWT 操作：两次 openssl、正确属性、员工非 root、改 YAML 重启 API、现有 gba-dev 使用 up -d、Compose 配置变化与 restart 区别；逐条核对命令和真实服务结构，移除登录需要重复 Redis/login profile 的旧说明，不重建或删除本地卷。

## 4. 模拟短信、一次性挑战与限流

- [ ] 4.1 实现手机号/商户编码/用户名规范化与密码输入规则，扩展手机号展示脱敏适配 +86；单元验证等价号码合并、非法号码、用户名/手机号无歧义、密码不 trim 且 UTF-8 超 72 字节拒绝，原值不进入普通响应。
- [ ] 4.2 实现可替换 SmsSender、dev mock、test 替身和 prod disabled 模式；配置测试验证只有 dev/test 可 mock，dev 显式开关才回显 debugCode，prod mock/回显启动失败，disabled 返回 503 且不伪报发送成功。
- [ ] 4.3 实现六位安全随机验证码、300 秒 TTL、商户/手机号/REGISTER-LOGIN 范围及摘要存储；Redis IT 验证跨用途/跨租户/跨手机号拒绝、过期无效、重发失效、Redis 内无明文验证码。
- [ ] 4.4 实现 Lua 原子校验、失败计数、第五次失效、成功一次消费与 challengeId 安全激活/清理；Redis 并发 IT 证明同验证码最多一个请求成功，发送失败不残留可用挑战且不会误删下一代挑战。
- [ ] 4.5 实现发送冷却、手机号跨用途合并配额、IP 发送/登录配额和员工失败锁定，原子维护 TTL，429 返回 Retry-After；验证并发超限、窗口恢复、用户名/手机号共享员工计数、未注册账号 dummy BCrypt、伪造 X-Forwarded-For 不绕过。
- [ ] 4.6 将 key 命名空间、类型、TTL、限流默认值及 mock 操作写入 data-design/api-design；验证新 StringRedisTemplate 与旧序列化并存，短信普通日志无原始手机号、code 或 body，不调用真实短信 SDK。

## 5. 账号用例与登录会话

- [ ] 5.1 实现消费者 REGISTER 独立注册和 LOGIN 已注册登录，未知身份字段返回 400；单元/数据库 IT 验证注册 201 不含 token、登录不自动建号、重复注册 409、并发唯一约束以及消费验证码后数据库失败返回 503 且不恢复挑战。
- [ ] 5.2 实现商户员工受控开通，用服务端商户与创建者写入 BCrypt cost=12 摘要，固定员工角色；单元/数据库 IT 验证同商户创建、唯一冲突、跨租户创建关系拒绝、响应无密码/hash、原始密码不留存。
- [ ] 5.3 实现员工商户编码+用户名/手机号+密码登录；验证两标识定位同一员工、同名跨商户隔离，未知商户/账号、错密码、账号或商户停用均返回相同 401/11001，平台管理员角色无登录入口。
- [ ] 5.4 实现 Redis 活跃会话创建/读取与固定绝对过期，value 与 JWT identity 全字段匹配，多端独立 sid；Redis IT 验证 TTL 对齐、不滑动续期、无完整 token 落库、会话缺失 401、会话写入失败 503 且响应无 token。
- [ ] 5.5 实现当前身份与当前会话退出，严格区分 logout 的“已验签但会话已不存在”幂等情况；集成验证重复退出 200、退出后新请求 401、第二设备仍有效、过期/无 token 401、Redis 删除失败 503，不扩大撤销范围。
- [ ] 5.6 补齐账号与会话用例的维护说明和密码交付边界，说明本期不包含生产首员工/租户开通、找回密码、员工分权和状态管理 API；核对实现与 requirements/api-design/data-design 一致。

## 6. 安全链与三层授权

- [ ] 6.1 引入 Boot 管理版本的 Security starter，建立 `/api/v1/**` stateless 链与兼容链，只白名单 health 和四个匿名认证操作；HTTP 测试验证默认保护、无 form/basic 登录页或 cookie session，旧路由仍走既有拦截器，dev/test 文档仍可访问。
- [ ] 6.2 将 RequestIdFilter 排在 Security 前，避免认证 Filter 被重复注册；统一过滤器/Controller 的 400/401/403/404/409/429/503 envelope，并扩展错误码接口；契约测试验证每种错误的头/body requestId 一致、不返回 HTML 或堆栈。
- [ ] 6.3 建立不可变 Actor 与资源范围边界，每次保护请求读取 Redis 并确认 MySQL 账号/商户状态；IT 验证账号/商户停用后新请求 401、MySQL/Redis 断连 503、恢复后可重试，不用陈旧 token 或内存存储放行。
- [ ] 6.4 接通 9 个 API 的 Controller/DTO/Springdoc 注解和严格未知字段校验，拒绝客户端身份覆盖；HTTP/集成测试覆盖匿名开通员工 401、消费者开通员工 403、员工伪造角色/商户字段 400、创建成功 201 Location 及 no-store。
- [ ] 6.5 实现 staff-accounts 同商户资源查询及 consumer-accounts 同商户本人查询，SQL 带租户与必要的归属；测试本人 200、同商户他人/跨商户/不存在相同 404、角色越权 403，不通过先全局查详情泄露存在性。
- [ ] 6.6 更新 echo 契约测试为匿名 401、已认证非法输入 400；验证 SecurityContext 和 legacy BaseContext 在正常/异常请求结束后清理，连续复用线程的请求不会继承上一主体，现有旧接口回归通过。
- [ ] 6.7 在包结构文档记录 auth 入口与后续领域调用约定；以依赖检查确认新 auth 不导入 legacy 账号/Mapper/上下文，不直接为未实现的订单或 Agent 添加持久化模型。

## 7. 开发账号、审计与人工联调

- [ ] 7.1 新增 dev-only `R__auth_demo_accounts.sql`，按商户编码给两个既有 nonprod 租户提供首员工、样板双消费者及对照同手机号消费者，员工存 BCrypt 摘要；验证仅 dev 加载、重复初始化不覆盖凭证/状态，test/prod 均不加载演示数据；对照 `2.4` 验证测试账号只由独立 `@Sql` fixture 提供，生产开通延期。
- [ ] 7.2 接入独立 auth audit logger 到既有 audit.json，补齐 module、event、requestId、可用 actor/merchant、code 与 duration；日志输出测试验证无密码/hash/token/验证码/原始手机号、无 application/audit 重复落盘，legacy 登录 DTO/微信 code 直打日志已移除。
- [ ] 7.3 交付 `api/docs/auth/manual-verification.md`：列明 dev 账号、仅开发用密码、模拟取码、手动注册/开通、两类登录、me、资源隔离、多设备退出的完整请求与预期；用隔离验收实例逐步执行确认，不用自动化测试写入 gba-dev 业务卷。
- [ ] 7.4 导出实际 Springdoc `docs/api/auth-v1.openapi.yaml` 并做差异检查，确认 9 操作、具体 DTO/envelope、Bearer、各 HTTP/业务码与可选 dev debugCode；更新 API 契约记录/Apifox 场景说明与文档导航，不把旧快照当本次实现证据。
- [ ] 7.5 更新测试/备份恢复说明：完整 Testcontainers 隔离与 target 日志、认证 Redis 丢失重新登录、恢复历史快照后 auth 受控失效防止撤销回滚；在自己的隔离实例验证，不执行开发/生产全库 flush 或恢复真实卷。

## 8. 后续模块路线与完整验收

- [ ] 8.1 将 design 第 9 节整理为 `api/docs/architecture/module-roadmap.md` 并添加 README 导航；核对认证→商品/库存→只读订单/物流→知识文档的业务顺序及四个亮点各自最低前置，明确 MQ 不依赖 FC、ChatMemory 不等待前三亮点；不实现路线中的后续模块。
- [ ] 8.2 路线标注每模块仍按需求→API→表/索引→实现→测试/联调推进，四亮点独立真实评测，CI 与真实短信/部署另开 change；核对没有消费者下单支付、订单超时 MQ、平台后台、RBAC 或未使用中间件混入当前范围。
- [ ] 8.3 运行 `mvn -f api/pom.xml test`，再运行 `mvn -f api/pom.xml clean verify`；确认单元/HTTP、MySQL/Flyway、Redis、认证并发和故障 IT 实际执行，Docker 不可用明确失败，不连接固定开发端口或业务卷，报告与日志仅在 target。
- [ ] 8.4 汇总本 change 的真实验证结果、迁移/契约差异、手动联调与已知限制；运行 `openspec validate add-redis-auth-sessions --strict`，核对四份工件及维护文档一致，确认实际 dev key 被忽略、无凭据进入 diff，且没有执行真实生产发布。
