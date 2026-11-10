# 物流模块手工联调步骤（manual-verification）

> 状态：随实现交付。前置：仓库根 `.env` 已配置（含 `SHIPMENT_WS_TOKEN`），`docker compose up -d` 启动 gba-dev（MySQL 3307 / Redis 6380），API 以 dev profile 运行（`api/src/main/resources/application.yml` + 本地 `application-dev.yml`）。

## 1. 启动与迁移

1. 启动 API：Flyway 应依次执行 V1–V5，并加载 R__010/R__020/R__030。
2. 确认日志无迁移报错；`shipment`、`shipment_track` 表存在且含演示数据。

```sql
SELECT merchant_id, order_id, order_no, status, tracking_no FROM shipment;
SELECT shipment_id, status, description FROM shipment_track ORDER BY shipment_id, event_time;
```

## 2. 登录取 token（REST）

- 员工（M_3C_DEMO）：`POST /api/v1/auth/staff/login`，body `{"merchantCode":"M_3C_DEMO","identifier":"demo_staff","password":"DevOnly-Auth-2026!"}` → `data.accessToken`。
- 消费者：先 `POST /api/v1/stores/M_3C_DEMO/auth/sms-codes`（body `{"phone":"+8613800000401"}`，dev mock 回显 `debugCode`），再 `POST /api/v1/stores/M_3C_DEMO/auth/consumer/login`。

## 3. REST 物流查询

| 步骤 | 请求 | 预期 |
| --- | --- | --- |
| 员工查运输中 | `GET /api/v1/orders/3000000000000301/shipment` + 员工 token | 200，`IN_TRANSIT`、顺丰、`SF1000000000301`、3 段轨迹升序、`estimatedDeliveryAt` 存在 |
| 员工查已签收 | `GET /api/v1/orders/3000000000000302/shipment` | 200，`DELIVERED` + `deliveredAt`，无 `estimatedDeliveryAt` 字段 |
| 消费者查同租户 | 同上用消费者 token | 200（联调态；订单模块就绪后收口为仅本人订单） |
| 跨租户员工 | M_ISOLATION_TEST 员工查 0301 | 404/10004 |
| 不存在订单 | `GET /api/v1/orders/999/shipment` | 404/10004（与越权不可区分） |
| 匿名 | 不带 Authorization | 401/10002 |
| 非法 ID | `GET /api/v1/orders/abc/shipment` | 400/10001 |
| OpenAPI | `GET /v3/api-docs/shipment-v1` | 含 1 操作与错误响应说明；无 `/services` 内容 |

## 4. SOAP WebService（课程演示）

### 4.1 三步手工验证（SoapUI / 浏览器）

1. WSDL：浏览器/HTTP GET `http://localhost:8080/services/shipment?wsdl` → 可访问，含 `queryShipmentByOrderNo` 与 `ping`。
2. 无凭证调用（SoapUI 新建 SOAP 项目导入 WSDL）：请求被拒，Fault detail `invalid service credential`。
3. 配置凭证：请求头加 `<q:serviceToken xmlns:q="https://gba.merchantbao/shipment/v1">与 SHIPMENT_WS_TOKEN 一致的值</q:serviceToken>`（放 SOAP Header，不是 HTTP Header）。
4. 调用 `queryShipmentByOrderNo(M_3C_DEMO, SO-DEMO-0301)` → 返回 `IN_TRANSIT` 摘要与轨迹；`queryShipmentByOrderNo(M_3C_DEMO, missing)` → Fault detail `NOT_FOUND`。
5. `ping` 操作 → 连通性问候串。
6. 验证 `/services/**` 不影响 REST：匿名 `GET /api/v1/orders/.../shipment` 仍 401。

### 4.2 命令行直调（留证用，不需要装 SoapUI）

构造 envelope 文件 `soap-query.xml`（元素名以 WSDL 为准，命名空间固定 `https://gba.merchantbao/shipment/v1`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:v1="https://gba.merchantbao/shipment/v1">
  <soapenv:Header>
    <v1:serviceToken>${SHIPMENT_WS_TOKEN}</v1:serviceToken>
  </soapenv:Header>
  <soapenv:Body>
    <v1:queryShipmentByOrderNo>
      <merchantCode>M_3C_DEMO</merchantCode>
      <orderNo>SO-DEMO-0301</orderNo>
    </v1:queryShipmentByOrderNo>
  </soapenv:Body>
</soapenv:Envelope>
```

```bash
# 正常调用：应返回 IN_TRANSIT + 轨迹（SOAPAction 为空串，CXF doc/lit 不依赖它）
curl -sS -X POST http://localhost:8080/services/shipment   -H 'Content-Type: text/xml; charset=utf-8' -H 'SOAPAction: ""'   --data-binary @soap-query.xml

# 反例 1：删掉 Header 里的 serviceToken → soap:Fault，detail 含 invalid service credential
# 反例 2：orderNo 换成 missing → soap:Fault，detail 含 NOT_FOUND
```

入参是**商户编码 + 订单号**（不是 orderId，也不是运单号），走服务间授权；浏览器会话 token 对 SOAP 无效，
两条通道互不复用凭证——这正是本模块演示的「独立信任域」。

### 4.3 Java 客户端（可选，验证 JAX-WS 反序列化）

```java
// 用 WSDL 生成客户端（CXF cxf-codegen-plugin 或 wsimport），凭证通过 SOAPHandler 注入 serviceToken
var url = new java.net.URL("http://localhost:8080/services/shipment?wsdl");
var port = new com.gba.merchantbao.shipment.webservice.ShipmentWebService_Service(url)
        .getShipmentWebServicePort();
System.out.println(port.ping());   // 无凭证时会收到 Fault：invalid service credential
```

> 客户端侧提示：doc/lit wrapped 下 `@WebParam` 不要带 `targetNamespace`，否则反序列化失败（实现侧已按此约定移除）。

## 5. 自动化测试

```bash
# 需要 Docker（Testcontainers）
mvn -f api/pom.xml clean verify   # 单元测试 + 全部 IT（failsafe）
```

- `ShipmentQueryServiceTest` / `ShipmentServiceTokenInterceptorTest`：纯单元，无需 Docker。
- `ShipmentSchemaIT`：V5 DDL 约束（一单一运单、运单号租户内唯一、状态闭合、商户外键、轨迹幂等唯一）。
- `ShipmentFlowIT`：REST 授权契约全场景。
- `ShipmentSoapIT`：WSDL 可达、凭证门禁、往返摘要、NOT_FOUND、REST 链路不受影响。

## 6. 已知限制（联调态）

1. 订单模块未交付：无运单记录的订单统一 404，不返回 200/NOT_SHIPPED（`OrderAccessGuard` 端口已预留，真实实现落地后自动合成）。
2. 消费者个人归属校验（本人订单）待订单模块授权接入；当前仅租户级隔离由 SQL 谓词保证。
3. Flyway V5 编号与订单/库存模块协调（tasks 1.3）后如调整，需同步 devdata R 编号。
