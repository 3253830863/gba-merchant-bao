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
- 将首次短信登录建号、员工开通、开发账号、JWT 本地配置和日常 Docker 操作整理成可以照着执行的步骤。

**Non-Goals:**

- 平台管理员登录、商户自助入驻/审批、生产租户和首管理员开通流程、生产认证账号种子、匿名员工注册、邀请码体系、角色/权限/菜单表、完整员工管理后台、找回/重置密码、OAuth/SSO、真实短信与前端。
- 商品/订单/物流表、Agent 实现、RabbitMQ/Milvus 预安装、CI 或真实生产发布。后续路线只作为本文与实现阶段维护文档的内容。

## Decisions

### 1. 消费者首次短信登录自动建号，员工由商户内部开通

本方案采用店铺内消费者身份和受控员工子账号，不把它称为已核验的淘宝、有赞、微盟或某类公司内部实现。消费者不需要先注册或手输商户编码。

| 主体 | 首期账号建立 | 登录方式 | 租户来源 |
| --- | --- | --- | --- |
| 消费者 | 该店首次有效短信验证时自动创建；已存在则复用 | 手机号 + LOGIN 验证码，一个流程 | 店铺首页自动携带公开 storeSlug；服务端解析为 ACTIVE merchantId |
| 商户员工 | devdata 提供演示首员工；test 独立 @Sql fixture；其他员工由本商户已登录员工开通 | merchantCode + identifier（用户名/手机号）+ BCrypt 密码 | 登录校验账号所属商户；创建取可信 Actor |
| 平台管理员 | 后续 change | 本期没有 | 不使用 merchant_id=0 伪装 |

首期仅 MERCHANT_STAFF、CONSUMER；管理员与客服合并，所以所有员工暂时共享本商户员工开通权，不引入 isAdmin 或 RBAC。员工创建成功不替同事登录；员工手机号是内部维护标识，本期不声称完成其短信持有权验证。初始密码由模块外安全渠道交付。生产商户、首员工/管理员建立与生产种子延期，不提供命令或 HTTP bootstrap。

消费者同手机号在不同商户是不同账号、数据与会话。有效验证码消费后，以 (merchant_id, normalized_phone) 唯一约束处理首次建号并发；独立有效挑战发生唯一冲突时复用赢家账号，重新核验状态，不返回重复注册 409；同一个挑战重放仍失败。已有 DISABLED 账号不能重建绕过停用。

账号数据库事务先提交，再建立 Redis 双 token 会话。建号成功不等于登录成功：Redis 写入失败返回 503 且不返回任一 token，已提交账号保留，验证码不恢复，下次重新取码登录。没有独立注册接口、REGISTER 用途、首次建号提示、协议链接或同意勾选门槛。

店铺主页为 /store/{storeSlug}；初期 storeSlug 复用 merchantCode 的公开别名，不新增 slug 表字段，服务端按现有编码规则解析。它只定位商户，不证明访问权限；未来公开 slug 改名另作映射/迁移，内部 merchantId 不变。没有店铺上下文则先进入店铺，不接受无上下文的消费者手机号登录。

A 店已登录时访问 B 店公开主页仍是 B 店匿名浏览；私有功能只选取 B 店本账号类型的凭证，没有就发起 B 店短信登录。前端不清 A 店凭证，回 A 可继续使用/刷新其会话。通用私有接口无法知道浏览器当前在 B 店：若误发 A token，只会按 A 身份处理；前端必须按路由店铺选择凭证并核对响应 principal，不能指望后端猜测页面店铺。

### 2. 先冻结 API，再从实现导出 Springdoc 契约

任务组 1 交付 requirements.md、api-design.md 与旧接口迁移记录；下表为目标设计，不代表已经上线。实现的 Controller、DTO、校验和注解完成后再导出 docs/api/auth-v1.openapi.yaml。

