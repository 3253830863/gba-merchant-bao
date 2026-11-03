# Spec Delta

## Purpose

定义商户宝消费者与商户员工从账号建立、凭证验证到会话退出的可验证行为，为后续商品、订单、物流和 Agent 提供可信身份、商户边界、角色限制与资源归属校验。

## ADDED Requirements

### Requirement: Consumers shall register explicitly within the selected merchant

系统 MUST 允许消费者在选定商户下以手机号和 REGISTER 用途短信验证码单独注册，注册成功不自动登录。消费者登录 MUST 使用 LOGIN 用途验证码且账号已经存在；登录不得创建账号。同手机号 MUST 可在不同商户独立注册，同一商户不得重复注册。登录前的商户编码由服务端解析为有效租户，客户端不得指定账号 ID、角色或内部租户 ID。

#### Scenario: Consumer completes registration and then logs in
- **WHEN** 消费者在有效商户下提交有效的 REGISTER 验证码并完成注册，随后提交新的 LOGIN 验证码登录
- **THEN** 注册返回 201 与消费者标识但不返回 token，登录返回 200 与仅属于该商户消费者的会话

#### Scenario: Login does not imply registration
- **WHEN** 尚未注册的手机号提交有效 LOGIN 验证码
- **THEN** 系统返回 401 和统一凭证失败码，不创建账号或会话，提示流程引导用户单独注册

#### Scenario: Duplicate and concurrent registrations
- **WHEN** 同一商户、同一规范化手机号的请求通过有效挑战后，发现账号已存在或并发插入发生唯一冲突
- **THEN** 最多存在一个账号，该重复注册返回 409；仅重放已消费验证码的请求返回验证码无效；不同商户的同手机号账号独立，不共享登录态或私有资源

#### Scenario: Account identity cannot be supplied by a client
- **WHEN** 注册或登录请求包含不允许的 role、merchantId、customerId、accountId 等字段
- **THEN** 请求返回 400，不采纳客户端身份，不创建提升权限的账号

### Requirement: Merchant staff accounts shall be provisioned inside the merchant

系统 MUST 采用受控员工子账号开通流程：dev 的首个员工由 devdata 演示种子提供，自动化测试员工由 test fixture 显式加载；后续员工由已登录的本商户 `MERCHANT_STAFF` 手动开通，成功不替新员工登录。匿名访问者和消费者 MUST NOT 注册员工或加入商户。新员工固定为 `MERCHANT_STAFF`，租户从创建者身份取得；首期合并角色共享开通能力，不建设 RBAC 或匿名商户注册。生产租户及首员工的建立方式延期，不在本 change 提供命令、HTTP bootstrap 接口或生产账号种子。

#### Scenario: Staff manually provisions a colleague
- **WHEN** 有效员工提交用户名、手机号和符合规则的初始密码
- **THEN** 系统返回 201 与本商户新员工标识，保存 BCrypt 密码摘要，不返回密码或 token；新员工能够单独登录

#### Scenario: Unauthorized staff registration is rejected
- **WHEN** 匿名访问者或消费者请求员工开通，或员工试图指定另一个商户/平台管理员角色
- **THEN** 匿名请求返回 401，消费者返回 403，不允许的身份字段返回 400，不产生跨租户或平台账号

#### Scenario: First staff is available in development and tests
- **WHEN** dev 加载演示数据，或自动化测试通过 `@Sql` 加载自己的 fixture
- **THEN** 各自的样板/隔离商户内均有可登录的首员工；test 不读取 devdata，生产初始化不加载演示账号

### Requirement: Staff login shall require merchant-scoped password credentials

员工登录 MUST 使用商户编码、用户名或手机号和 BCrypt 密码；用户名和手机号在商户内分别唯一。系统 MUST 规范化登录标识并消除用户名与手机号的歧义。未知商户、未知账号、密码错误、账号或商户停用 MUST 返回统一 401 凭证失败，不泄露账号存在与状态。平台管理员首期不能登录。

#### Scenario: Staff logs in with either supported identifier
- **WHEN** 员工在同一商户分别使用其用户名或手机号及正确密码登录
- **THEN** 两种方式均解析到同一个员工主体并签发独立会话，响应不含密码摘要或原始手机号

#### Scenario: Merchant boundary applies to credentials
- **WHEN** 相同用户名或手机号存在于两个商户，或者用商户 A 的密码登录商户 B
- **THEN** 系统只验证目标商户账号，不能因为另一个商户存在同名账号而成功认证

#### Scenario: Invalid or inactive credentials
- **WHEN** 未知商户、未知员工、错误密码、停用账号或停用商户提交登录
- **THEN** 系统返回相同 401 业务码与通用消息，不签发会话；连续失败触发有限时间限流

### Requirement: SMS challenges shall be scoped single-use and rate-limited

