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
###### ai
* ai在后端api根目录下自动运行 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

###### dev profile
* 在dev profile是先运行docker-compose.yml, 然后再IDEA激活dev profile, 然后再启动后端, dev数据库账号自动执行flyway, 然后就可以断点调试

    1. 数据库+中间件
        1. 打开docker desktop
        2. 打开仓库根目录
            - .env.example 改为.env
            - 修改.env的密码
        3. Github根目录运行 docker compose up -d
            1. 停止就点docker desktop
        4. 运行带profile标记的中间件 docker compose --profile * up -d # 某个模块中间件

    2. 激活application-dev.yml
        1. IDEA打开项目
        2. 打开api\src\main\resources\application.yml
            1. ~~dev前后端联调开发, test自动化测试, prod上服务器~~
        3. Run Configuration 的 Active profiles 填 `dev` 
        4. 图片![IDEA-dev激活配置](https://github.com/user-attachments/assets/fe04c683-44f6-4ffa-a7f9-518bf7ba67fc)

    3. 依赖配置
        1. application-dev.yml.example 改为 application-dev.yml
            - <u>修改application-dev.yml的配置</u>
        2. 生成jwt密钥
            1. 任意打开本地git bash
                ```bash
                # 生成2个, 1个给root, 1个给user
                openssl rand -base64 32
                ```     
            2. 打开application-dev.yml
            3. jwt密钥放到jwt​ : secret : ​

    4. 数据库配置
        1. 初次创建数据库时, 打开api\src\main\resources\application.yml
            1. ~~数据库 表从V1开始执行~~
        2. 若像我有 未迁移前的 数据库后,
            1. baseline-on-migrate设置为true
            2. baseline-version = 1
            3. 假装库已经执行V1, 从V2开始