| 方法与路径 | 请求 | 成功结果 | 访问条件 |
| --- | --- | --- | --- |
| POST /api/v1/stores/{storeSlug}/auth/sms-codes | phone | 200：expiresIn=300, retryAfter=60；dev 显式开启才有 debugCode | 匿名，LOGIN 挑战与限流 |
| POST /api/v1/stores/{storeSlug}/auth/consumer/login | phone, code | 200：首次建号或已有账号双 token 登录 | 匿名，有效 LOGIN 挑战 |
| POST /api/v1/auth/staff/login | merchantCode, identifier, password | 200：双 token 登录 | 匿名，商户内密码 |
| POST /api/v1/auth/refresh | refreshToken, refreshRequestId | 200：轮换后的双 token | 独立 refresh 凭证，不要求有效 access |
| POST /api/v1/staff-accounts | username, phone, displayName, password | 201：员工摘要，Location 指向其资源；无 token | MERCHANT_STAFF，本商户 |
| GET /api/v1/staff-accounts/{accountId} | 字符串 ID | 200：脱敏员工摘要 | MERCHANT_STAFF，同商户 |
| GET /api/v1/consumer-accounts/{accountId} | 字符串 ID | 200：脱敏本人摘要 | CONSUMER，同商户且本人 |
| GET /api/v1/auth/me | 无 | 200：可信 principal | 有效 access 与活跃记录 |
| POST /api/v1/auth/logout | 可选 JSON refreshToken；或有效 access | 200：data=null | 验证凭证后撤销当前 sid，支持重复退出 |

详情资源是实际账号资料，不额外造订单作为测试接口。所有 JSON 使用 {code,message,data,requestId}，错误 data=null；X-Request-Id 与 body 一致；认证响应 Cache-Control: no-store；ID 为字符串，时间 UTC ISO-8601。

登录/刷新 data 为 {accessToken,refreshToken,tokenType:Bearer,expiresIn,expiresAt,refreshExpiresAt,principal}。expiresIn 通常 7200，临近 absolute 时截短；expiresAt 是 access exp，refreshExpiresAt 是原始 absolute，刷新不推后。principal 含 accountId、merchantId、merchantCode、role、displayName、phoneMasked。消费者首次建号仍 200，不返回注册 201/Location。仅员工开通返回 201/Location 且无 token。

输入：大陆 11 位手机号或等价 +86 输入 trim 后统一 E.164；用户名 trim/小写，正则 [a-z][a-z0-9_-]{2,31}；merchantCode 按现有大写业务编码规范。密码不 trim，8～64 字符且 UTF-8 最多 72 字节。新 DTO 拒绝未知字段，客户端不得注入 role、merchantId、accountId、customerId。消费者请求体不再接受 merchantCode/purpose；refresh 不接受店铺/角色/内部 ID，refreshRequestId 为一次轮换尝试的随机 UUID，网络重试复用，与每个 HTTP 的 X-Request-Id 不同。

| HTTP | code / 名称 | 对外语义 |
| --- | --- | --- |
| 200/201 | 0 SUCCESS | 成功 |
| 400 | 10001 INVALID_ARGUMENT | 格式、未知字段、身份注入、双凭证会话不一致 |
| 401 | 10002 UNAUTHORIZED | access 无效/活跃记录缺失；refresh 无效/撤销/绝对过期 |
| 403 | 10003 FORBIDDEN | 有效主体角色越权或显式保护店铺路径与 token 租户不符 |
| 404 | 10004 RESOURCE_NOT_FOUND | 具体资源不存在、跨租户、非本人统一结果 |
| 409 | 10009 CONFLICT | 员工用户名/手机号冲突，不用于消费者自动建号竞争 |
| 401 | 11001 AUTH_CREDENTIALS_INVALID | 登录凭证错误、账号/商户不存在或停用统一消息 |
| 400 | 11002 AUTH_SMS_CODE_INVALID | 验证码错误、过期、已用或范围不符 |
| 429 | 11003 AUTH_RATE_LIMITED | 超限，Retry-After 秒数 |
| 503 | 11501 AUTH_DEPENDENCY_UNAVAILABLE | MySQL/Redis 不能完成操作或确认状态 |
| 503 | 11502 AUTH_SMS_UNAVAILABLE | 短信关闭或发送失败 |
| 500 | 10500 INTERNAL_ERROR | 非预期内部错误，不泄露堆栈 |

商户无效的匿名短信/登录保持统一凭证失败，不枚举手机号是否已有账号。安全身份/角色校验先于 DTO：匿名员工开通 401、消费者开通 403、员工提交身份注入 400。详细请求/响应与失败例以 api-design.md 为准。

公开资源示例 GET /api/v1/stores/{storeSlug}/products 按 URL 定位且忽略身份；私有资源示例 GET /api/v1/orders、/orders/{orderId} 按 Actor.tenant 与消费者本人/员工能力限定，同一个业务资源不必强制拆 /staff 前缀。当前不实现这些示例。未来确有多店操作才设计显式店铺私有路径并校验授权，不能简单用 URL 覆盖 token；具体资源查询仍统一 404。

### 3. V4 仅追加两张租户认证表

`V4__auth_accounts.sql` 只含 DDL；不改 V1/V2/V3、不修改旧 employee/user、不导入旧 MD5/微信账号。账号类型决定角色，不需要 role 表、role 列或权限关联表。

