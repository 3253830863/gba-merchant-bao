# Tasks

> 状态：计划稿，全部未勾选。执行顺序按「契约评审 → 表 → Service/REST → WebService → 测试 → 文档验收」。

## 1. 需求与契约评审

- [ ] 1.1 评审并按反馈修订 `api/docs/shipment/requirements.md`：冻结未发货/运输中/已签收三态、不编造预计送达、越权统一 404、trackingNo 不可绕过授权等行为。
- [ ] 1.2 评审并按反馈修订 `api/docs/shipment/api-design.md`（REST + SOAP 双契约）与 `docs/api/shipment-v1.openapi.yaml` 评审稿；确认错误码只用公共段、预留 12xxx。
- [ ] 1.3 与订单负责人（郑舒允）对齐 `orders/order_item` 表名、主键类型、`order_no` 快照字段与 `GET /api/v1/orders/{orderId}` 的授权语义；与库存负责人（区巧柔）协调 Flyway 编号顺序。
- [ ] 1.4 与老师/队友确认 WebService 信任模型（SOAP 独立信任域、服务间凭证、不绕过 REST 授权）与「WebService ≠ WebSocket」的口径。

## 2. Flyway 表结构、索引与数据边界

- [ ] 2.1 落盘 `V5__shipment_tables.sql`（编号按 1.3 协调结果调整）：shipment、shipment_track、索引、UTC 时间；不建物理外键；Testcontainers 验证约束与既有 V1–V4 校验和不变。
- [ ] 2.2 devdata `R__030_shipment_demo_data.sql`（编号协调）：两租户未发货/多段轨迹/已签收演示数据，只补缺失、幂等；test/prod 不加载。
- [ ] 2.3 test fixture `db/fixture/shipment.sql`：跨租户对照、越权场景、无物流记录订单；由 `@Sql` 显式加载，事务回滚或稳定标识清理。

## 3. ShipmentQueryService 与 REST

- [ ] 3.1 实现 `ShipmentQueryService`：按可信 Actor（消费者本人/员工同商户）+ 订单归属校验查询；SQL 层带 merchant_id 限定；订单不存在/越权统一 404/10004。
- [ ] 3.2 实现 `GET /api/v1/orders/{orderId}/shipment` Controller 与严格 DTO；ID 字符串化、UTC 时间、no-store、统一 envelope；订单未发货态返回 `NOT_SHIPPED` + 空轨迹。
- [ ] 3.3 HTTP 测试覆盖：消费者本人 200、员工同商户 200、跨租户/非本人/不存在统一 404、匿名 401、无物流记录 200/NOT_SHIPPED、预计送达缺失不返回。

## 4. WebService（SOAP/JAX-WS）端点

- [ ] 4.1 引入 Apache CXF starter（4.x Jakarta），注册 `/services/shipment` 端点，发布 `ShipmentWebServicePort`（ping + queryShipmentByOrderNo），WSDL 可访问；Springdoc 扫描排除 `/services/*`。
- [ ] 4.2 实现服务间凭证校验（SOAP Header token，配置注入、忽略文件管理），无效凭证统一 SOAP Fault，不泄露数据；REST 授权路径不受影响。
- [ ] 4.3 SOAP 端点内部复用 `ShipmentQueryService`；`merchantCode+orderNo` 不存在/跨商户 → SOAP Fault `.NOT_FOUND`；返回与 REST 同源脱敏字段。
- [ ] 4.4 测试：CXF 客户端往返（正常查询、Fault、凭证拒绝）、WSDL 稳定性快照、SOAP 调用不建立浏览器会话、REST 端点不受 `/services/*` 引入影响。

## 5. 文档与验收

- [ ] 5.1 交付 `api/docs/shipment/data-design.md`（表/字段/索引/编号协调记录）与 `manual-verification.md`（dev 联调步骤：两类主体查询、越权、SOAP 客户端演示）。
- [ ] 5.2 实现完成后以 Springdoc 导出替换 `docs/api/shipment-v1.openapi.yaml` 评审稿，逐操作核对；更新 README 导航与 Apifox 记录。
- [ ] 5.3 运行 `mvn -f api/pom.xml test` 与 `clean verify`：HTTP/MySQL/Flyway/SOAP 集成测试真实执行，Docker 不可用明确失败，报告仅写 target。
- [ ] 5.4 `openspec validate add-shipment-web-service --strict`；核对 proposal/design/tasks/specs 与 api/docs 一致；汇总已知限制（订单真实授权接入前为联调态）。
