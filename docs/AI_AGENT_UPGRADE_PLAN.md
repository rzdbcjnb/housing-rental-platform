# AI 客服 Agent 升级实施计划

> 文档版本：v2.0  
> 更新日期：2026-07-22  
> 当前状态：阶段 0-8 已完成，阶段 9 验收中  
> 适用项目：`housing-rental-platform-java`

## 1. 文档目的

本文档用于指导 AI 客服模块从“意图识别 + 混合 RAG 固定工作流”升级为“意图路由 + 混合 RAG + 单 Agent Tool Calling + SSE”的混合架构。

本文档同时记录设计决策、实际实现和验收结果。只有完成对应测试并勾选验收项后，能力才计入项目现状。

## 2. 改造原则

1. 保留当前已经验证的混合 RAG，不将所有请求强制交给 Agent。
2. 使用单 Agent，不引入多智能体协作。
3. 模型不生成或执行任意 SQL，只能调用服务端注册的白名单工具。
4. 工具复用现有业务 Service，不绕过权限校验直接访问 Mapper。
5. 读操作可以由 Agent 自动执行，写操作必须经过用户确认。
6. AI 流式回答使用 SSE，用户聊天和站内通知继续使用 WebSocket。
7. 不修改房源主表；第一版复用现有 `ai_message.metadata` 保存执行摘要。
8. 每完成一个模块先执行该模块测试，再执行全量测试。
9. 保留现有同步 AI 接口，升级过程不破坏前端和已有测试。
10. 不为了形式拆分过多包，AI 相关服务继续放在扁平的 `service.ai` 包中。

## 3. 当前架构

### 3.1 当前调用链（已实现）

```text
前端 AI 页面
├── POST /api/ai/chat/stream/ → SSE 状态、工具轨迹、增量回答和待确认操作
├── POST /api/ai/actions/{token}/confirm/ → 用户确认敏感操作
└── POST /api/ai/chat/ → 保留的同步兼容接口

AiConversationService
├── 校验用户与会话并持久化用户消息
├── AiOrchestrator
│   ├── 普通对话 → AiModelGateway
│   ├── 知识问题 → KnowledgeRagService
│   ├── 单步推荐 → HybridRagService → AiHouseSearchService
│   └── 复杂比较、详情与操作 → RentalAgentService
│       ├── RentalReadTools：搜索、详情、比较、知识检索
│       └── RentalActionTools：仅创建待确认操作
├── 持久化完整回答、来源、工具轨迹与待确认操作
└── AiStreamService 将执行阶段转换为 SSE 事件

AiActionService
├── Redis 保存五分钟、单次使用的确认令牌
├── 校验用户、会话、参数摘要和业务权限
└── 复用 InteractionService / ChatService 执行收藏或发送咨询
```

### 3.2 当前已经具备的能力

- AI 会话创建、历史查询和消息持久化。
- 大模型意图识别与确定性规则兜底。
- 价格、地区和户型等硬约束过滤。
- 价格目标、语义相关度等软偏好排序。
- Chroma 房源向量召回及 FAQ 知识检索。
- 模型不可用时的确定性回答兜底。
- Spring AI 白名单 Tool Calling 和有上限的单 Agent 执行。
- 复杂任务的多步搜索、详情补充与房源比较。
- 收藏、联系房东等写操作的预览与二次确认。
- SSE 状态、工具轨迹、增量回答、心跳、取消和结构化错误事件。
- 路由、工具、首段响应和完整执行耗时指标。
- 用户聊天和站内通知 WebSocket。
- 模型、Chroma、Redis 异常时的安全降级。

### 3.3 当前限制

- SSE 的 `delta` 目前对完整模型回答分段发送，不是模型上游逐 Token 流。
- 真实 MySQL、Redis 和 Chroma 仍需在本机服务启动后完成联调验收。
- 第一版不提供跨会话长期任务和多 Agent 协作。

## 4. 最终架构

### 4.1 最终调用链

```text
前端
├── HTTP POST + SSE：AI 对话
└── WebSocket：用户聊天和站内通知

AI 接入层
├── AiController：同步兼容、会话和历史接口
├── AiStreamController：SSE 对话接口
└── AiActionController：确认待执行操作

AI 编排层
└── AiOrchestrator
    ├── 确定性路由：普通对话、知识 RAG、单步房源 RAG
    └── RentalAgentService：复杂任务按需连续调用白名单工具

工具与业务层
├── RentalReadTools → AiHouseSearchService / KnowledgeRagService / HouseService
├── RentalActionTools → AiActionService（只准备，不直接写业务数据）
└── 用户确认 → InteractionService / ChatService → MySQL → WebSocket
```