| 表 | 核心字段 | 约束与查询依据 |
| --- | --- | --- |
| consumer_account | id BIGINT、merchant_id BIGINT、phone VARCHAR(20)、display_name VARCHAR(64)、status VARCHAR(16) 默认 ACTIVE、created_at/updated_at TIMESTAMP(6)、version INT | PK(id)，UNIQUE(merchant_id,id)，UNIQUE(merchant_id,phone)，FK merchant_id→merchant.id，status CHECK 为 ACTIVE/DISABLED |
| merchant_staff_account | id BIGINT、merchant_id BIGINT、username VARCHAR(32)、phone VARCHAR(20)、display_name VARCHAR(64)、password_hash VARCHAR(100)、status、created_at/updated_at、created_by BIGINT 可空、version INT | PK(id)，UNIQUE(merchant_id,id)，UNIQUE(merchant_id,username)，UNIQUE(merchant_id,phone)，FK merchant_id→merchant.id；(merchant_id,created_by) 组合 FK→同表(merchant_id,id)，首个员工 created_by=NULL；状态 CHECK |

除 display_name 默认值/首员工 created_by 外，认证必要字段均 NOT NULL。phone 使用 ASCII 二进制比较，username 存规范化小写并用 ASCII 二进制比较；状态是可读字符串。时间存 UTC，接口统一 UTC。不做逻辑删除，停用不释放用户名/手机号。密码摘要使用带盐 BCrypt（cost=12），不保存可逆密码，不把 MD5 当 BCrypt 输入。

主键沿用 merchant 约定的 Snowflake BIGINT，抽出最小公共 ID 生成边界与 worker-id 配置供新域复用；单实例 dev 可用 worker 0，测试使用独立可控时钟；多实例部署必须分配不同 worker，时钟回退时失败而不是复用 ID，不建设号段服务。

索引解释：消费者登录命中 (merchant_id,phone)；员工 identifier 先判别手机号/用户名再走对应等值唯一索引，不用 OR 扫描。资源详情按 (merchant_id,id) 查询，消费者还限定 id=上下文 accountId。所有更新/删除 SQL 带 merchant_id 与资源 ID。created_by 组合引用需要 (merchant_id,created_by) 索引，防止跨租户创建关系。当前没有列表功能，不凭空添加 status、created_at、last_login_at 索引；已有 merchant_code 唯一索引用于登录租户解析。

员工开通唯一冲突转 409；消费者自动建号唯一冲突改为读取同租户已有账号并核验状态，不只依赖先查再插。账号写入与创建者归属校验在数据库事务内完成。验证码消费后数据库失败返回 503 且不恢复挑战；数据库账号提交与 Redis 会话分开，不引入跨 MySQL/Redis 回滚。

### 4. 模拟短信保留标准发送边界和一次性校验

`auth` 内的发送接口仅表达接收方、用途和内容；dev 适配器模拟成功，test 注入可控适配器。dev 的 `merchant.auth.sms.provider=mock`，且显式开启 `merchant.auth.sms.expose-debug-code=true` 时才在发送结果附加 debugCode；默认和 prod 都不回显。prod 设置 provider=disabled，验证码接口返回 503；若 prod 误选 mock 或回显则启动失败。员工密码登录与有效 refresh 的刷新不依赖 SMS 发送；消费者无论是否已有账号，凭证登录均需短信验证。未来接阿里云只替换发送适配器、配置和真实发送验收，不改消费者登录用例。

六位码用安全随机数生成，不用固定 123456。Redis 挑战保存加随机盐的摘要、salt、错误次数、challengeId、绝对过期和 ready 状态，不存明文验证码；手机号 key 维度用规范化手机号摘要，摘要仍视为敏感标识，不视为匿名化。验证码仅在 dev 显式调试响应短暂显示，不记录请求/响应 body。发送失败返回 11502，并按 challengeId 定向删除本次挑战，不报告已发送；失败仍消耗限流额度。模拟模式的 debugCode 不进入审计/普通应用日志。

通过 Lua 原子完成“校验挑战状态/摘要、增加失败次数、成功删除”；错误第五次删除，成功一次只允许一个调用通过。发送采用原子冷却与配额预占，同一范围重发替换挑战，只有发送成功并激活后才可验证；发送与消费按 challengeId 避免误删新挑战。

