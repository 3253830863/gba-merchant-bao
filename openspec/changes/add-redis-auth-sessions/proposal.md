# Proposal

## Why

商户宝已经具备 MySQL/Flyway、租户根表、Redis 和 API 基础契约，但新 `/api/v1` 尚未真正校验身份，旧登录仍是餐饮模型的 MD5/微信登录与无服务端撤销的 JWT。先交付可注册、可登录、可退出且能证明租户隔离的认证闭环，后续商品、订单和 Agent 才能安全复用服务端身份。

## What Changes

- 按“业务需求与验收 → API 契约 → Flyway 表与索引 → 领域服务 → Redis 会话与授权 → 测试、文档和联调”的顺序交付一个模块。
- 消费者通过手机号和模拟短信验证码单独注册、单独登录；未注册者不能通过登录隐式建号。消费者账号首期归属商户，同手机号可以在不同商户独立注册。
- 商户员工采用商家工作台常见的受控子账号模式：dev 以样板商户的第一个演示员工联调，自动化测试通过独立 fixture 加载测试员工；登录使用商户编码、用户名或手机号与 BCrypt 密码。后续账号由已登录的本商户员工创建，不开放匿名加入商户。
- 首期仅有 `MERCHANT_STAFF` 和 `CONSUMER` 两个有效角色；商户管理员与客服合并，因此二者暂时共享员工开通权限；平台管理员后续实现，不提前建设 RBAC。
- 使用 HS256 JWT 与 Redis 活跃会话记录，支持查询当前身份、当前会话退出、固定过期、多端独立登录和依赖故障时拒绝放行；租户、角色、资源归属三层授权复用同一身份上下文。
- 新增认证账号表与租户内唯一索引、并发注册约束、模拟短信限流和一次性校验；员工和消费者演示账号只放 devdata，测试账号只通过 `@Sql` fixture 加载；生产首员工与生产账号种子延期到后续商户入驻/平台管理方案。
- 完善实际 dev JWT 密钥、占位示例、Base64 解码与启动校验；解释根 `.env`、IDEA YAML、prod 环境变量与 Docker 命令的关系。
- 形成按业务场景推进的后续模块路线，标明 RAG、Function Calling、RabbitMQ 文档任务、Redis ChatMemory 各自的最低前置与独立评测门槛；后续模块只列路线，不在本 change 实现。

## Capabilities

### New Capabilities

- `user-auth`: 消费者注册与短信登录、dev 演示和测试 fixture 提供的首员工、商户内员工开通与密码登录、JWT/Redis 会话、三层授权及模拟短信边界。

### Modified Capabilities

无。现有 `application-technical-baseline` 和 `merchant-ecommerce-scope` 的租户、契约、迁移、兼容、数据分层与 Agent 范围要求继续适用；本 change 为认证补充具体行为，不改写既有能力。

## Impact

- 后端 `api/`：新增 `auth` 业务域、Spring Security 配置、认证 Mapper/XML、V4 增量结构、devdata/test fixture、日志及契约/集成测试；复用已有 Redis starter 和 JJWT 0.12.6，不重复引入中间件。
- API：新增 `/api/v1/auth/**` 与 `/api/v1/staff-accounts`；已有 `/api/v1/echo` 将真正要求认证，其无 token 请求从现有校验行为变为 401，属于预期认证收口，需要同步测试和调用说明。
- 配置：移除共享 YAML 的弱 JWT 默认值，保留既有 admin/user 配置名作为兼容桥接；实际密钥只进入被忽略的配置文件，真实 dev 文件在 apply 阶段本地生成。
- 兼容：旧 `/admin/**`、`/user/**` 保留旧契约和独立认证边界；新旧 token 不互通，旧 MD5 和微信账号不自动迁入。修正旧登录日志泄露风险，密钥规范化调整导致的旧会话重新登录需要明示。
- 文档：实现阶段在 `api/docs/auth/` 交付需求/API/数据/key/验收说明，在现有运行手册补充 JWT/Docker 操作，在 `api/docs/architecture/` 写后续路线；不执行真实部署，不接阿里云 SMS，不实现 Agent 或前端。