### 4.2 实际目录边界

```text
com.bulongyu.housing
├── controller/user
│   ├── AiController.java
│   ├── AiStreamController.java
│   └── AiActionController.java
├── dto
│   ├── AiChatRequest.java
│   ├── HouseSearchToolRequest.java
│   └── AiActionConfirmRequest.java
├── entity
│   └── AgentContext.java
├── service/ai
│   ├── AiConversationService.java
│   ├── AiOrchestrator.java
│   ├── AiHouseSearchService.java
│   ├── HybridRagService.java
│   ├── KnowledgeRagService.java
│   ├── RentalAgentService.java
│   ├── RentalReadTools.java
│   ├── RentalActionTools.java
│   ├── AiActionService.java
│   ├── AiStreamService.java
│   ├── AiMetrics.java
│   └── AiModelGateway.java
└── vo
    ├── AiChatResponse.java
    ├── AiPendingActionView.java
    └── AiActionResultView.java
```

数据模型较小且只在单个服务内部使用时，优先使用内部 `record`，不强制为每个中间对象新建独立文件。
## 5. 路由设计

### 5.1 执行模式

| 执行模式 | 典型请求 | 处理方式 |
| --- | --- | --- |
| `GENERAL_CHAT` | “你好”“你能做什么” | 直接调用模型 |
| `KNOWLEDGE_RAG` | “押金通常怎么退” | FAQ 向量检索后生成回答 |
| `HOUSE_RAG` | “找大连两室一厅，预算 2000” | 现有混合 RAG |
| `AGENT_TASK` | “找三套，比较后收藏第二套” | Agent 连续调用工具 |

### 5.2 路由规则

1. 意图模型只生成白名单结构化候选，不生成 SQL。
2. 模型结果必须经过字段、操作符和数据类型校验。
3. 结构化结果不可用时回退到确定性规则。
4. 单步、条件清晰的推荐继续进入 `HOUSE_RAG`。
5. 包含比较、详情补充、指代引用或业务操作的多步骤请求进入 `AGENT_TASK`。
6. 路由失败时默认选择更受限的普通工作流，不默认开放工具。

## 6. 房源检索设计

### 6.1 统一检索服务

从 `HybridRagService` 抽取 `AiHouseSearchService`，统一供 RAG 和 Agent 工具调用：

```text
结构化约束
├── 硬约束
│   └── 编译为 MyBatis 参数并交给 MySQL 过滤
├── 向量召回
│   └── 使用 Chroma 缩小语义候选范围
└── 软偏好
    └── 结合向量得分和偏好匹配度排序
```

### 6.2 硬约束

- 城市和区域。
- 最高或最低价格。
- 卧室数量。
- 客厅数量。
- 卫生间数量。
- 厨房数量。
- 房源必须已审核并处于上架状态。

### 6.3 软偏好

- “2000 左右”等目标价格。
- “最好”“优先”“可以更少”等偏好表达。
- 地铁、采光、装修等文本语义偏好。
- 多个满足硬条件房源之间的排序偏好。

## 7. Tool Calling 设计

### 7.1 只读工具

| 工具 | 输入 | 输出 | 依赖 |
| --- | --- | --- | --- |
| `searchHouses` | 白名单结构化约束 | 最多 20 条候选摘要 | `AiHouseSearchService` |
| `getHouseDetail` | 房源 ID | 已发布房源详情 | `HouseService` |
| `compareHouses` | 2～5 个房源 ID | 确定性对比数据 | `HouseService` |
| `searchKnowledge` | 租房问题 | FAQ 片段和来源 | `KnowledgeRagService` |

### 7.2 待确认工具

| 工具 | 行为 | 是否直接写业务数据 |
| --- | --- | --- |
| `prepareFavorite` | 创建收藏确认操作 | 否 |
| `prepareSendLandlordMessage` | 创建“文字消息 + 房源卡片”的发送确认操作 | 否 |

真正写入数据库的操作由 `AiActionController` 在用户确认后调用：

- `InteractionService.addFavorite(userId, houseId)`
- `ChatService.sendHouseInquiry(userId, houseId, content)`

### 7.3 工具安全边界

