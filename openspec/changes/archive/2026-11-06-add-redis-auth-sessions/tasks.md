# Tasks

## 1. 业务需求与 API 契约

- [x] 1.1 交付并按本次确认修订 `api/docs/auth/requirements.md`：冻结消费者店铺首页首次短信登录自动建号、员工受控开通/密码登录、双 token 生命周期、店铺定位/三层授权、localStorage 接入与退出；对照 user-auth 全部场景，移除独立消费者注册和 REGISTER 挑战，保留 MERCHANT_STAFF 与生产首管理员延期。
- [x] 1.2 交付并修订 `api/docs/auth/api-design.md`：登记更新后的 9 操作、店铺匿名短信/登录、统一 refresh/logout、双 token DTO、请求校验、员工 201 Location、no-store、HTTP/业务码与失败例；明确 idle 恢复不等于重新登录、absolute 固定、刷新重试/重放和退出原子边界。
- [x] 1.3 修订旧接口迁移记录：登记消费者新店铺路径/自动建号、双 token 与刷新/退出差异、新旧头/envelope/token 隔离、旧会话重新登录与 POST echo 匿名 401；明确均是计划，保留旧 /admin、/user 契约，不宣称已实现新授权。

## 2. Flyway 表结构、索引与数据边界

- [x] 2.1 新增 `V4__auth_accounts.sql` 创建 consumer_account 与 merchant_staff_account，包含非空租户、租户内唯一手机号/用户名、组合资源键、员工创建者同租户引用、状态/时间/版本字段；用 MySQL Testcontainers 检查约束和跨租户关系拒绝，验证已发布 V1/V2/V3 校验和不变。
- [x] 2.2 新增最小 Snowflake ID 生成边界及明确 worker-id 配置，供认证新记录使用；单元验证并发唯一、时钟回退拒绝及字符串化 API ID，文档说明多实例 worker 不可重复。
- [x] 2.3 新增 auth Mapper/XML 与租户解析边界，更新新旧 Mapper 扫描和递归 XML 路径；以真实索引查询验证消费者手机号、员工用户名/手机号及租户资源定位，确认更新/删除与 created_by 写入不能跨租户。
- [x] 2.4 在 `src/test/resources/db/fixture/auth_accounts.sql` 提供独立测试账号：两个租户各有员工、样板租户有两名消费者、对照租户有复用手机号的消费者；由测试 `@Sql` 显式加载，独立于 devdata 和演示密码，按商户编码解析租户，验证唯一性、跨租户隔离与资源归属，fixture 事务回滚或按稳定标识清理，不清空业务表。
- [x] 2.5 扩展迁移集成验证为空库 V1→V4、已登记 V3→V4、重复 migrate 不重建/改写数据；修正基础启动测试硬编码迁移条数为版本/成功状态检查，继续保留未登记非空旧库拒绝与显式 baseline 场景。
- [x] 2.6 交付 `api/docs/auth/data-design.md` 的表/字段/索引部分，解释每个索引服务的查询、规范化手机号原值与展示掩码边界、旧表不迁入、迁移备份和向前修复；核对 migration 仅 DDL，认证演示账号未放 common/nonprod，测试账号未放任何 Flyway seed。

## 3. JWT、运行配置与兼容密钥

- [x] 3.1 新增 auth 自有 JWT 配置和启动校验，属性使用 `merchant.auth.jwt.merchant-staff-secret-base64` 与 `consumer-secret-base64`；单元覆盖缺失、占位、非法编码、少于 32 字节和两 key 相同均启动失败，错误信息不含密钥值。
- [x] 3.2 实现显式 HS256、issuer/audience、sub/merchantId/role/sid/jti/iat/exp，access 标称 7200 秒且不越 original absolute，jti 与 sid 不再等同；单元覆盖伪造/算法降级、错误发行者/受众、过期、未知角色、缺失/不一致 claims、临近 absolute 截短与无未验签身份。
- [x] 3.3 移除共享 YAML 的弱默认，将旧 merchant.jwt 配置桥接到新来源；legacy JwtUtil 同步为 Base64 解码与 HS256，去掉短口令补长；验证旧协议正常签验、新旧 token 交叉使用均被拒绝，并检查旧路径/头/envelope 不变。
- [x] 3.4 更新 dev YAML 示例、test-only 配置、prod YAML 与 prod YAML 示例、生产 `.env.example` 注释；test 使用独立测试 key，prod 保留 ADMIN_JWT_SECRET/USER_JWT_SECRET 变量映射；配置测试确认实际 dev 配置不进 JAR，prod 无 dev fallback，JWT 不进入根 dev `.env`/Compose。
- [x] 3.5 在被忽略的实际 `api/src/main/resources/application-dev.yml` 中本地生成两把不同的安全随机 32 字节 Base64 key；保留已有合规值及其他用户配置，不在输出中打印 key；通过只输出成功状态的校验和 `git check-ignore` 确认强度、区分及未跟踪。
- [x] 3.6 更新 `api/docs/deployment/local-development.md` 与根 README 的 JWT 操作：两次 openssl、正确属性、员工非 root、改 YAML 重启 API、现有 gba-dev 使用 up -d、Compose 配置变化与 restart 区别；逐条核对命令和真实服务结构，移除登录需要重复 Redis/login profile 的旧说明，不重建或删除本地卷。