默认限流：同商户+手机号 LOGIN 范围 60 秒发送冷却；同商户+手机号每小时 10 次；来源 IP 每小时 30 次发送。员工密码连续失败按商户+实际账号 5 次/15 分钟锁定，用户名/手机号共享同一账号计数；未知账号按标识摘要计数并执行 dummy BCrypt 校验，避免跳过成本暴露存在性；来源 IP 额外限制 30 次/15 分钟。成功密码登录清除该账号失败计数，不清 IP 总量；密码接口同样受 IP 请求配额约束。消费者验证码登录同时受挑战次数和 IP 请求配额约束；refresh 来源 IP 默认另按 30 次/15 分钟限额，合法重试也受配额约束。IP 首期取直接连接 remoteAddr，不信任任意 X-Forwarded-For；未来代理部署单独配置可信代理。上述参数可以配置，429 的 Retry-After 为剩余窗口，所有计数与首次 TTL 原子设置。

### 5. 双 token、滑动活跃记录与固定绝对期限

HS256 是 HMAC-SHA256 对称签名。员工/消费者各自 Base64 key，不是数据库 root/user；JWT 不放手机号、密码或验证码。access 校验固定算法、issuer=merchantbao、audience=api-v1 以及 sub、merchantId、role、sid、jti、iat、exp，未验签 role 仅用于固定候选 key 选择，不能建立身份。sid 每次凭证登录随机独立；jti 标识本次签发，不再等于 sid。刷新沿用同一 sid，不延长原始 absolute。

| 规则 | 消费者 | 商户员工 |
| --- | --- | --- |
| access 标称寿命 | 7200 秒（2 小时） | 7200 秒（2 小时） |
| 活跃 idle | 7 天 | 1 天 |
| absolute / refresh 最长寿命 | 首次凭证登录起 30 天 | 首次凭证登录起 7 天 |
| idle 到期 | access 活跃记录失效；有效 refresh 可无感恢复 | 同左 |
| absolute 到期 | 必须重新短信验证 | 必须重新密码登录 |

access exp=min(签发时刻+7200秒, absoluteAt)；activeExpiresAt=min(lastQualifyingActivity+idle, absoluteAt)。正常业务认证成功并成功完成允许的私有业务操作后才 touch；失败/匿名请求、me 身份探测、后台轮询与保活端点不 touch。touch 必须原子检查 control 未撤销、active 仍存在且身份匹配后才更新期限，不能由退出前在途请求在退出后重新 SET 活跃记录。前端仅为前台实际私有操作按需 refresh；不得后台定时刷新续命。后端无法可靠判断真人操作，不把客户端 active=true 当授权证据，也不承诺识别所有恶意伪装的业务请求。refresh 合法处理本身可 touch，但短时幂等重试不能重复推进 idle。

不透明 refresh 初始使用安全随机 256 位值，不含可由客户端采纳的租户 claims；服务端用其 SHA-256 摘要查 Redis 元数据取得 sid/merchantId/role。active 仅是短期允许列表，control 和 refresh 记录保留至 original absolute，明确区分 idle 清理和撤销。每次刷新重新确认账号/商户 ACTIVE；idle 丢失可重建 active，control/refresh 全部丢失或撤销则 401，绝不能单凭 JWT 重建。Redis/MySQL 出错为 503。

| Redis key（env 为 profile 命名空间） | 类型/TTL | 用途 |
| --- | --- | --- |
| mba:{env}:auth:session:{sid} | JSON，至 activeExpiresAt | schemaVersion、accountId、merchantId、role、absoluteAt、activeExpiresAt，与 access identity 匹配 |
| mba:{env}:auth:control:{sid} | hash，至 absoluteAt | 可信身份、原始登录时刻、absoluteAt、当前 generation/hash、ACTIVE/REVOKED 状态；不随 idle 删除 |
| mba:{env}:auth:refresh:{tokenHash} | hash，至 absoluteAt | sid、generation、CURRENT/USED 状态；保留历史摘要以识别重放与同会话退出，不保留原值 |
| mba:{env}:auth:refresh-retry:{sid}:{refreshRequestId} | JSON，默认 10 秒且不晚于 absoluteAt | 请求旧摘要、下一 generation、加密的同次响应，用于短时返回同一结果 |
| mba:{env}:auth:sms:{merchantId}:LOGIN:{phoneHash} | hash，300 秒 | 摘要、salt、challengeId、失败次数与 ready |
| mba:{env}:auth:sms-cooldown:{merchantId}:LOGIN:{phoneHash} | string，60 秒 | 发送预占 |
| mba:{env}:auth:sms-quota:{merchantId}:{phoneHash}:{window} | counter，窗口剩余时间 | 手机号配额 |
| mba:{env}:auth:sms-ip:{ipHash}:{window} | counter，窗口剩余时间 | 短信来源配额 |
| mba:{env}:auth:password-fail:{merchantId}:{accountOrIdentifierHash} | counter，900 秒 | 密码失败锁定 |
| mba:{env}:auth:login-ip:{ipHash}:{window} | counter，窗口剩余时间 | 登录/刷新请求配额 |

