# Housing Rental Platform

房屋租赁平台的 Java 实现。后端使用 Java 17、Spring Boot 4、Spring AI 2、MyBatis、MySQL、Redis 和 Chroma；前端使用 Vue 3、Vite、Pinia 和 Element Plus。

## 目录结构

```text
housing-rental-platform-java/
├── backend/          Java 后端源码与 Maven Wrapper
├── frontend/         Vue 前端源码与启动脚本
├── docs/             代码规范与 AI Agent 实施文档
├── scripts/          Embedding 模型准备脚本
├── .runtime/         Chroma、模型、日志和 PID（Git 忽略）
├── .env              本地私密配置（Git 忽略）
├── .env.example      配置模板
└── docker-compose.yml
```

`model_quantized.onnx_data` 是 ONNX 模型的外部权重。ONNX Runtime 从进程工作目录加载它，因此安装脚本会在项目根目录和 `backend` 各放置一份，并由 Git 忽略。

## AI 客服架构

AI 客服采用“确定性路由 + 混合 RAG + 单 Agent Tool Calling”的增强型智能体架构，不让模型生成或执行任意 SQL。

```text
用户问题
└── AiOrchestrator
    ├── 普通问答 → 模型或确定性降级
    ├── FAQ → Chroma 知识检索 → 有来源回答
    ├── 单步找房 → 语义召回 → MySQL 硬过滤 → 软偏好重排
    └── 复杂任务 → 单 Agent
        ├── searchHouses
        ├── getHouseDetail
        ├── compareHouses
        ├── searchKnowledge
        ├── prepareFavorite
        └── prepareSendLandlordMessage
```

核心边界：

- 房源状态、权限和硬约束以 MySQL 为准，Chroma 只负责召回候选。
- 工具参数经过白名单 DTO 校验，工具不接收 `userId`、SQL、表名或列名。
- Agent 只在复杂比较、详情补充和操作请求中接管；简单请求继续走可测试的 RAG 链路。
- 收藏和代发咨询不会由模型直接执行。Agent 只生成五分钟有效的一次性操作令牌，用户确认后才写业务数据。
- AI 回答使用 SSE；用户私聊与站内通知继续使用 WebSocket。
- Chroma 使用可选懒初始化，服务离线不会阻止主应用启动；模型、Chroma 或 Redis 不可用时执行安全降级，不绕过权限或确认流程。

例如“在大连租一间房，价格 2000 左右，可以更少，要两室、至少一卫并且有客厅”会被编译为地区、最高价格、卧室、卫生间和客厅硬条件，再按语义相关度及软偏好重排。

## 数据库策略

- 生产环境复用原项目 MySQL 业务表，不维护第二套房源数据。
- `spring.flyway.enabled=false`，应用不会自动修改生产表结构。
- 房源表应包含 `bedroom_count`、`living_room_count`、`bathroom_count`、`kitchen_count`。
- 测试使用隔离的 H2 MySQL 模式，不写入生产数据库。
- Django `pbkdf2_sha256` 密码可以直接登录，Java 新用户使用相同格式。

## 本地启动

要求：JDK 17、Node.js 20+、MySQL 8、Redis；启用向量检索时还需要 Chroma。

在项目根目录执行：

```powershell
Copy-Item .env.example .env
.\scripts\setup-ai-model.ps1
docker compose up -d redis chroma
.\backend\mvnw.cmd -f .\backend\pom.xml spring-boot:run
```

如果本机已经安装 Chroma，也可以执行：

```powershell
chroma run --host 127.0.0.1 --port 8000 --path .runtime/chroma
```

Spring Boot 会从项目根目录导入 `.env`。在 IDEA 中打开 `backend/pom.xml`，并将运行配置的 Working directory 设置为项目的 `backend` 目录；应用会从 `../.env`、`../.runtime` 和当前目录的 ONNX 外部权重加载本地配置。

启动前端：

```powershell
.\frontend\start-frontend.cmd
```

访问 `http://localhost:5173`。Vite 将 `/api` 和 `/ws` 代理到 `http://localhost:8080`。

首次启用 RAG 后，使用管理员 JWT 同步已审核房源和 FAQ：

```http
POST /api/admin/ai/index/sync/
Authorization: Bearer <admin-access-token>
```

## 关键配置

复制 `.env.example` 后填写数据库、模型、Redis、Chroma 和阿里云 OSS 配置。AI Agent 相关配置：

```dotenv
AI_THINKING_MODE=disabled
AI_AGENT_TIMEOUT=30s
AI_AGENT_MAX_TOOL_CALLS=6
AI_STREAM_TIMEOUT=60s
AI_STREAM_MAX_CONCURRENT=50
```

未配置模型或 Chroma 时普通业务仍可启动，房源问题回退到确定性约束检索。Redis 不可用时禁止确认 AI 写操作，不会直接执行收藏或代发消息。

## AI 接口示例

所有接口均使用登录后的 Bearer Token。

同步兼容接口：

```http
POST /api/ai/chat/
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "message": "比较刚才推荐的前两套，并告诉我哪套更适合通勤",
  "conversationId": 12,
  "newConversation": false
}
```

响应中的 `houses` 是房源卡片，`sources` 是知识来源，`pendingActions` 是需要用户确认的操作：

```json
{
  "response": "……",
  "type": "house_recommendation",
  "houses": [],
  "sources": [],
  "pendingActions": [],
  "conversationId": 12,
  "requestId": "request-id"
}
```

流式接口：

```http
POST /api/ai/chat/stream/
Accept: text/event-stream
Authorization: Bearer <access-token>
Content-Type: application/json

{"message":"收藏第一套房源","conversationId":12,"newConversation":false}
```

SSE 事件包括 `conversation`、`heartbeat`、`status`、`tool_start`、`tool_result`、`delta`、`pending_action`、`completed` 和 `error`。当前 `delta` 是对模型完整回答分段传输，尚不是上游模型逐 Token 流。

确认待执行操作：

```http
POST /api/ai/actions/<action-token>/confirm/
Authorization: Bearer <access-token>
Content-Type: application/json

{"conversationId":12}
```

服务端会校验令牌单次使用、用户与会话绑定、参数摘要、房源状态和业务权限。

## 测试与构建

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml clean test
npm --prefix .\frontend run build
```

测试覆盖认证、房源生命周期、聊天与通知、混合 RAG、Agent 工具边界、操作确认、SSE 事件与持久化、提示词注入和依赖不可用降级。

详细设计、实施状态和验收用例见 [AI Agent 升级实施计划](./docs/AI_AGENT_UPGRADE_PLAN.md)，代码生成与日志约束见 [代码规范](./docs/CODE_STYLE.md)。