# Design

## Context

动机见 `proposal.md`。本设计依据当前源码，而不是旧手稿中尚未更新的工程说明：

| 已核对位置 | 当前事实与本次影响 |
| --- | --- |
| `api/pom.xml` | 单 Maven 应用、Java 21、Boot 3.5.16、MyBatis、JJWT 0.12.6、Redis starter、Testcontainers 已在；本次新增 Boot 管理版本的 Security starter，复用已有依赖 |
| `api/src/main/java/com/gba/merchantbao/config/FoundationOpenApiConfiguration.java` | Bearer 仅有文档声明，没有 SecurityFilterChain；`/api/v1/echo` 实际尚未认证 |
| `api/src/main/java/com/gba/merchantbao/legacy/` | 员工使用 MD5、消费者使用微信；旧拦截器只保护 `/admin/**`、`/user/**`；员工 logout 没有撤销行为；登录日志存在直接输出 DTO/微信 code 的问题 |
| `api/src/main/resources/application.yml` | `merchant.jwt` 使用 itcast/itheima 弱默认值；旧 JwtUtil 对短口令做 SHA-256 补长且将字符串当 UTF-8 key，不能直接当作规范的 Base64 密钥实现复用 |
| `api/src/main/resources/db/migration/` | V1 旧餐饮结构、V2 merchant、V3 币种；新认证不能复用无租户的旧 employee/user 表，不修改已发布 V 文件 |
| 根 Compose / dev YAML | `gba-dev` 已有 MySQL/Redis，3307/6380、命名卷、Redis 无密码；API 在 IDEA 中运行；实际 dev YAML 已存在但没有 JWT 覆盖 |
| 现有测试 | HTTP 测试无认证也能校验 echo 为 400；加入 Security 后需先认证再校验；基础启动测试硬编码迁移成功条数，需随 V4 更新有意义的版本断言 |
| `view/` | 目前只有 README，无法据此声称已有新前端已完成登录联调；本次交付 API 和人工步骤 |

现有两个 main spec 已完整核对。本设计细化其要求；保留消费者商户归属、三层授权、旧接口兼容、数据分层和四个 Agent 场景。手稿出现过黑名单方案，本次明确选活跃会话允许列表，不同时维护两套撤销机制。

## Goals / Non-Goals

**Goals:**

- 以业务闭环为顺序：确认账号怎样建立与使用，再冻结 API，再落表和索引，再实现会话/授权及验证，不把“装 Redis”当成模块完成。
- 后续领域只依赖可信身份及授权范围，不依赖 JWT 字符串、Redis key 或 legacy Mapper。
- 将手动注册/登录、开发账号、JWT 本地配置和日常 Docker 操作整理成可以照着执行的步骤。

**Non-Goals:**

- 平台管理员登录、商户自助入驻/审批、生产租户和首管理员开通流程、生产认证账号种子、匿名员工注册、邀请码体系、角色/权限/菜单表、完整员工管理后台、找回/重置密码、refresh token、OAuth/SSO、真实短信与前端。
- 商品/订单/物流表、Agent 实现、RabbitMQ/Milvus 预安装、CI 或真实生产发布。后续路线只作为本文与实现阶段维护文档的内容。

## Decisions

### 1. 账号建立采用消费者自助注册与商户内部子账号开通

常见电商商家工作台将商户入驻/主账号建立，与员工子账号开通区分；消费者也不应通过公开接口成为任意商户员工。本项目采用这个业务模式，不声称复刻淘宝千牛、深信服、字节、阿里、京东或美团的内部架构，也不把项目选择包装成已核验的行业占比。

| 主体 | 首期账号建立 | 登录方式 | 租户来源 |
| --- | --- | --- | --- |
| 消费者 | 店铺内手机号 + REGISTER 验证码；注册与登录分开 | 同商户手机号 + LOGIN 验证码 | 登录前按 merchantCode 解析；登录后只信服务端上下文 |
| 商户员工 | dev 用 devdata 提供样板商户首员工；test 用独立 @Sql fixture；后续由本商户已登录员工手动创建 | merchantCode + identifier（用户名/手机号）+ password | 创建取操作者上下文；登录按商户编码与账号记录确认 |
| 平台管理员 | 后续 change | 本期没有 | 不使用 merchant_id=0 伪装 |