新域使用 StringRedisTemplate 与显式 JSON/Hash，保留 legacy 序列化。单实例 Redis Lua 为当前原子边界，多键跨槽适配留给未来 Cluster change。会话初始化原子写 control、active 和首 refresh 索引，成功后才返回双 token；失败不返回任一凭证。响应丢失的孤立会话由绝对 TTL 清理。

轮换采用“前端同店铺同角色跨 tab 协调 + refreshRequestId + 10 秒幂等窗口”。Redis 原子比较当前 generation/hash，消费旧 refresh 并登记新值、重试元数据和 active。旧值仅在同一 refreshRequestId、同一旧摘要、窗口内且该轮换仍是当前代时返回同一结果；不延长 absolute/idle。其他已知历史值的重用撤销当前 sid/family（不撤销其他设备），未知随机值只 401 不撤销任何可猜会话。不同 tab 必须共享在途 requestId 和结果，避免把正常并发误当重放。

为兼顾摘要存储与网络重试，refresh 本体始终用安全随机 256 位值；仅短时重试缓存保存 AES-256-GCM 加密后的完整同次响应，随机 nonce，sid/generation/requestId/absoluteAt 作为 AAD。缓存密钥由各角色签名 key 经 HKDF-SHA256、auth-refresh-retry-v1 域分离派生，不直接复用 JWT 原 key；解密只在服务端校验同一旧摘要、requestId、当前 generation 和未撤销控制记录后进行。缓存默认 10 秒且不超过 absolute，过期/退出清除，不存明文 token；解密失败拒绝并返回 503。实现验证加密完整性、域分离、并发和 TTL；密钥轮换使相关会话重新登录。候选随机值与加密响应在应用内生成，Lua 原子比较状态并提交，不在脚本中使用非安全随机数。

退出 POST /api/v1/auth/logout 可用未过期且验签完整的 access 定位 sid，active 已消失/账号已停用不妨碍撤销；access 缺失、无效或过期时用 body.refreshToken 的已知摘要定位。refresh 分支也接受该会话已轮换/已撤销的已知历史摘要用于撤销/重试，但绝不刷新或发新 token。可用 access 与已知 refresh 同时提交须属于同一 sid，否则 400；若 access 不能验证/已过期，可由有效的已知 refresh 独立定位并撤销，不因无效 access 阻断退出。refresh 仍不得签发新 token。

Lua 原子置 control=REVOKED、删除 active、使当前及历史 refresh 全部失去刷新能力并清除重试结果；以 control 墓碑与已知摘要保留至原 absolute 支持同一会话重复退出 200。未知/伪造/全部超过 absolute 的凭证 401，不把任意字符串退出当成功。退出与刷新在相同控制记录上串行检查，不允许撤销后重建 active/从重试缓存取回 token；退出后发起的请求失败，不承诺取消已授权在途业务。故障 503，不虚报撤销。其他店铺、账号类型、设备 sid 均不变。

浏览器当前约定（dev/test 与短期生产）：access+refresh 存 localStorage，按 accountType+storeSlug 分区，并校验 principal 的 merchantId/role；storeSlug 是当前公开别名，后续别名迁移需清理/迁移对应映射。同店同角色只维护一个当前浏览器凭证组，独立设备 sid 可并存；跨店不清其他键。跨 tab 用同源锁/消息协调刷新与 storage 同步，退出先设置本地会话 epoch/退出标记，清除对应凭证并广播；迟到刷新结果不得覆盖退出或新登录的状态，失败退出保留非凭证重试标识并提示服务端撤销未确认。重新打开浏览器在 absolute 内按需 refresh，idle 到期不是强制重登。

localStorage 长期凭证可被 XSS 窃取，键分区不是安全隔离；不记录 Authorization、refresh body 或 token 响应。生产目标另开 change 将 access 存内存、refresh 存 HttpOnly Secure Cookie，届时重新设计 CSRF/CORS；本 change 不实现前端或 Cookie 迁移。

### 6. Security 与领域服务共同落实三层授权

新 `auth/controller` → `service` → `mapper` 归于同一业务域，持久化模型置于 `entity`、请求置于 `dto`，安全与缓存使用辅助包；Mapper XML 在 auth 自己的边界内。更新 MyBatis 扫描及 `mapper/**/*.xml` 发现配置，并确保旧 Mapper 继续加载。新代码不导入 legacy 的 JwtProperties、BaseContext、User/Employee 或 Mapper。