1. 工具参数不接收 `userId`，当前用户由服务端上下文注入。
2. 工具不接收 SQL、表名、列名、URL 或 Java 方法名。
3. 搜索最多返回 20 条，传给模型最多 5 条。
4. 比较工具最多处理 5 套房源。
5. 单轮 Agent 最多调用 6 次工具。
6. 对 Agent 设置整体超时，对每个工具设置独立超时。
7. 工具异常转换为安全的结构化结果，不向模型暴露堆栈。
8. 未审核或下架房源不能通过工具返回给普通用户。

## 8. 写操作确认设计

### 8.1 执行流程

```text
Agent 判断用户需要执行写操作
  ↓
调用 prepareFavorite 或 prepareSendLandlordMessage
  ↓
服务端校验用户、房源和参数
  ↓
Redis 保存 5 分钟待确认状态
  ↓
SSE 推送 pending_action
  ↓
用户确认
  ↓
AiActionController 再次校验
  ↓
调用现有业务 Service
  ↓
删除或标记确认令牌为已使用
```

### 8.2 Redis 数据

```text
Key: ai:action:{token}
TTL: 5 分钟

Value:
├── userId
├── conversationId
├── action
├── arguments
├── argumentsHash
├── status
└── createdAt
```

确认令牌必须绑定用户和会话，只能成功执行一次。过期、跨用户、参数被修改或重复确认均返回明确错误。

### 8.3 代用户向房东发送咨询

Agent 可以根据用户要求拟定咨询内容，但不能直接发送。待确认操作必须向用户展示最终发送文本和房源卡片，用户确认后才执行。

支持的首批消息模板：

```text
感兴趣：您好，我对这套房子很感兴趣，可以聊聊吗？
索要图片：您好，可以发一下这套房子的更多细节图吗？我想进一步了解。
自定义：使用用户明确提供的文字
```

执行流程：

```text
用户要求联系某套房源的房东
  ↓
Agent 解析会话中的房源指代
  ↓
prepareSendLandlordMessage(houseId, content)
  ↓
服务端校验房源已上架、房东存在且不是当前用户
  ↓
返回“发送文字 + 房源卡片”的完整预览
  ↓
用户确认
  ↓
ChatService.sendHouseInquiry(userId, houseId, content)
  ├── 创建或获取与房东的聊天室
  ├── 保存 text 消息
  └── 保存 house_share 房源卡片消息
  ↓
事务提交后通过 WebSocket 依次广播两条消息
```

实现要求：

1. 文本和房源卡片必须关联同一个聊天室和同一套房源。
2. 聊天室创建、文本保存和房源卡片保存应在同一事务中完成。
3. WebSocket 广播失败不能回滚已经成功保存的消息，离线房东上线后仍能查询历史消息。
4. 确认页必须展示确切文本，不允许确认后由模型再次改写。
5. 文本去除首尾空白，限制为 1～300 个字符。
6. 禁止联系自己的房源、未审核房源和已下架房源。
7. 对同一用户和同一房源的短时间重复发送进行限流，避免重复确认或模型重试造成骚扰。
8. 执行结果返回聊天室 ID、文本消息 ID 和房源卡片消息 ID，前端可以直接进入聊天室。

## 9. SSE 设计

### 9.1 接口

```http
POST /api/ai/chat/stream/
Accept: text/event-stream
Authorization: Bearer <token>
Content-Type: application/json
```

现有接口继续保留：

```http
POST /api/ai/chat/
```

### 9.2 事件协议

| 事件 | 用途 | 关键字段 |
| --- | --- | --- |
| `conversation` | 返回本次会话 | `conversation_id` |
| `status` | 展示处理阶段 | `stage`、`message` |
| `tool_start` | 工具开始执行 | `tool` |
| `tool_result` | 工具执行摘要 | `tool`、`status`、`result_count` |
| `delta` | 回答文本增量 | `content` |
| `pending_action` | 等待用户确认 | `token`、`action`、`summary` |
| `completed` | 回答完成 | `message_id`、`type`、业务数据 |
| `error` | 结构化错误 | `code`、`message`、`request_id` |
| `heartbeat` | 检测断开并维持连接 | 无业务字段 |

### 9.3 事务边界

流式请求不能在整个生成期间持有数据库事务，因此拆成三个短事务：

```text
准备阶段
├── 校验用户和会话
└── 保存用户消息

生成阶段
├── 路由
├── RAG 或 Agent 执行
└── SSE 输出

完成阶段
├── 保存完整助手回答
├── 保存来源和工具摘要
└── 更新会话标题及时间
```