用户的“手动注册登录”落实为消费者主动注册、商户内部手动开通员工、两类账号各自登录，不开放陌生人匿名加入商户。首期管理员与客服合并为 `MERCHANT_STAFF`，所以每个员工都具有本商户员工开通权限；这是一项明确的产品简化。未来若要求仅管理员开通，另开 change 拆分固定角色或权限，不能暗加 isAdmin 来假装没有分权。

员工创建者负责核对录入手机号；员工手机号本期是内部维护的登录标识，不声称已完成短信持有权验证。初始密码通过安全渠道交付，本 change 不建设通知或找回密码。devdata 为每个非生产商户提供一个演示首员工，自动化测试按用例加载独立员工 fixture；两者都使用 BCrypt 摘要，dev 演示密码不用于 test。首员工可被业务称为“管理员”，但首期角色仍是 `MERCHANT_STAFF`，权限与其他员工相同。生产商户入驻、生产首员工/管理员开通与是否使用生产种子均延期到商户管理方案，不在本 change 提供命令或 HTTP bootstrap。

消费者身份首期是租户内账号，不做全平台统一用户与多商户会员关系。同手机号在两个商户有不同账号与会话。消费者在商户 A 登录后不能靠传商户 B 的编码切换授权范围，必须单独登录 B。

未采用的方案：首次短信登录自动注册不符合用户要求；公开商户编码加匿名注册会导致任意加入；邀请码和审批会引入本期不需要的新业务系统；命令式生产初始化超出当前没有商户入驻/平台后台的范围，因此生产首员工流程延期。

### 2. 先冻结 API，再由 Springdoc 从实现导出最终契约

实现阶段先写 `api/docs/auth/requirements.md` 与 `api/docs/auth/api-design.md`，以本 change 的行为与下表为审阅基础；不是先手写一套会和代码漂移的最终 OpenAPI。Controller、DTO、校验与 Springdoc 注解是契约来源，完成后导出 `docs/api/auth-v1.openapi.yaml`，更新兼容记录并交付 Apifox 可执行场景。

| 方法与路径 | 请求 | 成功结果 | 访问条件 |
| --- | --- | --- | --- |
| POST `/api/v1/auth/sms-codes` | merchantCode, phone, purpose=REGISTER/LOGIN | 200：expiresIn=300, retryAfter=60；只有 dev 显式开启时附加 debugCode | 匿名，受发送限流 |
| POST `/api/v1/auth/consumer/register` | merchantCode, phone, code | 201：accountId, role=CONSUMER, merchantCode, phoneMasked；无 token | 匿名，REGISTER 挑战 |
| POST `/api/v1/auth/consumer/login` | merchantCode, phone, code | 200：登录结果 | 匿名，LOGIN 挑战且账号已注册 |
| POST `/api/v1/auth/staff/login` | merchantCode, identifier, password | 200：登录结果 | 匿名，商户内凭证校验 |
| POST `/api/v1/staff-accounts` | username, phone, displayName, password | 201：员工摘要，Location 指向其资源 | MERCHANT_STAFF，只能开通本商户员工 |
| GET `/api/v1/staff-accounts/{accountId}` | 字符串 ID | 200：脱敏员工摘要，不含密码/hash | MERCHANT_STAFF，同商户 |
| GET `/api/v1/consumer-accounts/{accountId}` | 字符串 ID | 200：脱敏本人摘要 | CONSUMER，同商户且本人 |
| GET `/api/v1/auth/me` | 无 | 200：当前可信身份、商户和 phoneMasked | 有效会话 |
| POST `/api/v1/auth/logout` | 无 body | 200：data=null | 已验签的未过期新协议 token；支持重复退出 |

两个账号资源 GET 是实际账号资料与创建结果定位，不新增测试专用生产接口；它们用真实资源证明租户、角色、归属三层边界，不预建订单表。

所有普通结果包在现有 `{code,message,data,requestId}` 内。登录结果 data 为 `{accessToken,tokenType:"Bearer",expiresIn:7200,expiresAt,principal:{accountId,merchantId,merchantCode,role,displayName,phoneMasked}}`；ID 始终为字符串，时间采用 UTC ISO-8601。认证响应设 `Cache-Control: no-store`。错误 data=null，头部 `X-Request-Id` 与响应一致。

输入规则：手机号首期支持中国大陆，去首尾空白并将 11 位号码规范化为 `+86` E.164；可接受相同号码的 `+86` 表示，不将掩码当凭证。用户名 trim 后小写，正则 `[a-z][a-z0-9_-]{2,31}`，不能与手机号格式混淆。merchantCode trim 后按既有大写业务编码规范解析。密码不 trim，8～64 字符且 UTF-8 最多 72 字节，超过直接 400，避免 BCrypt 截断。新认证 DTO 严格拒绝未声明字段，尤其 role、merchantId、accountId、customerId；不全局改变 legacy DTO 的未知字段行为。