短信挑战 MUST 绑定规范化手机号、商户与 REGISTER/LOGIN 用途；使用六位随机验证码、300 秒有效期、一次性原子消费和最多五次错误尝试。发送与登录 MUST 具有共享存储支持的限流。验证码 MUST NOT 跨商户、跨用途、过期或重复使用；重新发送成功后旧验证码失效。凭证校验失败不得产生账号或登录会话。

#### Scenario: A challenge is consumed concurrently
- **WHEN** 两个请求同时使用同一有效验证码
- **THEN** 最多一个请求通过挑战校验，其余返回 400 验证码无效，不因并发重复签发会话

#### Scenario: Challenge scope and expiry are enforced
- **WHEN** 验证码被用于另一个商户、另一个手机号、另一个用途，或已过期/已使用
- **THEN** 系统返回同一个验证码无效业务码，不披露具体失败细节

#### Scenario: Excessive attempts are blocked
- **WHEN** 60 秒内重复发送、超过配置的发送配额、密码失败配额，或同一验证码达到五次错误
- **THEN** 发送/登录配额返回 429 与 Retry-After；挑战达到五次错误后失效；次数增加与过期设置在并发下有效

### Requirement: Simulated SMS shall remain a non-production development capability

首期 MUST 通过可替换短信发送边界模拟发送，不调用阿里云或其他真实短信服务。只有 dev/test 可使用模拟发送；dev 可显式开启调试验证码响应供手动联调，普通应用/审计日志不得记录验证码。prod MUST 禁止模拟发送和调试码输出；尚无真实短信适配器时短信相关能力保持不可用并返回可识别结果，不伪装成已发送。

#### Scenario: Developer exercises phone login
- **WHEN** dev 开启模拟短信与调试码回显并请求验证码
- **THEN** 响应明确提供仅开发使用的 debugCode，可用于手动注册或登录；常规日志中没有验证码、原始手机号或 token

#### Scenario: Production cannot use debug SMS
- **WHEN** prod 被配置为模拟发送或调试码回显
- **THEN** 配置校验拒绝启动；prod 未配置真实发送适配器时，验证码接口返回 503 短信不可用，不返回验证码

### Requirement: Login sessions shall require a valid JWT and an active server-side session

登录 MUST 同时具备 HS256 签名有效的 JWT 和匹配的 Redis 活跃会话；会话固定有效 7200 秒，不滑动续期，不提供 refresh token。JWT MUST 校验签名、算法、发行者、受众、过期与主体/商户/角色/会话匹配。每个受保护请求 MUST 验证当前账号及商户仍有效；多次登录允许独立并存，不能继承客户端角色。

#### Scenario: A session is created successfully
- **WHEN** 凭证校验和 Redis 会话写入均成功
- **THEN** 响应包含 accessToken、Bearer 类型、expiresIn、expiresAt 和脱敏后的主体；会话与 JWT 的绝对过期一致

#### Scenario: Token validation fails
- **WHEN** token 缺失、伪造、算法错误、issuer/audience 错误、过期、属于旧协议，或与服务端会话不匹配
- **THEN** 受保护接口返回 401，不接受仅含可信格式声明但没有活跃会话的 token

#### Scenario: An existing principal is disabled
- **WHEN** 登录之后账号或商户被受控停用，随后又请求受保护资源
- **THEN** 后续请求返回 401，该登录态不再访问资源，不等 JWT 自然过期

### Requirement: Current identity and logout shall operate on the current session

系统 MUST 提供获取当前主体与退出当前会话的接口；身份信息来自可信上下文且手机号脱敏。退出成功 MUST 撤销当前 Redis 会话，其他设备会话保持有效。对签名有效且尚未过期、但已撤销的同一 token 再次退出 MUST 幂等成功；过期/无效/无 token 仍返回 401。退出成功之后开始的受保护请求 MUST 失败，已在执行的请求不承诺取消。

#### Scenario: Logout revokes only the current device
- **WHEN** 同一账号存在两个独立会话并用其中一个退出
- **THEN** 退出返回 200，新请求使用该 token 返回 401，另一个会话仍有效

#### Scenario: Logout is retried
- **WHEN** 使用相同的有效签名、尚未过期 token 重复调用退出
- **THEN** 系统返回成功，不恢复会话或扩大撤销范围

### Requirement: Authorization shall enforce tenant role and resource ownership

授权 MUST 先限定租户，再判断角色允许的操作，再校验私有资源归属。首期只接受 `MERCHANT_STAFF` 和 `CONSUMER`，不创建角色/权限/菜单表。资源访问 MUST 在查询和写入条件中包含服务端商户及必要的消费者归属，不能接受模型或请求提供的身份覆盖。跨租户和非本人资源 MUST 与不存在资源使用相同 404，合法身份的角色越权返回 403。

#### Scenario: Consumer reads own account resource
- **WHEN** 消费者查询本人账号资料或用可信资源范围调用领域服务
- **THEN** 仅在相同商户和相同消费者归属下返回允许字段，不能读取其他消费者或商户的记录