## 4. 模拟短信、一次性挑战与限流

- [x] 4.1 实现手机号/merchantCode/公开 storeSlug 别名/用户名规范化和密码输入规则，手机号脱敏支持 +86；单元验证等价号码、非法号码、slug 只定位 ACTIVE 商户、无上下文拒绝、用户名无歧义、密码不 trim 且 UTF-8 超 72 字节拒绝。
- [x] 4.2 实现可替换 SmsSender、dev mock、test 替身和 prod disabled 模式；配置测试验证只有 dev/test 可 mock，dev 显式开关才回显 debugCode，prod mock/回显启动失败，disabled 返回 503 且不伪报发送成功。
- [x] 4.3 实现六位安全随机验证码、300 秒 TTL、商户/手机号/LOGIN 绑定和摘要存储，删除 REGISTER 分支及客户端 purpose 输入；Redis IT 验证跨租户/手机号、过期/重发/重放拒绝，存储无明文验证码。
- [x] 4.4 实现 Lua 原子校验、失败计数、第五次失效、成功一次消费与 challengeId 安全激活/清理；Redis 并发 IT 证明同验证码最多一个请求成功，发送失败不残留可用挑战且不会误删下一代挑战。
- [x] 4.5 实现发送冷却、手机号/IP 发送配额、登录/刷新 IP 配额与员工失败锁定，原子 TTL，429/Retry-After；验证并发超限/恢复、用户名手机号共享计数、未知账号 dummy BCrypt、伪造 X-Forwarded-For 不绕过。
- [x] 4.6 将 key 命名空间、类型、TTL、限流默认值及 mock 操作写入 data-design/api-design；验证新 StringRedisTemplate 与旧序列化并存，短信普通日志无原始手机号、code 或 body，不调用真实短信 SDK。

## 5. 账号用例与登录会话

- [x] 5.1 实现消费者单一 LOGIN 流程：店铺路径解析、验证后首次建号/已有账号复用、停用禁止重建；数据库 IT 覆盖独立有效挑战并发唯一冲突复用账号、同挑战重放无效、均返回 200、无注册 409；数据库失败时验证码不恢复，账号提交后 Redis 失败保留账号、503 且无双 token。
- [x] 5.2 实现商户员工受控开通，用服务端商户与创建者写入 BCrypt cost=12 摘要，固定员工角色；单元/数据库 IT 验证同商户创建、唯一冲突、跨租户创建关系拒绝、响应无密码/hash、原始密码不留存。
- [x] 5.3 实现员工商户编码+用户名/手机号+密码登录；验证两标识定位同一员工、同名跨商户隔离，未知商户/账号、错密码、账号或商户停用均返回相同 401/11001，平台管理员角色无登录入口。
- [x] 5.4 实现双 token 初始会话原子创建：随机独立 sid、7200 秒 access、256 位随机 opaque refresh 摘要、active/control/refresh 索引；Redis IT 验证消费者 idle7d/absolute30d、员工 idle1d/absolute7d、写入失败无任一 token、独立设备并存、无明文凭证。
- [x] 5.5 实现 me 与统一 logout：access 缺失/无效/过期支持 body refresh、历史已知摘要定位同 sid、双凭证不符拒绝、原子撤销 active/refresh 并清重试缓存、墓碑保留至原 absolute；IT 验证重复 200、未知/绝对过期 401、停用仍可退、其他设备/店铺/类型不变、删除失败 503、退出刷新竞争无复活。
- [x] 5.6 补齐账号/会话维护说明、密码交付边界与浏览器按店铺+类型 localStorage 接入契约，明确 dev/test 和短期生产及 XSS 风险；Cookie 分离存储、生产首员工、找回密码/分权/状态管理延期，核对 requirements/api-design/data-design 一致。