业务码增量登记如下；扩展公共错误接口，使新 auth 枚举可被 ApiResponse 使用，保留旧枚举数值与调用兼容。

| HTTP | code / 名称 | 对外语义 |
| --- | --- | --- |
| 200/201 | 0 | 成功 |
| 400 | 10001 INVALID_ARGUMENT | 格式、未知字段或校验失败 |
| 401 | 10002 UNAUTHORIZED | token 缺失/无效、会话不存在、主体不再有效 |
| 403 | 10003 FORBIDDEN | 有效主体的角色不允许该操作 |
| 404 | 10004 RESOURCE_NOT_FOUND | 不存在、跨商户或非本人资源统一结果 |
| 409 | 10009 CONFLICT | 已通过验证的重复注册，或本商户员工用户名/手机号冲突 |
| 401 | 11001 AUTH_CREDENTIALS_INVALID | 账号不存在、密码错误、未注册消费者、商户/账号停用等统一登录失败 |
| 400 | 11002 AUTH_SMS_CODE_INVALID | 错误、过期、已使用、用途/范围不符的统一验证码失败 |
| 429 | 11003 AUTH_RATE_LIMITED | 超限，带 Retry-After 秒数 |
| 503 | 11501 AUTH_DEPENDENCY_UNAVAILABLE | 无法确认账号/租户/会话或完成 Redis 操作 |
| 503 | 11502 AUTH_SMS_UNAVAILABLE | 短信发送能力关闭或发送失败 |
| 500 | 10500 INTERNAL_ERROR | 非预期内部错误，不泄露堆栈 |

登录接口对未知商户/账号和停用状态保持相同 11001；短信发送不因账号是否注册改变正常成功响应。消费者注册重复只在验证码验证后告知冲突，避免未验证手机号直接枚举；发送只解析有效商户，不提前查询手机号注册状态。安全认证/角色校验先于 DTO 校验，因此匿名员工创建为 401，消费者创建为 403，员工携带伪造身份字段为 400。

### 3. V4 仅追加两张租户认证表

`V4__auth_accounts.sql` 只含 DDL；不改 V1/V2/V3、不修改旧 employee/user、不导入旧 MD5/微信账号。账号类型决定角色，不需要 role 表、role 列或权限关联表。

| 表 | 核心字段 | 约束与查询依据 |
| --- | --- | --- |
| consumer_account | id BIGINT、merchant_id BIGINT、phone VARCHAR(20)、display_name VARCHAR(64)、status VARCHAR(16) 默认 ACTIVE、created_at/updated_at TIMESTAMP(6)、version INT | PK(id)，UNIQUE(merchant_id,id)，UNIQUE(merchant_id,phone)，FK merchant_id→merchant.id，status CHECK 为 ACTIVE/DISABLED |
| merchant_staff_account | id BIGINT、merchant_id BIGINT、username VARCHAR(32)、phone VARCHAR(20)、display_name VARCHAR(64)、password_hash VARCHAR(100)、status、created_at/updated_at、created_by BIGINT 可空、version INT | PK(id)，UNIQUE(merchant_id,id)，UNIQUE(merchant_id,username)，UNIQUE(merchant_id,phone)，FK merchant_id→merchant.id；(merchant_id,created_by) 组合 FK→同表(merchant_id,id)，首个员工 created_by=NULL；状态 CHECK |

除 display_name 默认值/首员工 created_by 外，认证必要字段均 NOT NULL。phone 使用 ASCII 二进制比较，username 存规范化小写并用 ASCII 二进制比较；状态是可读字符串。时间存 UTC，接口统一 UTC。不做逻辑删除，停用不释放用户名/手机号。密码摘要使用带盐 BCrypt（cost=12），不保存可逆密码，不把 MD5 当 BCrypt 输入。

主键沿用 merchant 约定的 Snowflake BIGINT，抽出最小公共 ID 生成边界与 worker-id 配置供新域复用；单实例 dev 可用 worker 0，测试使用独立可控时钟；多实例部署必须分配不同 worker，时钟回退时失败而不是复用 ID，不建设号段服务。