#### Scenario: Staff accesses another staff account
- **WHEN** 员工查询员工开通结果所属的员工资源
- **THEN** 只返回本商户员工的脱敏摘要，另一个商户的 ID 和不存在 ID 均返回相同 404

#### Scenario: A future tool supplies forged identity
- **WHEN** 后续 Agent Tool 或 HTTP 调用传入与服务端上下文不一致的 merchantId/customerId
- **THEN** 领域服务不采纳该身份，并按租户、角色与资源归属拒绝访问；认证模块不提前实现订单或 Tool

### Requirement: Authentication dependencies shall fail closed

Redis 或数据库不可用时，依赖它们的登录、注册、退出和受保护请求 MUST 返回 503 与稳定依赖不可用码，不降级为仅验 JWT、匿名放行或内存会话。登录会话写入失败 MUST 不返回 token；退出删除未确认成功 MUST 不报告退出成功。消费者账号已提交但会话写入失败不得回退成自动注册逻辑。

#### Scenario: Redis fails during login or authorization
- **WHEN** 登录写会话、受保护请求读会话或退出删会话发生 Redis 故障
- **THEN** 请求返回 503，不产生假成功、不绕过校验；客户端可在恢复后重新登录或重试退出

#### Scenario: Account storage is unavailable
- **WHEN** 无法确认账号或商户当前状态
- **THEN** 请求返回 503，不使用陈旧客户端声明继续授权

### Requirement: Authentication APIs shall publish stable errors and privacy-safe evidence

认证 API MUST 使用 `/api/v1`、`code/message/data/requestId`、Bearer 认证和稳定 HTTP/业务码，requestId 与响应头相同。系统 MUST 区分 400、401、403、404、409、429、503。认证审计 MUST 记录事件、结果、请求关联和可用的商户/主体，不记录密码、摘要、完整 token、验证码或原始手机号；新接口与旧登录协议保持隔离。

#### Scenario: A security filter rejects a request
- **WHEN** 请求在 Controller 之前发生 401/403 或依赖故障
- **THEN** 响应仍使用统一 JSON envelope 和 requestId，不重定向登录页，不返回框架默认 HTML

#### Scenario: Compatibility is exercised
- **WHEN** 调用旧 `/admin`/`/user` 或把新旧 token 交叉使用
- **THEN** 旧接口保留原请求头和响应契约，新旧 token 均不能跨协议授权，旧日志不泄露登录凭证

### Requirement: Runtime JWT keys shall be strong explicit and environment-isolated

商户员工与消费者 MUST 使用各自独立随机生成的 HS256 对称密钥，配置值为 Base64，解码后不少于 32 字节；缺失、占位、非法编码、过短或两者相同 MUST 拒绝启动。dev 实际密钥 MUST 只写入忽略的 dev YAML，示例只留占位；test 使用独立测试值，prod 通过环境变量注入。文档 MUST 说明改 dev JWT YAML 只需重启 API，以及 Compose 环境变更的生效方式。

#### Scenario: Developer configures JWT signing
- **WHEN** 执行两次 `openssl rand -base64 32` 并将不同结果放入 dev 的两处 JWT 配置
- **THEN** IDEA 只激活 dev 即可启动，无需将密钥写入根 .env 或重建 MySQL/Redis；签名显式为 HS256

#### Scenario: Configuration cannot silently weaken keys
- **WHEN** 配置缺失、占位、非法 Base64、解码不足 32 字节或员工与消费者密钥相同
- **THEN** 应用在启动阶段报出不含密钥值的配置错误，不通过散列短口令或默认 itcast/itheima 自动补足

### Requirement: Development data shall support manual authentication without entering production

dev MUST 通过 `db/devdata` 提供样板和对照商户的可登录首员工、至少两名同商户消费者，以及跨商户同手机号/同用户名数据，供手动联调；repeatable 种子不得覆盖已有账号凭证或状态。自动化测试 MUST 通过 `@Sql` 从 `src/test/resources/db/fixture` 显式加载独立员工和消费者，并验证租户内唯一性、跨租户隔离及资源归属，不得依赖 devdata。两个租户根继续位于 `db/seed/nonprod`；认证业务账号不得进入 common/nonprod。生产不加载 devdata 或 test fixture；生产租户及首员工开通设计延期到后续 change。

#### Scenario: A developer starts from a fresh database
- **WHEN** dev 从空库执行结构迁移和适用开发数据
- **THEN** 可依据文档用员工密码和消费者模拟短信分别登录，也可手动完成新消费者注册与员工开通

#### Scenario: Tests and production keep seed boundaries
- **WHEN** test 或 prod 初始化
- **THEN** test 只由测试显式 fixture 加载员工和消费者，prod 不获得演示员工和消费者、也不要求当前提供生产首员工；已发布 V1/V2/V3 校验和保持不变