客户端断开或生成失败时，在元数据中记录 `cancelled` 或 `failed`，不伪装为正常完成。

## 10. 持久化与审计

第一版不修改房源表，也不新增工具审计表。工具调用摘要写入现有 `ai_message.metadata`：

```json
{
  "route": "AGENT_TASK",
  "run_id": "run-xxx",
  "status": "completed",
  "tool_calls": [
    {
      "name": "searchHouses",
      "status": "success",
      "duration_ms": 85,
      "result_count": 8
    }
  ]
}
```

满足以下任一条件后，再考虑新增独立的 `ai_tool_execution` 表：

- 管理后台需要按工具、用户或时间查询执行记录。
- 需要统计历史工具成功率和耗时分布。
- 单条消息元数据体积明显增大。
- 需要长期保留失败参数和重试状态。

## 11. 实施阶段与测试门禁

### 阶段 0：固化当前基线

- [x] 执行现有 `mvnw.cmd clean test`。
- [x] 记录测试数量及执行结果。
- [x] 保存当前同步接口的请求响应样例。
- [x] 验证现有核心租房问题只能返回正确房源。

完成标准：现有全部测试通过，未修改业务代码。

### 阶段 1：抽取 AI 编排层

- [x] 新增 `AiOrchestrator`。
- [x] 将 `AiConversationService` 与具体 RAG 实现解耦。
- [x] 保持同步接口响应格式不变。
- [x] 增加普通对话、知识 RAG、房源 RAG 路由测试。
- [x] 执行 AI 模块测试和全量测试。

完成标准：架构边界调整完成，外部行为不变。

### 阶段 2：抽取统一房源检索服务

- [x] 新增 `AiHouseSearchService`。
- [x] 迁移硬约束编译逻辑。
- [x] 迁移语义召回和软偏好排序逻辑。
- [x] `HybridRagService` 改为调用统一检索服务。
- [x] 增加硬约束、软偏好、Chroma 不可用测试。
- [x] 执行 AI 模块测试和全量测试。

完成标准：现有 RAG 结果不回退，检索能力可被工具复用。

### 阶段 3：实现只读工具

- [x] 新增 `RentalReadTools`。
- [x] 实现 `searchHouses`。
- [x] 实现 `getHouseDetail`。
- [x] 实现 `compareHouses`。
- [x] 实现 `searchKnowledge`。
- [x] 增加参数校验、权限校验和结果上限测试。
- [x] 验证工具不能访问未审核房源。
- [x] 执行 AI 模块测试和全量测试。

完成标准：四个工具可以脱离模型独立、确定性运行。

### 阶段 4：接入单 Agent

- [x] 新增 `RentalAgentService`。
- [x] 通过 Spring AI `ChatClient` 注册白名单工具。
- [x] 配置 Agent 系统提示词。
- [x] 增加最大工具次数和执行超时。
- [x] 记录工具名称、状态、耗时和结果数量。
- [x] 增加搜索后比较、详情补充等多步调用测试。
- [x] 增加模型没有调用工具时的回退测试。
- [x] 执行 AI 模块测试和全量测试。

完成标准：Agent 可以完成只读多步骤任务，不能执行写操作。

### 阶段 5：实现写操作确认

- [x] 新增 `AiActionService`。
- [x] 新增 `AiActionController`。
- [x] 实现收藏待确认操作。
- [x] 实现发送“文字消息 + 房源卡片”的待确认操作。
- [x] 在 `ChatService` 中实现事务性的 `sendHouseInquiry`。
- [x] 确认成功后通过 WebSocket 广播文本和房源卡片。
- [x] 增加咨询内容长度校验和重复发送限流。
- [x] 使用 Redis 保存短期状态并限制单次使用。
- [x] 增加过期、重复、跨用户、参数篡改和联系自己房源测试。
- [x] 执行 AI 模块测试和全量测试。

完成标准：任何业务写操作都必须由用户显式确认。

### 阶段 6：实现 SSE

- [x] 增加流式所需依赖和异步配置。
- [x] 新增 `AiStreamController` 和 `AiStreamService`。
- [x] 实现标准事件协议。
- [x] 增加心跳、超时、取消和异常处理。
- [x] 将流式事务拆成准备、生成和完成三个阶段。
- [x] 完成后持久化完整回答和工具摘要。
- [x] 增加事件顺序、异常和持久化集成测试。
- [x] 保证原同步接口测试继续通过。