- [x] 5.7 实现统一 refresh 的当前摘要/generation 原子轮换与 ACTIVE 状态检查，保持 original absolute；IT 用可控时间验证 access 两小时、idle7d/1d、active 缺失恢复、30d/7d 绝对截止、主体停用/元数据丢失拒绝与故障 503。
- [x] 5.8 实现 refreshRequestId 默认 10 秒幂等与 AES-256-GCM 短期响应缓存，角色 key 经 HKDF 域分离派生、AAD 绑定 sid/generation/requestId/absoluteAt，随机 nonce、无明文 token；验证同旧摘要+同 requestId+当前代才同结果、重试不 touch、过窗/异 requestId/旧代真实重放撤销当前 family、未知随机值不误撤销、缓存篡改/过期和退出后不可取回。
- [x] 5.9 明确并实现 idle touch 策略：成功完成允许的私有业务操作或必要 refresh 可推进，失败/匿名/me/已定义后台轮询不 touch，期限 capped absolute；原子核验 control 未撤销与 active 存在再更新，验证退出前在途业务不能在退出后重建 active；测试成功与失败/探测差异，文档说明后端无法判断真人，前端禁无条件保活，不能信客户端 active 标记。

## 6. 安全链与三层授权

- [x] 6.1 建立 /api/v1/** stateless Security 链与 legacy 兼容链，匿名仅 health、店铺短信/消费者登录、员工登录；refresh/logout 进入专用凭证验证分支而非匿名授权。HTTP 测试覆盖无效/过期 access 不阻断合法 refresh/退出、默认保护、无 form/basic/cookie session、旧路由与文档规则保持。
- [x] 6.2 将 RequestIdFilter 排在 Security 前，避免认证 Filter 被重复注册；统一过滤器/Controller 的 400/401/403/404/409/429/503 envelope，并扩展错误码接口；契约测试验证每种错误的头/body requestId 一致、不返回 HTML 或堆栈。
- [x] 6.3 建立不可变 Actor 与资源边界，每次保护请求读 Redis 并确认 MySQL 账号/商户状态；通用私有租户只取 Actor，公开店铺从 URL 解析；IT 验证停用 401、依赖故障 503、不能用 token 重建失去 control/refresh 的会话。
- [x] 6.4 接通更新后 9 API 的 Controller/严格 DTO/Springdoc，替换旧消费者注册契约，refresh 不接收店铺/角色；HTTP 测试覆盖首次自动建号 200、员工创建 201 Location、注入 400、匿名 401/角色 403、no-store 与双 token 字段/截止时间。
- [x] 6.5 实现 staff-accounts 同租户、consumer-accounts 同租户本人 SQL 范围；测试资源越界/不存在相同 404、角色越权 403；若入口有显式保护店铺路径则 token 租户不符 403。说明通用路由误收 A token 只能处理 A 身份，不虚构 B 页面校验，不实现订单示例。
- [x] 6.6 更新 echo 契约测试为匿名 401、已认证非法输入 400；验证 SecurityContext 和 legacy BaseContext 在正常/异常请求结束后清理，连续复用线程的请求不会继承上一主体，现有旧接口回归通过。
- [x] 6.7 在包结构文档记录 auth 入口与后续领域调用约定；以依赖检查确认新 auth 不导入 legacy 账号/Mapper/上下文，不直接为未实现的订单或 Agent 添加持久化模型。

## 7. 开发账号、审计与人工联调

- [x] 7.1 新增 dev-only `R__auth_demo_accounts.sql`，按商户编码给两个既有 nonprod 租户提供首员工、样板双消费者及对照同手机号消费者，员工存 BCrypt 摘要；验证仅 dev 加载、重复初始化不覆盖凭证/状态，test/prod 均不加载演示数据；对照 `2.4` 验证测试账号只由独立 `@Sql` fixture 提供，生产开通延期。
- [x] 7.2 接入独立 auth audit logger 到既有 audit.json，补齐 module、event、requestId、可用 actor/merchant、code 与 duration；日志输出测试验证无密码/hash/token/验证码/原始手机号、无 application/audit 重复落盘，legacy 登录 DTO/微信 code 直打日志已移除。
- [x] 7.3 交付 manual-verification.md：dev 账号/仅开发密码、模拟取码/首次建号、员工开通/两类登录、me、刷新/退出、资源隔离、多设备；列出前端 A→B→A、多 tab/重开、idle 恢复/absolute 重登、刷新协调/迟到响应及 localStorage 风险的接入验收，不宣称已有前端实现；在隔离后端实例执行可运行步骤，不写 gba-dev 业务卷。
- [x] 7.4 实现后导出 Springdoc docs/api/auth-v1.openapi.yaml，核对更新后 9 操作（无独立注册，有统一 refresh）、双 token DTO、Bearer/refresh body、HTTP/业务码/dev debugCode，更新 API 记录/Apifox 与导航，不拿旧快照作为实现证据。
- [x] 7.5 更新测试/备份恢复说明：active idle 丢失可有效 refresh 恢复，control/refresh 全丢失须重登；Redis 故障 503、历史快照恢复后受控失效防撤销回滚；在隔离实例验证，不 flush 开发/生产库或恢复真实卷。

## 8. Flyway 种子排序与已有开发库受控升级

本组采用已确认的“统一新文件名 + 一次性受控升级已有开发库 + 保留真实历史记录”。仓库不保留运行期旧名兼容脚本，数据库 history 保留旧脚本实际执行记录；第 7 节描述此前交付，本组负责替换其种子执行方式。自动化只使用隔离实例，真实 Docker Compose 开发库升级按 8.10 显式维护流程执行；第 9 节继续后置。

2026-11-06 实际开发库预检发现仅到 V3；用户确认采用 B：8.5–8.7 的差异白名单增加且仅增加“V1–V3 成功、固定 V4 Pending”分支，报告显式列出 V4，备份恢复验证后按 repair 旧 R→migrate V4/010/020→validate 执行。其他意外待迁移仍阻断；8.10 包含此分支的隔离验证和实际维护记录。

- [x] 8.1 同步 `design.md` 的开发数据与 Migration Plan，以及受影响的 `specs/user-auth/spec.md`：冻结固定宽度数字前缀、移除回调、旧库一次性受控过渡和环境边界；明确历史旧名不等于需要保留旧文件，受控 `repair` 仅用于已核验改名、不得自动掩盖迁移失败；对照 proposal 与现有技术基线，确认业务/API 范围不变、V1–V4 不改、生产账号开通仍延期。
- [x] 8.2 将现有租户和账号脚本分别统一为 `db/seed/nonprod/R__010_local_test_tenants.sql`、`db/devdata/R__020_auth_demo_accounts.sql`，删除 `AuthDevDataConfiguration` 及其 AFTER_MIGRATE 重跑逻辑；更新代码、配置检查、测试断言和运行文档引用，不保留旧名空脚本或其他启动补写机制；以资源扫描、编译和迁移测试确认旧名仅出现在历史说明/升级白名单/测试快照中，已发布 V1–V4 校验和不变。
- [x] 8.3 将租户种子改为只补缺失记录，保留已有名称、状态、时区、币种及其他人工修改；显式核验固定 ID 与 merchant_code 的对应关系，身份冲突明确失败，不用 `INSERT IGNORE` 吞错；MySQL 隔离测试覆盖新建、重复执行、停用租户不被重新启用，以及固定 ID/编码冲突不静默覆盖。
- [x] 8.4 调整账号种子为依赖明确、只补缺失账号：两个预期租户缺失时返回可定位的初始化错误，稳定 ID、用户名/手机号及商户归属冲突明确失败；保留现有密码摘要、状态和展示资料，不以零行插入冒充初始化成功；MySQL 隔离测试覆盖依赖缺失、身份冲突、首次插入、重复补种与人工修改保留，确认 test/prod 默认仍不加载 devdata。
- [x] 8.5 交付独立的一次性开发库升级工具及运行说明，使用项目一致的 Flyway 11.7.2 与目标 dev 库完整 locations；先检查目标实例/库名、同库应用停机、备份及历史状态，只允许 V1–V4 全成功，或 V1–V3 全成功且唯一待执行版本严格为 `V4__auth_accounts.sql`；已登记的旧 R Missing、新编号 R Pending 亦限定白名单，其他缺失、失败、V 校验和变化或意外待迁移均阻断；隔离测试覆盖目标限制、差异白名单、失败退出及凭据不进输出，不接入应用启动或 Compose 自动启动链。
- [x] 8.6 在升级工具中实现显式维护流程：保存迁移前结构＋数据（包含 history）和检查报告，经人工核对后执行一次 `repair`，核验仅预期旧 R 标记 Deleted、无失败清理或其他迁移元数据对齐；若报告的 pendingVersions 精确为 `["4"]`，随后按 V4→新编号 R migrate，否则只 migrate 新编号 R；再 `validate` 并核对业务数据。异常立即停止，不直接 UPDATE/DELETE history、不 clean/删卷、不永久关闭校验或忽略 Missing；以 Flyway 11.7.2 隔离演练确认 Deleted 不删除业务数据、新迁移有正常执行记录，工具重入识别已完成过渡且不重复 repair。
- [x] 8.7 新增或调整隔离迁移 IT：空库 V1→V4→010 租户→020 账号；旧名称历史库保留数据完成 repair/migrate/validate，覆盖仅旧租户 R 和两份旧 R；明确验证用户批准的精确 V3→V4→010→020 分支与非 V4 意外待执行版本拒绝；用测试资源保存必要旧版 SQL 快照复现历史，不作为运行期兼容脚本；核验旧执行记录仍可追溯、新迁移记录成功、密码/停用状态/人工资料保留，普通启动不会自动 repair 旧库。
- [x] 8.8 用真实 MySQL 隔离测试验证两个独立 Flyway 实例以相同迁移包并发启动时由 Flyway 锁协调，种子无重复行、唯一键竞争失败或回调额外写入；验证无文件变化再次 migrate 不增加成功迁移记录、不修改业务行，缺少依赖或迁移失败阻止应用就绪；更新原 AuthDevManualIT 等依赖回调的测试并运行本组定向测试，报告仅写 target，不连接固定开发端口或真实业务卷。
- [x] 8.9 更新 devdata/seed 说明、`api/docs/auth/data-design.md`、人工联调、测试及部署维护文档：说明所有待执行 V 先于 R、R 按描述数字前缀排序且 locations 顺序不代表依赖，统一管理后续模块编号；R 仅首次或校验和变化才执行、上游变化不自动触发下游、手工删数据后普通重启不自动补回，补种须显式操作；核对现有 Docker Compose 的实例/端口/卷与连接参数，保留“中间件健康→AI/IDEA 显式 dev 启动→自动 migrate”及“prod 独立迁移成功→应用启动”的流程，记录每个旧开发库升级一次与旧迁移包不可混跑，不引入后续模块表、中间件或真实生产发布。
- [x] 8.10 交付已有 Docker Compose 开发库的一次性人工升级清单与隔离恢复演练证据：明确用户指定的目标库和受影响应用，停止同库后端，按备份恢复手册验证结构＋数据备份可恢复，再依 8.5–8.6 执行受控升级；检查新旧 history、两租户/演示账号和已有人工数据，重启 dev 后验证登录及重复启动无种子改写；实际 gba-dev 升级须在实现阶段明确安排维护窗口并保留操作记录，自动化测试及本次计划编辑均不得代替人工步骤操作真实卷，prod 不执行这次开发种子 repair。

## 9. 后续模块路线与完整验收

- [x] 9.0 将 auth 实际迁移为 controller/service/mapper，Row 与请求分置 entity/dto，安全/缓存/异常使用辅助包；同步 package/import、MapperScan、XML namespace/resultType、Springdoc/ControllerAdvice 与测试，更新包结构及数据文档；后续业务域统一该结构；验证编译、单元/HTTP 与真实 MySQL/Redis 认证集成，保持接口与安全行为。

- [x] 9.1 参照 `docs/initialization/设计手稿.md` 与已确认分工，修订 design 第 9 节、proposal、`api/docs/architecture/module-roadmap.md` 和 README 导航；分别列队友的商品、库存、订单、物流完整模块，以及本人 Agent 四亮点/知识库文档/完整历史与 SSE/评测；逐模块写功能、数据、接口、测试、依赖及真实 Service 够用即接入的条件，不等待四模块全部完工，不强制测试替身流程；保留 ChatMemory，客服接管不列本次目标。
- [x] 9.2 路线标注每模块仍按需求→API→表/索引→实现→测试/联调推进，四亮点独立真实评测与 badcase、并行迁移编号协调；CI、真实短信/部署另开 change，课程 WebService/Nginx/Redis 并发验收单独安排；核对没有消费者下单支付、订单超时 MQ、平台后台、完整 RBAC 或未使用中间件混入当前范围。
- [x] 9.3 运行 mvn -f api/pom.xml test 与 clean verify；确认 HTTP/MySQL/Flyway/Redis、首次建号并发、idle/absolute、轮换重试/重放、退出刷新竞争及故障 IT 实际执行；Docker 不可用明确失败，不连接固定开发端口/业务卷，报告仅 target。
- [x] 9.4 汇总本 change 的真实验证结果、迁移/契约差异、手动联调与已知限制；运行 `openspec validate add-redis-auth-sessions --strict`，核对四份工件及维护文档一致，确认实际 dev key 被忽略、无凭据进入 diff，且没有执行真实生产发布。