索引解释：消费者登录命中 (merchant_id,phone)；员工 identifier 先判别手机号/用户名再走对应等值唯一索引，不用 OR 扫描。资源详情按 (merchant_id,id) 查询，消费者还限定 id=上下文 accountId。所有更新/删除 SQL 带 merchant_id 与资源 ID。created_by 组合引用需要 (merchant_id,created_by) 索引，防止跨租户创建关系。当前没有列表功能，不凭空添加 status、created_at、last_login_at 索引；已有 merchant_code 唯一索引用于登录租户解析。

注册靠唯一约束处理竞争，捕获唯一冲突转 409；不是只做“先查再插”。新账号写入与创建者归属校验在数据库事务内完成。消费者注册验证码被消费后数据库失败不会恢复验证码，返回 503 并要求重新取码，优先保证一次性；注册本来就不签 token，数据库提交后没有跨存储注册+登录事务。

### 4. 模拟短信保留标准发送边界和一次性校验

`auth` 内的发送接口仅表达接收方、用途和内容；dev 适配器模拟成功，test 注入可控适配器。dev 的 `merchant.auth.sms.provider=mock`，且显式开启 `merchant.auth.sms.expose-debug-code=true` 时才在发送结果附加 debugCode；默认和 prod 都不回显。prod 设置 provider=disabled，验证码接口返回 503；若 prod 误选 mock 或回显则启动失败。员工密码登录与已存在账号不依赖 SMS 发送。未来接阿里云只替换发送适配器、配置和真实发送验收，不改注册/登录用例。

六位码用安全随机数生成，不用固定 123456。Redis 挑战保存加随机盐的摘要、salt、错误次数、challengeId、绝对过期和 ready 状态，不存明文验证码；手机号 key 维度用规范化手机号摘要，摘要仍视为敏感标识，不视为匿名化。验证码仅在 dev 显式调试响应短暂显示，不记录请求/响应 body。发送失败返回 11502，并按 challengeId 定向删除本次挑战，不报告已发送；失败仍消耗限流额度。模拟模式的 debugCode 不进入审计/普通应用日志。

通过 Lua 原子完成“校验挑战状态/摘要、增加失败次数、成功删除”；错误第五次删除，成功一次只允许一个调用通过。发送采用原子冷却与配额预占，同一范围重发替换挑战，只有发送成功并激活后才可验证；发送与消费按 challengeId 避免误删新挑战。

默认限流：同商户+手机号+用途 60 秒发送冷却；同商户+手机号（跨用途合并）每小时 10 次；来源 IP 每小时 30 次发送。员工密码连续失败按商户+实际账号 5 次/15 分钟锁定，用户名/手机号共享同一账号计数；未知账号按标识摘要计数并执行 dummy BCrypt 校验，避免跳过成本暴露存在性；来源 IP 额外限制 30 次/15 分钟。成功密码登录清除该账号失败计数，不清 IP 总量；密码接口同样受 IP 请求配额约束。消费者验证码登录同时受挑战次数和 IP 请求配额约束。IP 首期取直接连接 remoteAddr，不信任任意 X-Forwarded-For；未来代理部署单独配置可信代理。上述参数可以配置，429 的 Retry-After 为剩余窗口，所有计数与首次 TTL 原子设置。

### 5. JWT 明确签名规范，Redis 保存可撤销会话

HS256 是 HMAC-SHA256 对称签名，不是 HSA256，也不是加密。token 中不放手机号、密码、验证码。两把 key 分别用于 MERCHANT_STAFF、CONSUMER，不是数据库 root/user，也没有本期平台 root key。

JWT 使用 issuer=`merchantbao`、audience=`api-v1`，claims 包含 sub（账号 ID 字符串）、merchantId、role、sid、iat、exp，jti=sid；sid 使用安全随机 UUID。签名和验证均限制 HS256，不能根据客户端 alg 自由选算法。验证服务仅把未验证 role 当作在固定两把 key 中选择候选的提示，随后必须校验签名、完整 claims 与会话匹配，绝不能提前建立角色上下文。异常统一 401。

会话 TTL 与 exp 同一时钟绝对过期点，默认 7200 秒，Redis PX/EXPIREAT 对齐，不滚动续期。每次登录产生新 sid，可独立多端登录，不做每账号唯一登录、踢人后台或会话索引。会话 value 包含 schemaVersion、accountId、merchantId、role、issuedAt、expiresAt，不保存完整 JWT。JWT 签好且 Redis SET 成功才向客户端返回；任一步失败无可用 token 返回。响应丢失可能留下孤立会话，靠 TTL 清理。