Spring Security 使用 stateless 新 API 链，覆盖 `/api/v1/**`，匿名白名单仅为已有 health 与三个凭证入口（店铺短信、消费者登录、员工登录）；refresh/logout 使用专门的 refresh 凭证验证分支而非无条件放行；其他新 API 默认认证，具体账号路由校验角色。覆盖其他路径的兼容链放行到原 MVC 拦截器，保留旧 `/admin`、`/user` 认证；不能启用 Boot 默认登录页而锁死旧接口。prod 保持文档关闭，dev/test 文档按现有规则开放。

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

`db/seed/nonprod/R__010_local_test_tenants.sql` 与 `db/devdata/R__020_auth_demo_accounts.sql` 使用统一的三位数字描述前缀，显式表达租户先于账号。Flyway 先执行所有待执行 V，再按描述执行待执行 R；locations 的顺序不是依赖声明。V1–V4 保持不变，新 V 不得依赖 R 已创建的记录。后续实际模块在同一编号表登记依赖顺序，不提前创建商品、订单或其他模块种子。

账号种子为两个既有非生产租户建立演示首员工：均可用 demo_staff 用户名、同一合成手机号验证跨租户唯一性；样板商户至少两个消费者，对照商户至少一个同手机号消费者。实际演示手机号/用户名/显示名和公开的 dev-only 密码在 `api/docs/auth/manual-verification.md` 登记；合成手机号不发真实短信。SQL 只保存 BCrypt hash，按 merchant_code 解析既有租户。租户和账号均只补缺失记录，不覆盖已有密码、状态、名称或其他人工修改；固定 ID 与业务标识不一致或缺少预期租户时明确失败，不能用 INSERT IGNORE 或零行插入掩盖冲突。账号仅由 dev Flyway locations 加载，不把演示租户或账号作为生产初始化数据。

移除 `AuthDevDataConfiguration` 的 AFTER_MIGRATE 重跑逻辑，不使用启动回调或其他初始化器补写数据。R 仅首次发现或校验和变化时执行；上游 R 变化不自动触发下游，手动删除演示数据后普通重启不承诺修复。需补种时显式执行受控操作，仍遵守只补缺失及身份检查，不改变 history 伪造待执行。

#### 已有开发库的一次性名称过渡

采用“统一新文件名 + 一次性受控升级已有开发库 + 保留真实历史记录”。运行期不保留旧名空脚本。新空库直接执行新名字；历史库先停同库应用、备份结构和数据（包含 history），并在隔离实例恢复验证。独立维护工具固定 Flyway 11.7.2，使用目标 dev 完整 locations，默认只检查，不接入 Spring、Compose 自动启动或生产发布。检查目标实例/库名、停机确认及迁移状态；仅允许实际已执行的旧 `R__local_test_tenants.sql`、`R__auth_demo_accounts.sql` 为 Missing，新编号 R 为 Pending。未知缺失、失败、意外待迁移或 V 校验和变化均拒绝，不盲目 repair。

受控起点仅允许 V1–V4 全部成功，或 V1–V3 成功且只有固定的 `V4__auth_accounts.sql` Pending；后一分支须在 review.json 明列 pendingVersions=["4"]，不是放宽任意 V 待迁移。2026-11-06 用户已选择 B，同意在同一维护窗口补 V4 和完成种子改名。

经维护人员核对报告及备份恢复证据后显式执行一次 repair，核验仅预期旧 R 标记 Deleted、没有其他迁移元数据对齐或失败清理；随后 migrate（若 V4 待执行则先 V4，再 010、020，否则只执行两个新 R）、validate，确认原历史执行记录与现有业务数据保留。Deleted 表示脚本退役，不删除其业务数据；改名后的 R 作为新迁移登记执行。保留维护报告、备份校验和与前后 history，工具重入识别已完成过渡、不重复 repair。失败立即停止，禁止直接 UPDATE/DELETE history、clean/删卷或永久放宽校验。每个旧开发库各升级一次，过渡后不得用旧迁移包混跑；prod 不加载这些种子，不执行此次 repair。

独立测试账号放 `src/test/resources/db/fixture/auth_accounts.sql`，通过 `@Sql` 显式加载，不放 common/nonprod/devdata；每个相关用例有两个租户的员工、样板商户两名消费者、对照商户一名复用手机号的消费者，另按场景检查租户内用户名/手机号唯一性和资源归属。测试密码与 dev 演示密码隔离。HTTP 测试跨事务时按商户与稳定标识清理；数据库事务用例回滚，避免全表 count 或表空断言。