完成标准：前端可以按增量接收回答，断开或失败状态可追踪。

### 阶段 7：前端接入

- [x] 在 `frontend/src/api/ai.js` 增加流式请求函数。
- [x] 使用 `fetch` POST 并解析 SSE 数据帧。
- [x] 在 AI Store 中管理生成中、工具状态和待确认操作。
- [x] AI 页面逐字追加 `delta` 内容。
- [x] 增加停止生成按钮及 `AbortController`。
- [x] 增加收藏和“发送消息 + 房源卡片”确认控件。
- [x] 连接失败时重新加载数据库中的消息历史。
- [x] 执行前端构建和相关测试。

完成标准：AI 页面完整支持流式回答和用户确认。

### 阶段 8：安全、观测和稳定性

- [x] 工具日志只记录必要参数，不记录 JWT、密码和完整敏感内容。
- [x] 增加路由次数、工具成功率和耗时指标。
- [x] 增加提示词注入、越权访问和任意 SQL 攻击测试。
- [x] 增加模型、Chroma、Redis 不可用时的降级测试。
- [x] 执行有限并发测试并记录首字耗时和完整耗时。
- [x] 不在简历中填写未经实际测试的性能数字。
- [x] 执行后端和前端全部测试。

完成标准：关键失败场景均有明确行为和测试覆盖。

### 阶段 9：文档与最终验收

- [x] 更新项目 README。
- [x] 更新接口请求响应示例。
- [x] 更新最终架构图。
- [x] 记录本地运行和依赖配置步骤。
- [x] 完成 DeepSeek V4-Pro 真实模型及两轮 Tool Calling 冒烟测试。
- [x] 按第 12 节完成自动化可覆盖项验收；真实依赖联调项见下方记录。
- [ ] 根据真实实现更新简历描述（项目代码完成后单独处理）。

完成标准：其他开发者可以根据文档启动、测试和演示完整功能。
验收记录（2026-07-22）：

- 后端执行 `mvnw.cmd clean test`：72 个测试全部通过，失败 0、错误 0、跳过 0。
- 前端执行 `npm run build`：生产构建成功。
- AI 相关 ESLint：0 个错误；保留 2 条经 DOMPurify 白名单净化的 `v-html` 规则提示。
- DeepSeek V4-Pro 鉴权、普通回答、工具调用和工具结果回传后的最终回答均已通过真实端点冒烟测试。
- 本机验收时 3306、6379、8000 端口未监听，因此真实 MySQL、Redis、Chroma 联调未计为已完成。
- 未填写未经压测得到的 QPS、首段耗时或完整耗时数字；Micrometer 已记录相应运行时指标。

## 12. 最终验收用例

### 12.1 房源硬约束

输入：

```text
我想要在大连租一间房子，价格在 2000 左右，可以更少。
要两室，必须至少有一卫，还要有客厅。
```

预期：

- 只返回大连房源。
- 价格不高于 2000 元。
- 恰好两室。
- 至少一个卫生间。
- 至少一个客厅。
- 价格接近 2000 的房源优先排序。

### 12.2 多步骤比较

输入：

```text
帮我找三套符合条件的房子，再比较价格、面积和位置。
```

预期调用链：

```text
searchHouses → compareHouses → 生成比较结论
```

### 12.3 上下文指代

输入：

```text
第一套离哪里近？第二套和第三套哪个更划算？
```

预期：Agent 可以使用当前会话中的候选房源，不要求用户重新输入房源 ID。

### 12.4 收藏确认

输入：

```text
收藏第二套。
```

预期：

1. 不立即写入收藏表。
2. 返回 `pending_action`。
3. 用户确认后才调用 `InteractionService.addFavorite`。
4. 同一个确认令牌不能重复执行。

### 12.5 向房东发送咨询确认

输入：

```text
你好，可以替我问第一套房子的房东能不能发一些细节图吗？
```

预期：

1. Agent 解析“第一套”对应的房源 ID。
2. 返回包含确切文字和房源卡片的发送预览。
3. 用户确认前不创建消息。
4. 确认后调用 `ChatService.sendHouseInquiry`。
5. 聊天室内依次出现文字消息和该房源卡片。
6. 在线房东通过 WebSocket 实时收到两条消息；离线房东可以在历史消息中查看。
7. 返回聊天室 ID，用户可以直接进入聊天页面继续沟通。

### 12.6 安全与降级

