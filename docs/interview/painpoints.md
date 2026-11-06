# 技术问题与决策记录

更新：2026-11-06。本页记录认证实现中已有代码、测试或维护记录可追溯的问题；“设计约束”“隔离测试构造故障”“实际开发库维护”分别标明，不把它们包装成线上事故。认证面试只选 AUTH-01、AUTH-02 两个主讲难点，其余保留为备查。

共同来源：[实现 PR #12](https://github.com/e-commerce-agent/gba-merchant-bao/pull/12)、[最终验收](../../api/docs/auth/verification.md)、[四层面试卡片](auth.md)、[归档设计](../../openspec/changes/archive/2026-11-06-add-redis-auth-sessions/design.md)。全部数字对应代码规则或具名测试；本模块没有线上性能指标。

| 编号 / 类型 | 问题或现象 | 原因与处理 | 证据 / commit | 面试主讲 |
| --- | --- | --- | --- | --- |
| AUTH-01 / 设计取舍 | 未登录消费者也必须先定位商户，同手机号跨商户独立 | 用店铺路径 storeSlug；本期复用 merchantCode，不增加独立别名表。公开 locator 不授予私有权限。对比 body、Header、域名与 JWT 的取舍见面试卡片 | AuthInput#storeSlug、TenantResolver#resolveActive、AuthFlowIT HTTP/资源范围；`c4802d2` / `68b660c` | 是 |
| AUTH-02 / 用例及失败边界 | 首次和再次登录要统一，又不能重复建号或产生“账号存在但假登录成功” | 一次消费验证码 → 事务建号/复用 → 提交 → Redis 建会话。独立挑战竞争用唯一约束；Redis 失败保留账号、503 且无 token；消费者均成功返回 200 | AuthFlowIT#independentChallengesRacingFirstLoginReuseOneCommittedAccount、#databaseFailureConsumesChallengeAndSessionFailureKeepsCommittedAccount；`68b660c` | 是 |
| AUTH-03 / 并发验证 | 同验证码可能同时提交；旧发送失败清理可能误删新挑战 | Lua 校验并消费；challengeId 区分代际，发送完成才激活，清理只针对本代。第五次错误失效 | AuthSmsRedisIT#concurrentConsumptionHasExactlyOneWinner、#pendingCannotBeUsedAndOldSendFailureCannotDeleteNewChallenge；`68b660c` | 否 |
| AUTH-04 / 重试设计 | refresh 响应丢失后直接重试，可能被误当重放；存明文响应又会暴露 token | 同旧摘要+同 UUID+默认 10 秒+仍为当前代时返回 AES-GCM 加密缓存结果，其余已知旧值重放撤销当前 sid；未知值不误撤销 | AuthFlowIT#rotationRetryIsEncryptedIdempotentAndReplayRevokesOnlyItsDevice、#oldGenerationWindowAndTamperedCacheNeverReturnCredentials；`68b660c` | 否 |
| AUTH-05 / 并发验证 | refresh 或在途业务 touch 可能与 logout 竞争 | 同 control 上原子检查撤销；touch 不创建已丢失 active，退出清重试缓存，保留撤销墓碑至 absolute | AuthFlowIT#concurrentRefreshAndLogoutNeverResurrects、#touchOnlyOnSuccessfulAllowedWorkAndCannotResurrectLogout；`68b660c` | 否 |
| AUTH-06 / 生命周期 | 只有一个 TTL 无法区分闲置、重新凭证登录和永久续命 | active 控制 idle，control/refresh 保留至原始 absolute；有效 refresh 可恢复 idle，absolute 不滑动，me 不 touch | AuthFlowIT#idleCanRecoverButAbsoluteAndLostControlOrIndexCannot；可控时钟而非等待 30 天；`68b660c` | 否 |
| AUTH-07 / 授权边界 | 客户端传商户/主体 ID 或全局查询资源可能串租户、泄露资源是否存在 | 私有 SQL 只取 Actor 商户，消费者另限定本人；未知 DTO 字段拒绝，跨租户/非本人/不存在统一 404，角色错 403 | AuthPersistenceIT、AuthFlowIT#httpContractStrictInputRolesResourcesAndDedicatedBranches；`c4802d2` / `68b660c` | 否 |
| AUTH-08 / 凭证与配置 | 旧 JWT 有弱默认、短 key 补长及新旧 token 混用风险 | 两把不同 Base64 key 解码至少 32 字节，显式 HS256，校验 claims 和环境；新旧协议隔离，旧会话重登 | AuthJwtConfigurationTest、AccessTokenServiceTest、LegacyJwtCompatibilityTest、JwtPackagingIT；`9db2a3e` | 否 |
| AUTH-09 / 密码输入 | BCrypt 的字节边界与字符数不同；未知账号直接跳过校验有成本差异 | 密码不 trim，8–64 字符且 UTF-8 至多 72 字节；未知账号 dummy BCrypt；用户名/手机号共享实际员工失败锁定 | AuthCredentialTest、AuthInputTest、AuthFlowIT#staffLoginSharesIdentifierLockAndAllCredentialFailuresLookTheSame；`68b660c` | 否 |
| AUTH-10 / 依赖故障验证 | 仅验 JWT 会在 Redis/MySQL 故障或账号停用后错误放行 | 保护请求检查会话和主体，依赖异常 503，停用拒绝；退出允许凭有效凭证撤销停用主体的当前会话 | AuthFlowIT#subjectDisableRejectsPrivateAndRefreshButAllowsLogout、#concurrentSameRefreshRequestReturnsOnePairAndDependencyFaultsUse503；`68b660c` | 否 |
| AUTH-11 / 日志与上下文 | 旧日志输出登录 DTO/微信 code；复用线程可能遗留身份 | 移除敏感日志，认证审计分流，finally/afterCompletion 清 SecurityContext/BaseContext；requestId 排在安全链前 | JsonLoggingTest、AuthContextTest、HttpClientTest；`68b660c` | 否 |
| AUTH-12 / Flyway 交付修正 | 租户/账号旧 R 描述顺序不能表达依赖，回调重跑与普通迁移混杂 | 种子改为 010 租户、020 账号，移除回调；R 仅首次或 checksum 变化执行，上游变更不自动重跑下游 | DevSeedMigrationIT 排序/重入/并发；`fe4657c` | 否 |
| AUTH-13 / 实际开发库维护 | 2026-11-06 预检发现实际库到 V3，旧 R 改名后普通 migrate 会受 Missing 历史阻断 | 用户确认精确 V3 分支，18:25–18:27 停同库应用、完整备份并隔离恢复，显式退役已知旧 R，再 V4→010→020→validate，保留原历史与数据 | [开发库记录](../../api/docs/deployment/dev-seed-upgrade.md)；升级后 2 租户/2 员工/3 消费者是开发数据数量；`fe4657c` | 否 |
| AUTH-14 / 数据安全 | 重跑种子可能覆盖人工密码/停用状态，INSERT IGNORE 可能掩盖身份冲突 | 只补缺失；稳定 ID/编码/手机号/归属冲突明确失败；test 用独立 fixture，prod 不装演示账号 | DevSeedMigrationIT、AuthDevManualIT；`fe4657c` | 否 |
| AUTH-15 / 构建与包迁移 | 移动包后旧 class/SQL 可残留，扫描/XML/Springdoc 引用可能失效 | 29 类迁移时同步所有引用，clean verify 清除生成残留，逐类核对方法体未变；不将只改目录等同于验收完成 | 44 单元/契约 + 62 IT 全过，[包结构](../../api/docs/architecture/packages.md)；`7f09f0b` | 否 |
| AUTH-16 / 测试边界 | 定向测试成功可能被误认为全量成功；固定开发端口会污染业务卷 | Docker 不可用失败，完整 clean verify 实际执行 Failsafe，动态端口/可销毁容器/报告仅 target | [测试说明](../../api/docs/testing/README.md)、最终验收 106 项；没有 Apifox/浏览器/性能测试结论；`7f09f0b` | 否 |
| AUTH-17 / 文档与版本策略 | 归档目录增加一级后相对链接失效；本地主规格被仓库忽略 | 修正归档 design 的相对链接；同步 14 条主规格并逐段对比。保持 openspec/.gitignore 的 `/specs/` 规则，提交归档 delta 作为可追溯来源 | 本次 docs/auth-interview；新 commit 和 mini PR 尚待作者创建 | 否 |

## 证据保存约定

每次追加问题时注明：问题类型、实际触发条件、发现工具/用例、修复或设计决策、验证结果、限制、commit 和 PR。代码中的 TTL/位数是参数，类中的测试数量是用例数量，Maven 总耗时是构建时间；均不能替换成并发数、接口耗时或线上成功率。

原始测试 XML、脚本输出、开发备份按现有忽略规则保留，仓库只保存脱敏摘要与可运行步骤。没有原始观测依据时写“未测”，不补估计值；后续真实压测或生产问题另行追加，不回写成当前实现已经测得的收益。
