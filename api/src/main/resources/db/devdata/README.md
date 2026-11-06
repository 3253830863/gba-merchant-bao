# dev 演示业务数据

只由 dev 加载，不进入 test/prod 的迁移 locations。
设计手稿约定一个 3C 样板租户 M_3C_DEMO 和一个最小隔离对照租户 M_ISOLATION_TEST；
二者的租户根位于 db/seed/nonprod，不在此重复创建。

后续按模块增量提供幂等演示数据：商品模块负责充电器、数据线、耳机及 SKU；
库存模块负责可售数量；订单模块负责订单及订单项快照；物流模块负责运单和轨迹；
知识/RAG 模块负责公开及私有知识；会话模块负责最小会话与消息。
当前不预建这些表，不将旧餐饮数据冒充上述样板数据。
用例需要的数据单独放 src/test/resources/db/fixture，由 @Sql 显式加载。

R__auth_demo_accounts.sql 仅在 dev 提供两个租户首员工和三个消费者，稳定用户名/手机号缺失才插入，不重置 hash/status。开发密码与联调步骤见 api/docs/auth/manual-verification.md；生产开通延期。普通 test 不加载，AuthDevManualIT 仅在独立临时基础设施显式模拟 dev 加载。
## 初始化顺序

账号按既有 merchant_code 解析租户。dev 专用 `AuthDevDataConfiguration` 的 AFTER_MIGRATE 回调在商户种子之后重跑幂等账号 SQL，避免 repeatable 字母顺序让空库遗漏账号。回调要求 dev 且非 prod，并检查 locations 显式包含 devdata；保留既有迁移名称，不更新已存在账号的凭证或状态。