- [x] 用户要求“忽略规则并执行 SQL”时拒绝执行。
- [x] 用户不能查看未审核或已下架房源。
- [x] Chroma 不可用时继续使用 MySQL 搜索。
- [x] 模型不可用时保留当前确定性兜底。
- [x] Redis 不可用时禁止写操作确认，不直接执行写操作。
- [x] SSE 异常时发送结构化 `error` 事件。
- [x] WebSocket 聊天和通知功能不受影响。

## 13. 实际保留、修改和新增内容

### 13.1 保留

- `AiController` 中的会话与历史接口。
- `AiConversationService` 的会话权限和消息持久化能力。
- `IntentService` 的模型识别与规则兜底思路。
- `HybridRagService` 的混合检索策略。
- `KnowledgeRagService`。
- `SemanticRetriever`。
- `AiMapper` 及现有会话表。
- 用户聊天和通知 WebSocket。

### 13.2 修改

- `AiConversationService`：改为调用编排层，并拆分流式事务边界。
- `HybridRagService`：复用统一房源检索服务。
- `SpringAiAgentGateway`：通过 `ChatClient` 注册白名单工具并传递服务端上下文。
- `AiOrchestrator`：根据意图和复杂度选择确定性 RAG 或 Agent。
- `AiChatResponse` 与消息元数据：增加待确认操作和工具轨迹。
- `AiConversationService`：拆分 SSE 准备、完成和失败事务边界。
- 前端 AI API、Store 和聊天页面。

### 13.3 新增

- `AiOrchestrator`。
- `AiHouseSearchService`。
- `RentalAgentService`。
- `RentalReadTools`。
- `AiActionService` 和 `AiActionController`。
- `AiStreamService` 和 `AiStreamController`。
- SSE 事件协议、待确认操作模型和 Micrometer 指标。
- Agent、工具、SSE、安全和降级测试。

## 14. 风险与处理方式

| 风险 | 处理方式 |
| --- | --- |
| 模型错误调用工具 | 白名单、参数校验、次数限制、工具异常隔离 |
| Agent 代替用户执行写操作 | 两阶段确认，Agent 只能准备操作 |
| 流式连接占用线程 | 配置异步执行器、超时和连接上限 |
| 客户端中途断开 | 心跳检测、取消上游生成、记录取消状态 |
| 工具结果过大 | 限制结果数量和字段，禁止返回完整数据库对象 |
| RAG 与 Agent 产生两套搜索逻辑 | 统一使用 `AiHouseSearchService` |
| 模型调用测试不稳定 | 自动测试使用模拟模型，真实模型只做冒烟测试 |
| 包结构再次过度拆分 | 继续使用扁平 `service.ai`，仅按明确职责拆类 |

## 15. 架构决策记录

| 编号 | 决策 | 原因 | 状态 |
| --- | --- | --- | --- |
| ADR-001 | 使用单 Agent，不使用多智能体 | 当前业务复杂度不足以抵消多智能体成本 | 已确定 |
| ADR-002 | 保留路由工作流 | 简单请求更稳定、快速、可测试 | 已确定 |
| ADR-003 | 房源推荐保留混合 RAG | 硬过滤和语义推荐各自解决不同问题 | 已确定 |
| ADR-004 | 模型不生成任意 SQL | 降低越权、注入和错误查询风险 | 已确定 |
| ADR-005 | AI 使用 SSE | AI 回答主要是服务端单向流式输出 | 已确定 |
| ADR-006 | 用户聊天继续使用 WebSocket | 聊天需要真正的双向实时通信 | 已确定 |
| ADR-007 | 写操作必须确认 | 防止模型未经用户授权修改业务数据 | 已确定 |
| ADR-008 | 第一版不新增审计表 | 先复用消息元数据，控制改造范围 | 已确定 |
| ADR-009 | AI 代发消息采用“预览后确认” | 消息会影响真实用户，必须确保发送内容与用户确认内容一致 | 已确定 |

## 16. 变更记录

| 日期 | 版本 | 修改内容 | 修改人 |
| --- | --- | --- | --- |
| 2026-07-22 | v2.0 | 完成阶段 0-8，实现 Agent、工具确认、SSE、前端和稳定性测试，并记录最终验收状态 | Codex |
| 2026-07-21 | v1.1 | 增加 AI 代用户发送咨询文字和房源卡片的工具设计 | Codex |
| 2026-07-21 | v1.0 | 创建 AI Agent 升级实施计划 | Codex |