| Redis key（`env` 来自明确 profile 命名空间） | 类型/TTL | 用途 |
| --- | --- | --- |
| `mba:{env}:auth:session:{merchantId}:{role}:{sid}` | string JSON，至 JWT exp | 活跃会话允许列表 |
| `mba:{env}:auth:sms:{merchantId}:{purpose}:{phoneHash}` | hash，300s | 挑战、摘要、失败次数与 generation |
| `mba:{env}:auth:sms-cooldown:{merchantId}:{purpose}:{phoneHash}` | string，60s | 发送预占 |
| `mba:{env}:auth:sms-quota:{merchantId}:{phoneHash}:{window}` | counter，窗口剩余时间 | 跨用途合并的手机号配额 |
| `mba:{env}:auth:sms-ip:{ipHash}:{window}` | counter，窗口剩余时间 | 短信来源配额 |
| `mba:{env}:auth:password-fail:{merchantId}:{accountOrIdentifierHash}` | counter，900s | 员工密码失败锁定 |
| `mba:{env}:auth:login-ip:{ipHash}:{window}` | counter，窗口剩余时间 | 登录请求配额 |

新域使用 StringRedisTemplate 与显式 JSON/Hash，不修改 legacy RedisTemplate 的 JDK value 序列化。当前是单实例 Redis，Lua 原子性依据该部署；Redis Cluster 多键 hash slot 适配留给未来基础设施 change。与商品缓存、未来 ChatMemory 分开命名空间，不使用全库 flush 清理会话。

每个受保护请求：校验 token → 读匹配会话 → 从数据库查询当前 merchant 与对应账号是否 ACTIVE → 形成不可变身份。MySQL 是账号/商户事实来源，Redis 是短期登录态权威；Redis 丢失导致重新登录，不能从 JWT 自动重建。首期不缓存账号状态，所以受控停用后的后续请求立即失败，代价是每次保护请求有索引查询。后台状态管理 API 另开 change，当前通过测试 fixture 验证失效行为。

退出仅撤销当前 sid。过滤器对 logout 特殊处理：仍严格验签/claims/过期，不要求会话存在或账号 ACTIVE；读取存在会话时仍核对匹配，按准确 key 删除并确认结果；已不存在返回成功。Redis 故障返回 503，不谎报成功。已过期 token 为 401；其他路由始终需要活跃会话及有效账号。退出完成后开始的请求不能再通过；已经建立上下文的在途请求不承诺取消。

选择允许列表的原因是登录、当前身份、退出和 Redis 丢失的规则集中，便于讲清和验收；纯无状态 JWT 无法立即退出，黑名单需要另定义未列入 token 与存储故障行为。本期不并行实现黑名单。

### 6. Security 与领域服务共同落实三层授权

新 `auth/controller`、`application`、`domain`、`infrastructure` 归于同一业务域，Mapper 及 XML 在 auth 自己的边界内；更新 MyBatis 扫描及 `mapper/**/*.xml` 发现配置，并确保旧 Mapper 继续加载。新代码不导入 legacy 的 JwtProperties、BaseContext、User/Employee 或 Mapper。

Spring Security 使用 stateless 新 API 链，覆盖 `/api/v1/**`，白名单仅为已有 health 与明确的四个匿名认证操作（短信、消费者注册/登录、员工登录）；其他新 API 默认认证，具体账号路由校验角色。覆盖其他路径的兼容链放行到原 MVC 拦截器，保留旧 `/admin`、`/user` 认证；不能启用 Boot 默认登录页而锁死旧接口。prod 保持文档关闭，dev/test 文档按现有规则开放。

请求使用显式 Bearer header，不建立 cookie session，禁用 form/basic 登录；仅对这条无 cookie 的 API 链关闭 CSRF，不引入全来源凭证 CORS。首期没有前端来源清单，不开放任意 origin；后续前端接入按精确 origin 配置并测试。

将现有 RequestIdFilter 提到 Security 之前（当前 order=0 通常在 Security 之后），使 401/403/503 都有 requestId。认证过滤器显式注册在安全链内，关闭重复 servlet 自动注册；统一 entry point/access denied/依赖错误输出 envelope，不能依赖 ControllerAdvice 捕获过滤器异常。安全上下文在 finally 清理；legacy BaseContext 也补齐其拦截器的 afterCompletion 清理，不让线程池复用残留旧 ID。

