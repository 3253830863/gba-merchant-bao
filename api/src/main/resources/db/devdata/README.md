# dev 演示业务数据

只由 dev 加载，不进入 test/prod 的迁移 locations。
设计手稿约定一个 3C 样板租户 M_3C_DEMO 和一个最小隔离对照租户 M_ISOLATION_TEST；
二者的租户根位于 db/seed/nonprod，不在此重复创建。

后续按模块增量提供幂等演示数据：商品模块负责充电器、数据线、耳机及 SKU；
库存模块负责可售数量；订单模块负责订单及订单项快照；物流模块负责运单和轨迹；
知识/RAG 模块负责公开及私有知识；会话模块负责最小会话与消息。
当前不预建这些表，不将旧餐饮数据冒充上述样板数据。
用例需要的数据单独放 src/test/resources/db/fixture，由 @Sql 显式加载。

R__020_auth_demo_accounts.sql 仅在 dev 提供两个租户首员工和三个消费者，稳定用户名/手机号缺失才插入，不重置 hash/status。开发密码与联调步骤见 api/docs/auth/manual-verification.md；生产开通延期。普通 test 不加载，AuthDevManualIT 仅在独立临时基础设施显式模拟 dev 加载。

R__030_shipment_demo_data.sql 仅在 dev 提供物流演示：M_3C_DEMO 两单（运输中 3 段轨迹、已签收）与 M_ISOLATION_TEST 一单（运输中），幂等补缺。订单模块未交付，order_id/order_no 为独立演示值；NOT_SHIPPED 由服务在无运单记录时合成，不落数据行。物流联调步骤见 api/docs/shipment/manual-verification.md。

租户与账号仅补缺失记录，保留人工名称/状态/密码等资料；预期租户缺失或稳定身份冲突时通过 `dev_seed_*` 校验明确失败。R 首次或校验和变化才执行，上游变化不自动触发下游；手工删除数据后重启不自动补回。补种须显式受控执行，不改 history。
## 初始化顺序

先执行所有待执行 V，再按 R 描述的三位数字前缀排序：nonprod 的 `R__010_local_test_tenants.sql` → devdata 的 `R__020_auth_demo_accounts.sql` → devdata 的 `R__030_shipment_demo_data.sql`。locations 顺序不表达依赖，不使用 AFTER_MIGRATE 回调或启动补写。

编号统一登记：010 租户（dev/test）、020 账号（仅 dev）、030 物流运单与轨迹（仅 dev）；后续真实模块再分配三位编号及依赖，不在各模块重复维护编号。新 V 不依赖 R 数据。

旧名已执行库按 `api/docs/deployment/dev-seed-upgrade.md` 先完成一次性受控升级；不保留运行期兼容空脚本，不自动 repair，不混跑旧迁移包，不删除卷。生产只加载 migration/common，不执行开发种子改名 repair。