测试层次：单元覆盖规范化、BCrypt、key/claim 校验、身份范围与错误映射；HTTP 契约测试用替身身份/会话存储保持无 Docker，确认匿名 echo 变 401、已认证非法 echo 仍 400；真实 MySQL/Redis Testcontainers 覆盖 V1→V4、已有 V3 升级、唯一竞争、短信 Lua、idle/absolute、轮换重试/重放、退出刷新竞争、多会话、状态失效和断连 503。只验证自身创建的数据与容器，不读写本地 gba-dev 卷。完整 `mvn -f api/pom.xml clean verify` 必须真正运行 IT；Docker 不可用失败，报告写 target，不宣称规划已通过应用测试。

日志将认证请求 module 从固定 foundation 细化为 auth，保留 requestId、event、actorId/merchantId（可信时）、errorCode、durationMs。独立 auth audit logger 接到已有 audit.json，不创建第三种日志文件，不重复输出到 application.json。未知账号只记录脱敏/摘要标识；请求体、响应 token/debugCode、Authorization 不能直接打印。

实现时交付 `api/docs/auth/{requirements,api-design,data-design,manual-verification}.md`、OpenAPI 快照、旧接口兼容记录、测试说明与运行手册更新，README 只做导航。数据文档说明两表字段/索引、Redis key 与 TTL、种子/fixture 边界及备份恢复原则。

### 9. 按队友业务模块与本人 Agent 范围拆分，按真实能力就绪接入

完整认领清单见 [module-roadmap.md](../../../../api/docs/architecture/module-roadmap.md)，依据原始 [PRD / 设计手稿](../../../../docs/initialization/设计手稿.md) 与本次确认的分工。当前 change 只交付路线；下面均为后续目标，不新增其表或中间件。

auth 已迁移为 controller/service/mapper 三层包结构，数据模型在 entity、请求在 dto，安全/缓存/异常等使用对应辅助包。MapperScan、XML namespace/resultType、Springdoc/ControllerAdvice、测试引用同步迁移；HTTP 契约与安全行为保持。后续商品、库存、订单、物流及 Agent 相关业务域同样使用 controller/service/mapper，按需加 entity/dto/vo，不再用 api/application/domain/infrastructure 分层。具体类职责见 [packages.md](../../../../api/docs/architecture/packages.md)。

| 可认领技术模块 | 负责人 | 完整模块交付 | 供 Agent 使用的真实能力 |
| --- | --- | --- | --- |
| 商品 | 队友 | 分类、商品/SPU、SKU、属性/兼容性、上下架、公开与本商户查询 | 商品检索/详情、SKU 规格与兼容性 Service |
| 库存 | 队友 | SKU 库存、受控调整/流水、可售状态、并发/幂等 | 租户范围的 SKU 可售状态 Service |
| 订单 | 队友 | 本人/本商户订单列表详情、订单项成交快照、dev 订单 | 服务端身份限定的订单查询 Service；不做消费者下单/支付 |
| 物流 | 队友 | 按订单查询发货/承运商/轨迹、dev 物流 | 先验证订单归属的物流查询 Service；不强制真实物流 SDK |
| Agent 及全部配套 | 本人 | RAG、FC、RabbitMQ 文档任务、Redis ChatMemory，以及知识库/文件、完整聊天历史/SSE、评测 | 复用队友真实 Service，身份注入、只读查询与脱敏；不直接调用 Mapper 或 HTTP 回调自己 |

最终场景是商户上传说明书/兼容性/售后/店铺规则，消费者或客服在授权资料范围内问答；订单/库存/物流通过只读 Tool；批量 PDF/Word/Markdown 原文件可靠保存后，异步解析→切分→Embedding→Milvus 写入，MySQL 记录任务/单文档进度、幂等与失败，支持重试、死信、人工重投与部分成功，返回 taskId 供查询。Redis ChatMemory 与 MySQL 完整历史支撑连续追问。匿名只查公开商品/库存状态与公开知识，不能访问私有 Tool/历史；客服接管、人工回复、恢复 Agent 不列本次目标。

认证后四个队友模块可同时开发，但集成依赖 SKU→库存、SKU→订单→物流。本人同时搭 Agent、知识文档/RAG、完整历史/ChatMemory、MQ 任务；不等队友全部完成，不强制测试替身对接。哪个模块的真实查询 Service、授权与必要字段可用，就接对应 Tool，不等其全部管理接口/前端结束。