可信 Actor 保存 accountId、merchantId、role、sessionId，不接受请求覆盖。领域资源校验顺序如下：

1. 租户范围取 Actor，资源查询在 SQL 中包含 merchant_id。
2. 路由/用例判断固定角色，角色不符 403，不查询私有资源详情。
3. 消费者账号资源还要求 accountId=Actor.accountId；员工详情允许同商户查询。无匹配返回 404，跨商户与不存在使用相同 body/message，不做“先全局查存在再告知无权”。

通用资源范围接口可供后续服务复用，但本 change 只以账号资源验收，不能虚构已完成订单/物流的归属防护。后续 Function Calling 必须从调用线程/明确传递的 Actor 取身份，不能把租户/消费者身份作为模型可选 Tool 参数。

### 7. JWT 配置使用单一新来源并桥接 legacy

新配置使用 `merchant.auth.jwt.merchant-staff-secret-base64`、`merchant.auth.jwt.consumer-secret-base64`；旧 `merchant.jwt.admin-secret-key`、`user-secret-key` 通过占位引用上述来源，仅供旧配置绑定。新域有自己的配置类，不依赖 legacy。

dev 示例只放占位符，实际被忽略的 `api/src/main/resources/application-dev.yml` 在 apply 阶段用安全随机生成两把不同的 32 字节 key 并 Base64 编码，不输出到工具日志或提交。若已有合规不同 key 则保留，不覆盖用户配置。共享 application.yml 移除弱默认，仅保留桥接、issuer/audience、TTL 等非秘密。test 的配置显式提供独立公开测试 key，不回落 dev。prod 新配置映射现有 `ADMIN_JWT_SECRET`、`USER_JWT_SECRET` 环境变量，变量名中的 ADMIN 仅为兼容，含义是 MERCHANT_STAFF。

```yaml
# application-dev.yml.example 的目标结构；真实值只填实际 dev YAML
merchant:
  auth:
    jwt:
      merchant-staff-secret-base64: REPLACE_WITH_STAFF_BASE64_KEY
      consumer-secret-base64: REPLACE_WITH_CONSUMER_BASE64_KEY
    sms:
      provider: mock
      expose-debug-code: true
```

启动检查缺失/占位/非法 Base64、解码少于 32 字节、两把 key 相同；日志只说明属性名和原因。JJWT 显式 HS256，不让更长 key 自动改选 HS384/HS512。legacy JwtUtil 同步按 Base64 解码并显式 HS256，删除短口令 SHA 补长；这是统一正确密钥语义，现存旧会话需要重新登录。新 token 不包含 empId/userId，旧 token 没有 issuer/audience/sid/role 与 Redis 记录，新旧验证分别拒绝，不做默认租户桥接。修正 legacy 登录日志直接打印 password DTO/微信 code 的问题，保留旧路径、头与 envelope。

Git Bash 可连续执行两次 `openssl rand -base64 32`，结果分别用于员工和消费者；OpenSSL 输出已经是 Base64，Java 解码一次后取得 key。命令只用于实际本地配置，不把随机结果写到规划或示例。OpenSSL 不可用时由 apply 使用 JDK/系统加密随机 API 生成等价结果。

| 改动场景 | 文件/操作 | Docker 操作 |
| --- | --- | --- |
| 仅生成或修改 dev JWT key | 实际 dev YAML；重启 IDEA 中 API；旧 token 失效 | MySQL/Redis 已运行则无需 Docker 命令 |
| 已有 gba-dev，只是日常启动 | 仓库根执行 `docker compose up -d`，可选 `--wait`；`docker compose ps` 查看 | 复用既有项目/卷，不会创建一套新的身份服务 |
| 根 .env 的中间件端口或 Compose 定义变化 | 同步 dev YAML 连接参数，`docker compose config --quiet` 后 `docker compose up -d --wait` | up 按变化重建；restart 不重新加载环境变量 |
| 已有 MySQL 卷修改 .env 密码 | 先对正确实例受控 ALTER USER，再同步 .env/YAML | up/restart 不会重新运行初始化来改账号，不删除卷 |
| 未来 prod JWT 环境变量变化 | 生产环境文件更新并使 app 容器重建，按既有生产手册 | 示例 `docker compose --profile deploy up -d --no-build --no-deps --force-recreate app`；本次不执行 |

