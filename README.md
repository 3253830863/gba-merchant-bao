# 粤港澳商户宝 · 中小商户交易营收提升助手

> [启动后端](#-启动后端)

---

## 📖 项目简介

摘要 : 
    1.面向广州中小型消费品电商和跨境零售的中小商户，为消费者与人工客服提供商品/售后知识问答，以及订单、库存、物流查询的电商客服 Agent。
    2.使用一个3C配件电商商户作为样板租户, 商品包括充电器、数据线、耳机等, 但架构仍按多商户设计, 首期只准备一个样板租户和一个用于隔离验证的对照租户。

---

## 🏗️ 架构
系统架构图


业务架构图


## 🛠️ 后端技术栈
还需要修改

| 分类 | 技术 |
|------|------|
| 核心框架 | Spring Boot 3 + SSM（Spring + SpringMVC + MyBatis） |
| 数据库 | MySQL 8.0 + MyBatis |
| 缓存 | Redis（热点数据缓存 + 布隆过滤器） |
| 消息队列 | RabbitMQ（延迟队列处理订单超时） |
| 实时通信 | WebSocket（交易状态主动推送） |
| 存储 | 阿里云 OSS（图片存储） |
| 接口对接 | HttpClient |
| 版本控制 | Git + GitHub |

---

## ⚙️ 核心功能模块
还需要修改

### 1. 后台管理模块
- **人员管理**：员工账号 CRUD、状态管理（启用/禁用）
- **商品管理**：菜品分类、菜品 CRUD、套餐组合管理
- **SKU 管理**：商品 SKU 维度管理（口味、规格等）
- **用户暂存区**：购物车管理（添加、删除、清空）

### 2. 交易链路模块
- **订单管理**：用户下单 → 支付（模拟）→ 订单状态流转
- **订单超时处理**：RabbitMQ 延迟队列准实时释放库存
- **缓存优化**：Redis + 布隆过滤器拦截无效查询，降低数据库压力
- **实时推送**：WebSocket 推送订单状态变更，替代前端轮询

### 3. 数据统计模块
- 营业额统计（按日/周/月）
- 用户增长趋势统计
- 订单数据统计
- 商品销量排名 TOP10
- Excel 报表导出

## 🚀 启动后端

开发使用 JDK 21、Maven 和 Docker Desktop。已有配置请保留，首次配置才复制示例（仓库根 PowerShell）：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
if (-not (Test-Path api/src/main/resources/application-dev.yml)) {
    Copy-Item api/src/main/resources/application-dev.yml.example api/src/main/resources/application-dev.yml
}
docker compose up -d
```

同步 `.env` 与实际 dev YAML 中的数据库密码和端口。gba-dev 已有 MySQL/Redis，`up -d` 复用已有卷，
登录不需要额外 Redis 或 login profile。Compose 配置变化后使用 `up -d` 应用；`restart` 只重启现有进程。

在本机有 OpenSSL 的终端运行两次，分别生成员工和消费者 JWT 密钥：

```bash
openssl rand -base64 32
openssl rand -base64 32
```

将结果分别写入实际 dev YAML 的 `merchant.auth.jwt.merchant-staff-secret-base64` 和
`merchant.auth.jwt.consumer-secret-base64`，保留其他设置。员工是 MERCHANT_STAFF，与 MySQL root
无关；两把密钥必须不同且各解码至少 32 字节。已有合规值可保留，真实值只放被忽略的实际 YAML，
不要放示例、根 `.env` 或 Compose；旧 `merchant.jwt` 自动桥接。修改 YAML 后重启 API，换密钥后重新登录。

IDEA 运行 MerchantApplication：JDK 21、工作目录 `api`、Active profiles 为 `dev`；或在 api 目录执行：

```powershell
mvn spring-boot:run '-Dspring-boot.run.arguments=--spring.profiles.active=dev'
```

空库自动迁移 V1→V4；未登记的历史非空库须按 [显式 baseline 手册](api/docs/deployment/flyway-baseline.md)
先核验和备份，保持自动 baseline 关闭。详细配置、已有卷和凭据边界见
[本地持久联调](api/docs/deployment/local-development.md)。健康检查为 http://localhost:8080/api/v1/health 。