RAG 依赖本人知识文档/解析与基础模型入口，在本模块引 Embedding/Milvus，不等待订单或 MQ；FC 按商品/库存/订单/物流各自真实查询就绪接入；MQ 依赖本人文档与可执行处理阶段、MySQL 任务状态/幂等，不依赖 FC，但完整导入验收要有真实 Embedding/Milvus；ChatMemory 依赖认证、完整消息历史与一条基础对话链，不等待 RAG/FC/MQ 全部完成。四亮点没有强制串行链。

每人按需求→API→表/索引→实现→测试/联调交付自己的完整模块与文档/演示数据；协调 Flyway 版本和 devdata 编号。本人分别保留四亮点的真实评测/故障证据与 badcase，不预填优化百分比。CI、真实短信、生产开通/部署及课程 WebService/Nginx/Redis 并发验收另行规划；消费者下单/支付、订单超时 MQ、复杂 RBAC、平台后台不混入当前范围，未来公开入口单独设计白名单/资料范围/限流。

## Risks / Trade-offs

- [浏览器 localStorage 存长期凭证] → dev/test 与短期生产接受 XSS 窃取风险；账号类型/店铺键不等于浏览器隔离，Cookie 目标另开 change。
- [轮换重试复杂度] → 同源协调、短时 requestId 幂等和同 sid 原子撤销一并验收；不承诺仅凭请求识别真人活动。

- [所有员工共享开通权] → 明确首期合并角色的权限结果，只允许本商户范围，记录创建者审计；真正需要区分管理员/客服时单独细化角色。
- [手机号在各商户产生独立消费者账号] → 店铺首页自动带公开上下文、会话绑定商户，后续统一用户需独立迁移设计，不暗中共享账号。
- [每次受保护请求查 MySQL 和 Redis] → 先使用唯一索引保证即时停用与简单正确性，量化性能后再决定状态缓存和版本撤销机制。
- [模拟验证码在 dev 响应可见] → 只在 dev/test、显式开关下显示，prod fail-fast；默认日志不记 body，不能把此模式直接部署为真实短信。
- [旧 key 解码语义变化与新 API 开始认证] → 明示旧会话重新登录和 echo 401 变化，保留旧协议回归；不声称旧接口获得新三层授权。
- [Redis 丢失或故障] → 仅丢 active 可用有效 refresh 恢复；control/refresh 丢失要求重新登录，故障 503；不新增备份作业，不从历史 RDB/AOF 恢复会话以免撤销记录回滚使 token 复活；灾难恢复后对 auth 命名空间受控失效或轮换 key，其他缓存按本身恢复规则处理。
- [单 Redis Lua 方案未来扩展 Cluster] → 记录部署假设；Cluster 切换另开 hash-slot/脚本适配与一致性验证，不承诺当前多 key 脚本可直接跨槽运行。

## Migration Plan

1. apply 开始先冻结需求/API/业务码与兼容差异，再编写 V4；保持 V1/V2/V3 不变，受控迁移前按现有手册备份结构与数据并能在隔离库恢复。
2. 空库按 V1→V4 迁移，既有已登记 V3 库只追加 V4；两个账号表由结构迁移创建，dev 演示账号只由 devdata 加载，test 账号只由测试显式加载 fixture。租户根继续由 nonprod seed 供 dev/test 使用；prod 独立迁移服务只加载 migration/common，不包含演示租户或认证账号。
3. 完成 JWT/模拟短信配置示例与校验后本地生成真实 dev key，保留用户已有其他配置；现有 gba-dev MySQL/Redis 与数据卷继续使用。已登记旧名 R 的开发库先按第 8 节完成一次性受控升级，再启动新 dev API；空库及已升级库正常启动仅自动 migrate，不自动 repair 或额外补种。生产仍使用迁移账号独立 migrate，成功后用 DML 账号启动关闭 Flyway 的 prod 应用。
4. 加入新安全链、账号用例与会话，修正请求关联顺序及 legacy 兼容；运行单元、HTTP 与容器完整验收，导出实际 Springdoc 快照，按文档短信自动建号/员工开通/登录/刷新/退出。
5. 生产发布及首商户/首员工开通另行规划：结合商户自助入驻、首管理员绑定与平台管理方案决定创建流程和是否需要生产种子；本 change 不提供命令/HTTP 初始化入口或生产账号种子，也不执行云发布。
6. 应用回退先核验新增表与旧应用兼容；本次 DDL 为新增表，通常可保留表回退代码，但不得重新恢复弱 key 或让新 token 流入旧接口。若必须恢复数据库，按已有结构＋数据备份方案处置新增写入，不能自动 drop 新表、删卷、clean/repair 或改历史迁移。