根 `.env` 和 `.env.example` 仍只定义 dev 中间件，不加入 JWT；根 Compose 没有 app 服务，无需为 JWT 改动，不添加 login profile 或重复 Redis 服务。需要更新的是运行说明中“登录模块再加入 Redis”的过时描述。prod Compose 已向 app 传递两项 JWT 环境变量，保留该映射，更新 prod YAML/示例的 Base64 语义与启动校验即可。

### 8. 开发数据、测试与交付文档按现有分层追加

`db/devdata/R__auth_demo_accounts.sql` 为两个既有非生产租户建立演示首员工：均可用 demo_staff 用户名、同一合成手机号验证跨租户唯一性；样板商户至少两个消费者，对照商户至少一个同手机号消费者。实际演示手机号/用户名/显示名和公开的 dev-only 密码在 `api/docs/auth/manual-verification.md` 登记；合成手机号不发真实短信。SQL 只保存 BCrypt hash，按 merchant_code 解析既有租户；幂等重跑只插入缺失记录，不重置密码、状态或展示资料，不能借 upsert 覆盖用户改动。该路径仅由 dev Flyway locations 加载，不把演示租户或账号作为生产初始化数据。

独立测试账号放 `src/test/resources/db/fixture/auth_accounts.sql`，通过 `@Sql` 显式加载，不放 common/nonprod/devdata；每个相关用例有两个租户的员工、样板商户两名消费者、对照商户一名复用手机号的消费者，另按场景检查租户内用户名/手机号唯一性和资源归属。测试密码与 dev 演示密码隔离。HTTP 测试跨事务时按商户与稳定标识清理；数据库事务用例回滚，避免全表 count 或表空断言。

测试层次：单元覆盖规范化、BCrypt、key/claim 校验、身份范围与错误映射；HTTP 契约测试用替身身份/会话存储保持无 Docker，确认匿名 echo 变 401、已认证非法 echo 仍 400；真实 MySQL/Redis Testcontainers 覆盖 V1→V4、已有 V3 升级、唯一竞争、短信 Lua、TTL、退出、多会话、状态失效和断连 503。只验证自身创建的数据与容器，不读写本地 gba-dev 卷。完整 `mvn -f api/pom.xml clean verify` 必须真正运行 IT；Docker 不可用失败，报告写 target，不宣称规划已通过应用测试。

日志将认证请求 module 从固定 foundation 细化为 auth，保留 requestId、event、actorId/merchantId（可信时）、errorCode、durationMs。独立 auth audit logger 接到已有 audit.json，不创建第三种日志文件，不重复输出到 application.json。未知账号只记录脱敏/摘要标识；请求体、响应 token/debugCode、Authorization 不能直接打印。

实现时交付 `api/docs/auth/{requirements,api-design,data-design,manual-verification}.md`、OpenAPI 快照、旧接口兼容记录、测试说明与运行手册更新，README 只做导航。数据文档说明两表字段/索引、Redis key 与 TTL、种子/fixture 边界及备份恢复原则。

### 9. 后续按业务价值推进，Agent 各亮点按最低前置启动

“先认证”是本仓库当前合理顺序，不代表 Redis 本身是所有项目第一步。每个后续模块仍按需求、接口、表/索引、实现、测试与联调交付，不一次性造完所有表。当前 change 只产出路线，下面各行需要独立 change。

| 建议顺序 | 业务场景与模块 | 最低前置/交付边界 |
| --- | --- | --- |
| 1（本次） | 消费者注册登录、员工开通登录、退出；身份与 Redis 会话 | 已有基础设施/merchant；交付可信 Actor 和三层授权 |
| 2 | 商户维护 3C 商品/SPU、SKU、规格与公开可售状态；商品/库存 | 认证；新电商表、公开字段和商户写入边界，库存先做只读可售查询与受控维护，不提前造复杂交易系统 |
| 3 | 消费者查询本人订单/物流，客服查询本商户数据；订单/订单项快照、物流/轨迹 | 认证与商品/SKU；数据来自开发种子或商户后台，消费者不下单、不支付；提供可复用领域只读查询服务 |
| 4 | 商户整理商品说明、售后政策和店铺规则；知识文档管理 | 认证；文档归属、版本/可见范围、文件格式/大小与最小同步解析边界；向量数据库由真正检索模块引入 |
| 5 | 商品/售后知识问答；RAG 亮点 | 认证/公开商户边界 + 可用知识来源与检索入口；按需引 LangChain4j/Milvus，交付检索、引用与独立评测；不等待订单或 MQ 全部完成 |
| 6 | 查商品、库存、本人订单与物流；Function Calling 亮点 | 认证 + 各 Tool 对应的真实领域查询服务 + 最小 Agent 调用入口；服务端身份注入、只读工具、脱敏、有限重试与降级；只上线具备前置的 Tool，不把直接查 Mapper 伪装成领域服务 |
| 7 | 批量资料导入避免阻塞；RabbitMQ 文档任务亮点 | 知识文档 + 可独立调用的解析/切分/向量化流水线；从模块 4/5 分支即可开始，不依赖 FC；本模块才引 RabbitMQ，交付状态查询、确认、ACK、幂等、重试/死信/人工重投 |
| 8 | 连续追问与历史恢复；会话/消息持久化 + Redis ChatMemory 亮点 | 认证 + MySQL 会话归属和完整消息历史 + 至少一条可用 Agent 对话链；可与 RAG/FC/MQ 并行，不要求前三亮点全部完成；交付 token 预算、摘要、过期恢复与独立评测 |

表中的编号是个人开发的建议串行路线，不是所有亮点相互等待的硬依赖。路线图还应注明：认证后可单独规划 PR CI，CI 不是商品/RAG 的业务前置；上线按用户进度另开 change。RAG、FC、MQ、ChatMemory 每项分别留真实测试/评测与 badcase，不预填提升百分比。Redis 登录会话、商品缓存、ChatMemory 是不同业务状态与 key 空间；RabbitMQ 的当前目标是文档任务，不沿用旧 README 的订单超时支付场景。

## Risks / Trade-offs

- [所有员工共享开通权] → 明确首期合并角色的权限结果，只允许本商户范围，记录创建者审计；真正需要区分管理员/客服时单独细化角色。
- [手机号在各商户产生独立消费者账号] → 对外显式选择店铺、会话绑定商户，后续统一用户需独立迁移设计，不暗中共享账号。
- [每次受保护请求查 MySQL 和 Redis] → 先使用唯一索引保证即时停用与简单正确性，量化性能后再决定状态缓存和版本撤销机制。
- [模拟验证码在 dev 响应可见] → 只在 dev/test、显式开关下显示，prod fail-fast；默认日志不记 body，不能把此模式直接部署为真实短信。
- [旧 key 解码语义变化与新 API 开始认证] → 明示旧会话重新登录和 echo 401 变化，保留旧协议回归；不声称旧接口获得新三层授权。
- [Redis 丢失或故障] → 丢失会话要求重新登录，故障 503；会话可重建不新增备份作业，不从历史 RDB/AOF 恢复会话以免撤销记录回滚使 token 复活；灾难恢复后对 auth 命名空间受控失效或轮换 key，其他缓存按本身恢复规则处理。
- [单 Redis Lua 方案未来扩展 Cluster] → 记录部署假设；Cluster 切换另开 hash-slot/脚本适配与一致性验证，不承诺当前多 key 脚本可直接跨槽运行。

## Migration Plan

1. apply 开始先冻结需求/API/业务码与兼容差异，再编写 V4；保持 V1/V2/V3 不变，受控迁移前按现有手册备份结构与数据并能在隔离库恢复。
2. 空库按 V1→V4 迁移，既有已登记 V3 库只追加 V4；两个账号表由结构迁移创建，dev 演示账号只由 devdata 加载，test 账号只由测试显式加载 fixture。租户根继续由 nonprod seed 供 dev/test 使用；prod 独立迁移服务只加载 migration/common，不包含演示租户或认证账号。
3. 完成 JWT/模拟短信配置示例与校验后本地生成真实 dev key，保留用户已有其他配置；现有 gba-dev MySQL/Redis 继续使用，重启 API 即执行待迁移并加载 devdata。
4. 加入新安全链、账号用例与会话，修正请求关联顺序及 legacy 兼容；运行单元、HTTP 与容器完整验收，导出实际 Springdoc 快照，按文档手动注册/开通/登录/退出。
5. 生产发布及首商户/首员工开通另行规划：结合商户自助入驻、首管理员绑定与平台管理方案决定创建流程和是否需要生产种子；本 change 不提供命令/HTTP 初始化入口或生产账号种子，也不执行云发布。
6. 应用回退先核验新增表与旧应用兼容；本次 DDL 为新增表，通常可保留表回退代码，但不得重新恢复弱 key 或让新 token 流入旧接口。若必须恢复数据库，按已有结构＋数据备份方案处置新增写入，不能自动 drop 新表、删卷、clean/repair 或改历史迁移。
