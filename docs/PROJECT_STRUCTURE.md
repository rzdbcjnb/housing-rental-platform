# 项目结构与类职责说明

> 更新时间：2026-07-22  
> 统计范围：`backend/src/main/java` 中 184 个顶层 Java 类型，以及前端 `src`、后端资源和测试目录。  
> 不展开：`node_modules`、`target`、`dist`、`.runtime` 等依赖、构建产物和运行数据。

## 1. 项目根目录

```text
housing-rental-platform-java/
├── backend/                     Java 后端
│   ├── src/main/java/           Java 业务源码
│   ├── src/main/resources/      Spring 配置与 MyBatis XML
│   ├── src/test/                自动化测试及 H2 测试表结构
│   ├── .mvn/                    Maven Wrapper 配置
│   ├── mvnw / mvnw.cmd          无需单独安装 Maven 的启动脚本
│   └── pom.xml                  后端依赖和构建配置
├── frontend/                    Vue 3 前端
│   ├── src/                     页面、状态、接口和组件
│   ├── public/                  公共静态资源
│   ├── start-frontend.cmd       Windows 前端启动脚本
│   └── package.json             前端依赖和命令
├── docs/                        设计、规范和结构文档
├── scripts/                     Embedding 模型准备脚本
├── .runtime/                    本地模型、Chroma 数据和日志
├── .env                         本地私密配置，Git 忽略
├── .env.example                 配置模板，不包含密钥
├── docker-compose.yml           Redis、Chroma 等容器编排
└── README.md                    项目启动和接口说明
```

后端主要依赖方向：

```text
Controller → Service → Mapper → MySQL
                    ├→ Redis
                    ├→ Chroma
                    ├→ DeepSeek
                    ├→ 阿里云 OSS
                    └→ WebSocket
```

`dto` 只接收请求，`entity` 表示数据库行或内部领域数据，`vo` 只负责响应展示。Controller 不直接调用 Mapper，AI 工具也复用现有 Service，不绕过权限和事务。

## 2. Java 包结构

```text
com.bulongyu.housing
├── common                       通用异常和分页结构
├── config                       Spring、Security、AI、OSS 配置
├── controller
│   ├── admin                    管理端接口
│   └── user                     登录用户业务接口
├── dto                          请求参数对象
├── entity                       数据库实体和内部领域对象
├── filter                       HTTP 请求过滤器
├── handler                      全局异常处理
├── mapper                       MyBatis 数据访问接口
├── security                     JWT、密码和当前用户解析
├── service
│   └── ai                       RAG、Agent、工具和 SSE
├── storage                      对象存储抽象和阿里云实现
├── vo                           接口响应对象
└── websocket                    聊天和通知实时通信
```

## 3. 根包

| 类 | 职责 |
| --- | --- |
| `HousingRentalPlatformApplication` | Spring Boot 主启动类，扫描并启动整个后端应用。 |

## 4. `common` 通用结构

| 类 | 职责 |
| --- | --- |
| `BusinessException` | 携带业务错误码、HTTP 状态和安全提示的业务异常。 |
| `ErrorResponse` | 全局异常统一返回结构，包含错误码、提示、请求编号等信息。 |
| `PageResponse<T>` | 通用分页响应，封装总数、当前页、页大小和结果列表。 |

## 5. `config` 配置

| 类 | 职责 |
| --- | --- |
| `AiAgentConfig` | 创建 Agent 线程池、SSE 线程池和心跳调度器，并限制并发与队列容量。 |
| `AiVectorStoreConfig` | 将向量库 Bean 设为懒初始化，使 Chroma 离线时不阻止主应用启动。 |
| `AliOssProperties` | 绑定 `app.oss` 配置，并计算 OSS 文件公共访问地址。 |
| `SecurityConfig` | 配置无状态 JWT 认证、接口访问规则、角色转换和跨域策略。 |
| `WebMvcConfig` | 将 `CurrentUserIdArgumentResolver` 注册到 Spring MVC。 |

## 6. `controller` 公共接口

| 类 | 职责 |
| --- | --- |
| `AreaController` | 提供地区列表查询接口。 |
| `HealthController` | 提供不需要登录的应用健康检查接口。 |
| `UploadController` | 接收图片上传和删除请求，调用图片存储服务。 |

### 6.1 `controller.admin`

| 类 | 职责 |
| --- | --- |
| `AdminController` | 管理用户、用户状态和管理端统计看板。 |
| `AdminHouseController` | 管理端房源列表、详情和审核接口。 |
| `AiAdminController` | 管理员同步已审核房源和 FAQ 到向量库。 |

### 6.2 `controller.user`

| 类 | 职责 |
| --- | --- |
| `AiActionController` | 根据一次性令牌确认收藏或代发咨询等 AI 写操作。 |
| `AiController` | AI 同步对话、会话创建、会话列表和历史消息接口。 |
| `AiStreamController` | AI 客服 POST SSE 流式对话入口。 |
| `AuthController` | 注册、登录、用户资料、唯一性检查和资料更新接口。 |
| `ChatController` | 聊天室、消息、未读数、房源卡片和已读状态接口。 |
| `CommerceController` | 发布付费、积分充值、推荐点购买和点击统计接口。 |
| `HouseController` | 房源搜索、详情、发布、编辑、删除和个人房源接口。 |
| `InteractionController` | 收藏、浏览历史及其状态查询接口。 |
| `NotificationController` | 站内消息、公告、已读和删除接口。 |
| `RecommendationController` | 相似房源和个性化房源推荐接口。 |
| `TokenController` | 使用 Refresh Token 换取新 Access Token。 |

## 7. `dto` 请求参数

| 类 | 职责 |
| --- | --- |
| `AdminStatusRequest` | 管理员启用或停用用户的请求，字段为 `isActive`。 |
| `AdminUserRequest` | 管理员创建或修改用户时提交账号、密码、电话、角色和状态。 |
| `AiActionConfirmRequest` | 确认 AI 待执行操作时提交所属会话编号。 |
| `AiChatRequest` | AI 对话请求，包含消息、会话编号和是否新建会话。 |
| `AiConversationRequest` | 新建 AI 会话时提交标题。 |
| `AnnouncementRequest` | 新建或修改公告时提交标题、正文和启用状态。 |
| `ChatCreateRoomRequest` | 创建私聊时提交目标用户和可选房源。 |
| `ChatHouseShareRequest` | 在聊天室分享房源卡片时提交房源编号。 |
| `CommercePaymentRequest` | 模拟发布付费时提交金额。 |
| `HouseAuditRequest` | 管理员审核房源时提交审核动作。 |
| `HouseIdRequest` | 只包含房源编号的通用请求。 |
| `HouseSearchToolRequest` | Agent 找房工具的白名单参数，包含地区、价格、户型和数量上限。 |
| `HouseUpsertRequest` | 发布或编辑房源时提交标题、价格、户型、地区、地址和图片。 |
| `LoginRequest` | 用户名和密码登录请求。 |
| `NotificationBatchDeleteRequest` | 批量删除通知时提交消息编号集合。 |
| `PointsRechargeRequest` | 充值推荐积分时提交积分数量。 |
| `RecommendPointsRequest` | 为指定房源购买推荐点。 |
| `RefreshRequest` | Token 刷新请求，字段为 Refresh Token。 |
| `RegisterRequest` | 注册时提交用户名、密码、电话和角色。 |
| `UpdateProfileRequest` | 修改用户名、电话和头像。 |

## 8. `entity` 数据和领域对象

| 类 | 职责 |
| --- | --- |
| `AdminHouseRow` | 管理端房源联表查询结果，包含房东、地区和审核状态。 |
| `AdminUserRow` | 管理端用户与用户资料联表查询结果。 |
| `AgentContext` | 服务端注入工具的可信上下文：用户、AI 会话和请求编号。 |
| `AgentToolTrace` | 单次工具调用的名称、状态、耗时和结果数量摘要。 |
| `AiConversation` | `ai_conversation` 会话记录。 |
| `AiMessage` | `ai_message` 消息记录及其 metadata。 |
| `Announcement` | 公告数据库实体。 |
| `AnnouncementRow` | 带作者名称的公告联表查询结果。 |
| `Area` | 省市区街道等地区层级实体。 |
| `AuthUser` | 兼容原 Django `auth_user` 表的账号实体。 |
| `ChatMessage` | 聊天消息实体，包含类型、已读、撤回和删除状态。 |
| `ChatMessageRow` | 带发送人资料的聊天消息查询结果。 |
| `ChatRoom` | 聊天室实体。 |
| `House` | 房源主实体，包含结构化户型、房东、审核和上下架状态。 |
| `HouseCandidate` | AI 混合检索阶段使用的房源候选。 |
| `HouseQuery` | 普通房源列表的结构化查询条件。 |
| `HouseRow` | 房源、地区和房东联表后的完整数据行。 |
| `IntentResult` | AI 意图结果，包含意图、约束、房源编号、检索文本和澄清问题。 |
| `InteractionEntity` | 收藏或浏览行为的基础实体。 |
| `InteractionRow` | 收藏或浏览列表中的房源联表数据。 |
| `NotificationMessage` | 站内消息数据库实体。 |
| `NotificationMessageRow` | 带发送者和关联房源名称的通知查询结果。 |
| `PaymentRecord` | 房源发布付费记录。 |
| `PointAccount` | 用户推荐积分账户及累计购买、投入数据。 |
| `RecommendPoint` | 单套房源当前推荐点实体。 |
| `RecommendStatusRow` | 推荐点、点击数和房源状态的查询结果。 |
| `RoomRow` | 聊天室列表联表结果，包含对方用户、房源、最后消息和未读数。 |
| `SearchConstraint` | AI 搜索约束，定义字段、操作符、值及硬/软强度。 |
| `UserProfile` | 用户电话、角色和头像等资料实体。 |

## 9. `filter` 与 `handler`

| 类 | 职责 |
| --- | --- |
| `RequestIdFilter` | 为每个 HTTP 请求接收或生成 Request ID，并记录方法、路径、状态和耗时。 |
| `GlobalExceptionHandler` | 将参数异常、业务异常和未知异常统一转换为 `ErrorResponse`。 |

## 10. `mapper` MyBatis 数据访问

| 类 | 职责 |
| --- | --- |
| `AdminMapper` | 查询和修改管理端用户、房源及看板统计数据。 |
| `AiMapper` | 持久化 AI 会话和消息，并执行参数化房源硬条件查询。 |
| `AreaMapper` | 查询启用的地区层级。 |
| `ChatIdentityMapper` | WebSocket/聊天场景按资料编号查询用户身份。 |
| `ChatMapper` | 聊天室、参与者、消息、在线状态、未读数和已读状态数据访问。 |
| `CommerceMapper` | 发布付费、积分账户、推荐点、购买记录和点击数据访问。 |
| `HouseMapper` | 公共房源、房东房源、详情、发布、修改、删除和审核状态数据访问。 |
| `InteractionMapper` | 收藏和浏览历史的新增、删除、查询及计数。 |
| `NotificationIdentityMapper` | 通知 WebSocket 场景按资料编号查询身份。 |
| `NotificationMapper` | 站内消息和公告的查询、新增、已读及删除。 |
| `UserMapper` | 兼容 Django 用户表和资料表，处理注册、登录、资料及唯一性查询。 |

`ChatMapper.xml`、`CommerceMapper.xml`、`HouseMapper.xml`、`InteractionMapper.xml`、`NotificationMapper.xml` 保存较复杂的动态 SQL；其余 Mapper 主要使用注解 SQL。

## 11. `security` 认证授权

| 类 | 职责 |
| --- | --- |
| `CurrentUserId` | Controller 参数注解，表示该参数应注入当前 JWT 用户编号。 |
| `CurrentUserIdArgumentResolver` | 从 Spring Security JWT Authentication 中解析当前用户编号。 |
| `DjangoPbkdf2PasswordEncoder` | 读取和生成兼容 Django `pbkdf2_sha256` 的密码。 |
| `JwtConfig` | 注册 JWT 编解码器和相关安全 Bean。 |
| `JwtProperties` | 绑定 JWT 密钥、签发者和 Access/Refresh 有效期。 |
| `JwtService` | 签发、校验和解析 Access Token 与 Refresh Token。 |

## 12. `service` 核心业务

| 类 | 职责 |
| --- | --- |
| `AdminService` | 管理用户、用户状态、房源列表和管理看板。 |
| `AuthService` | 注册、登录、资料查询修改、唯一性检查和 Token 刷新。 |
| `ChatService` | 聊天室、消息、未读、已读、房源分享和“咨询文字 + 房源卡片”事务。 |
| `CommerceService` | 发布额度、模拟支付、积分账户、推荐点和点击统计。 |
| `HouseNotificationService` | 房源新发布、审核和下架时生成相应站内通知。 |
| `HouseService` | 房源列表、详情、个人房源、发布、修改、删除和审核。 |
| `ImageStorageService` | 校验图片格式和大小，再调用对象存储上传或删除。 |
| `InteractionService` | 收藏与浏览历史业务。 |
| `NotificationService` | 站内消息、未读数、公告及批量删除业务。 |
| `PublishingService` | 计算免费发布额度，并在创建、驳回时预占、消费或退还付费记录。 |
| `RecommendationService` | 融合房源属性、用户历史和语义相关度生成推荐列表。 |

### 12.1 `service.ai` AI 客服

| 类 | 职责 |
| --- | --- |
| `AgentToolEventListener` | 工具开始与结束事件监听接口，供 SSE 输出工具状态。 |
| `AiActionService` | 在 Redis 创建一次性待确认操作，并在用户确认后执行收藏或代发咨询。 |
| `AiAgentGateway` | 支持工具对象和可信 Tool Context 的 Agent 模型网关接口。 |
| `AiConversationService` | 校验 AI 会话权限，保存用户/助手消息，并拆分 SSE 准备、完成、失败事务。 |
| `AiHouseSearchService` | 统一执行向量召回、MySQL 硬过滤和软偏好重排。 |
| `AiMetrics` | 使用 Micrometer 记录路由、工具、首段响应和完整流耗时指标。 |
| `AiModelGateway` | 不带工具的普通模型调用接口，并定义历史对话 `ChatTurn`。 |
| `AiOrchestrator` | 根据意图和任务复杂度选择普通对话、知识 RAG、房源 RAG 或 Agent。 |
| `AiStreamService` | 发送 SSE 会话、状态、工具、delta、确认、完成和错误事件，管理心跳与并发。 |
| `HybridRagService` | 将已识别意图交给统一房源搜索，并基于真实候选生成推荐说明。 |
| `IntentService` | 结合大模型结构化识别和确定性规则解析租房意图。 |
| `KnowledgeIndexService` | 管理员将已审核房源文档和租房 FAQ 同步到 Chroma。 |
| `KnowledgeRagService` | 检索 FAQ、返回来源并生成有依据的知识回答；向量库异常时安全降级。 |
| `ModelIntentParser` | 对模型返回的意图 JSON 做字段、类型、操作符和约束白名单校验。 |
| `RentalActionTools` | 暴露 `prepareFavorite` 和 `prepareSendLandlordMessage`，只准备操作不直接写库。 |
| `RentalAgentService` | 执行单 Agent，限制工具次数和超时，收集工具轨迹并处理无工具回退。 |
| `RentalReadTools` | 暴露房源搜索、详情、比较和知识查询四个只读白名单工具。 |
| `SemanticRetriever` | 从 Chroma 召回房源编号和分数；异常时返回 inactive 让 MySQL 接管。 |
| `SpringAiAgentGateway` | 使用 Spring AI `ChatClient` 注册工具、注入 Tool Context 并调用 DeepSeek。 |
| `SpringAiModelGateway` | 使用 Spring AI `ChatClient` 完成普通对话和 RAG 最终生成。 |

AI 调用链：

```text
AiController / AiStreamController
└── AiConversationService
    └── AiOrchestrator
        ├── AiModelGateway
        ├── KnowledgeRagService
        ├── HybridRagService → AiHouseSearchService
        └── RentalAgentService
            ├── RentalReadTools
            └── RentalActionTools → AiActionService → 用户确认后执行
```

## 13. `storage` 对象存储

| 类 | 职责 |
| --- | --- |
| `ObjectStorage` | 上传和删除对象的存储抽象。 |
| `AliOssObjectStorage` | 使用阿里云 OSS SDK 实现对象上传、删除和公共 URL 生成。 |

## 14. `vo` 响应对象

| 类 | 职责 |
| --- | --- |
| `AccountView` | 返回积分余额、累计购买和累计投入。 |
| `AdminDetailView` | 管理操作的简单详情响应。 |
| `AdminHouseView` | 管理端房源详情响应。 |
| `AdminStatusView` | 管理操作详情和最新启用状态。 |
| `AdminUserView` | 管理端用户列表和详情响应。 |
| `AiActionHouseView` | AI 待确认操作中展示的精简房源卡片。 |
| `AiActionResultView` | AI 确认操作结果，返回收藏或聊天消息相关编号。 |
| `AiChatResponse` | AI 同步回答，包含类型、房源、来源、待确认操作和会话编号。 |
| `AiConversationView` | AI 会话列表项。 |
| `AiHouseView` | AI 回答使用的结构化房源卡片。 |
| `AiMessageView` | AI 历史消息及 metadata。 |
| `AiPendingActionView` | AI 待确认操作预览、令牌和过期时间。 |
| `AiSourceView` | FAQ/RAG 来源编号、标题、分类和相关度。 |
| `AnnouncementView` | 公告详情响应。 |
| `AreaView` | 地区编号、名称、父级和层级。 |
| `AuthPayload` | 登录/注册响应中的用户和 Token 组合。 |
| `AuthResponse` | 认证接口外层消息和数据。 |
| `AuthUserView` | 返回给前端的安全用户资料，不包含密码。 |
| `ChatCountView` | 聊天未读数等单一计数响应。 |
| `ChatDetailView` | 聊天操作的简单详情响应。 |
| `ChatHouseShareView` | 分享房源后返回消息编号、类型和内容。 |
| `ChatHouseView` | 聊天室中的精简房源信息。 |
| `ChatLastMessageView` | 聊天室列表中的最后一条消息。 |
| `ChatMessagePage` | 聊天消息分页响应。 |
| `ChatMessageView` | 带发送者信息的聊天消息响应。 |
| `ChatOtherUserView` | 聊天对方资料和在线状态。 |
| `ChatRoomPage` | 聊天室分页响应。 |
| `ChatRoomView` | 聊天室、房源、对方用户、最后消息和未读数综合响应。 |
| `ClickView` | 房源点击量响应。 |
| `FavoriteStatus` | 当前用户是否收藏及收藏记录编号。 |
| `HouseDetailResponse` | 房源详情的外层响应包装。 |
| `HouseDetailView` | 房源完整详情、地区、房东和状态展示数据。 |
| `HouseListView` | 房源列表项，包含结构化户型和状态。 |
| `HouseSummary` | 收藏、历史、推荐等列表使用的精简房源。 |
| `InteractionCreatedView` | 新增收藏或浏览行为后的记录信息。 |
| `InteractionDetailView` | 收藏、历史操作的简单详情响应。 |
| `InteractionItemView` | 收藏或浏览历史列表项。 |
| `LandlordView` | 房东编号、用户名、电话和头像。 |
| `NotificationCountView` | 通知未读数响应。 |
| `NotificationDetailView` | 通知操作的简单详情响应。 |
| `NotificationMessageView` | 站内消息、发送者和关联房源响应。 |
| `PaymentResponse` | 模拟支付结果、记录编号和金额。 |
| `PointResponse` | 积分充值/购买结果、权重、余额和房源积分。 |
| `PublishLimitView` | 是否需要付费、剩余免费次数和已发布总数。 |
| `RecommendStatusItem` | 单套房源推荐点、权重、上限和点击数。 |
| `RecommendStatusView` | 当前用户全部房源的推荐状态集合。 |
| `RefreshResponse` | Token 刷新后的新 Access Token。 |
| `TokenPair` | Access Token 和 Refresh Token。 |
| `UniqueResponse` | 用户名或手机号是否已存在的检查结果。 |
| `UpdateProfileResponse` | 资料更新消息和最新用户信息。 |

## 15. `websocket` 实时通信

| 类 | 职责 |
| --- | --- |
| `ChatHandshakeInterceptor` | 在聊天 WebSocket 握手阶段校验 JWT，并写入用户身份。 |
| `ChatWebSocketHandler` | 维护聊天连接、在线状态，广播普通消息和房源咨询消息。 |
| `NotificationGateway` | 向通知 WebSocket 和持久化通知服务提供统一发送入口。 |
| `NotificationHandshakeInterceptor` | 在通知 WebSocket 握手阶段校验 JWT。 |
| `NotificationWebSocketConfig` | 注册通知 WebSocket 路径和握手拦截器。 |
| `NotificationWebSocketHandler` | 维护通知连接并向指定用户实时推送消息。 |
| `WebSocketConfig` | 注册聊天 WebSocket 路径和握手拦截器。 |

## 16. 后端资源

| 文件 | 职责 |
| --- | --- |
| `application.yml` | MySQL、Redis、DeepSeek、Embedding、Chroma、JWT、OSS、SSE 和端口配置。 |
| `application.properties` | 少量兼容性或补充配置。 |
| `mapper/ChatMapper.xml` | 聊天室和消息动态 SQL。 |
| `mapper/CommerceMapper.xml` | 支付、积分和推荐点动态 SQL。 |
| `mapper/HouseMapper.xml` | 房源列表、详情和条件搜索动态 SQL。 |
| `mapper/InteractionMapper.xml` | 收藏与历史列表动态 SQL。 |
| `mapper/NotificationMapper.xml` | 通知和公告动态 SQL。 |

## 17. 测试目录

```text
src/test/java
├── admin/                       管理端集成测试
├── ai/                          AI HTTP、SSE、持久化和安全集成测试
├── chat/                        聊天接口和 WebSocket 鉴权测试
├── commerce/                    支付、积分和推荐点测试
├── common/                      错误响应和分页结构测试
├── config/                      Chroma 懒初始化测试
├── controller/user/             认证接口集成测试
├── house/                       房源生命周期和权限测试
├── interaction/                 收藏与浏览历史测试
├── notification/                通知和公告测试
├── recommendation/              推荐接口测试
├── security/                    JWT、密码和当前用户解析测试
├── service/ai/                  RAG、Agent、工具、确认、SSE 和指标单元测试
└── upload/                      OSS 上传边界测试
```

`application-test.yml` 关闭真实模型和向量库并使用 H2；`schema.sql` 创建隔离测试表，因此自动测试不会写入生产 MySQL。

## 18. 前端 `src`

```text
src/
├── api/                         按业务模块封装 HTTP/SSE 请求
├── assets/                      图片等静态资源
├── components/                  复用布局和通知组件
├── router/                      页面路由与登录守卫
├── store/                       Pinia 状态管理
├── utils/                       Token 和 WebSocket 工具
├── views/                       业务页面
├── App.vue                      根组件
├── main.js                      Vue、Pinia、Router、Element Plus 入口
└── style.css                    全局样式
```

### 18.1 `api`

| 文件 | 职责 |
| --- | --- |
| `admin.js` | 管理看板、用户管理、房源审核和 AI 索引接口。 |
| `ai.js` | AI 会话、历史、POST SSE 解析和操作确认接口。 |
| `chat.js` | 聊天室、消息、已读和分享房源接口。 |
| `house.js` | 房源列表、详情、发布、修改、收藏、历史和推荐接口。 |
| `index.js` | Axios 实例、请求头、响应处理和统一错误处理。 |
| `notification.js` | 站内通知和公告接口。 |
| `upload.js` | 图片上传和删除接口。 |
| `user.js` | 注册、登录、Token 和用户资料接口。 |

### 18.2 `components`、`store`、`utils`

| 文件 | 职责 |
| --- | --- |
| `AppHeader.vue` | 顶部导航和用户入口。 |
| `AppSidebar.vue` | 侧边业务导航。 |
| `AppFooter.vue` | 页面底部。 |
| `NotificationBell.vue` | 未读通知数量和快速入口。 |
| `HelloWorld.vue` | Vite 初始示例组件，目前不属于核心业务。 |
| `store/index.js` | 创建和导出 Pinia。 |
| `store/user.js` | 用户、Token 和登录状态。 |
| `store/chat.js` | 聊天室、消息和聊天 WebSocket 状态。 |
| `store/notification.js` | 通知列表、未读数和通知 WebSocket 状态。 |
| `store/ai.js` | AI 会话、流式消息、工具状态、停止生成和待确认操作。 |
| `utils/auth.js` | Token 本地存储、读取和清理。 |
| `utils/websocket.js` | WebSocket 创建、重连和生命周期封装。 |
| `router/index.js` | 页面路由定义和登录/管理员访问守卫。 |

### 18.3 `views`

| 文件 | 职责 |
| --- | --- |
| `AdminDashboard.vue` | 管理看板、用户、房源审核和知识索引页面。 |
| `AIChat.vue` | AI 客服、SSE 增量显示、工具状态和操作确认页面。 |
| `BrowseHistory.vue` | 浏览历史页面。 |
| `ChatList.vue` | 聊天室列表页面。 |
| `ChatRoom.vue` | 实时聊天、消息和房源卡片页面。 |
| `Contracts.vue` | 合同业务占位/展示页面。 |
| `Dashboard.vue` | 用户工作台页面。 |
| `EditHouse.vue` | 编辑房源页面。 |
| `Favorites.vue` | 收藏房源页面。 |
| `Home.vue` | 首页。 |
| `HouseDetail.vue` | 房源详情和联系房东页面。 |
| `HouseList.vue` | 房源搜索与列表页面。 |
| `Houses.vue` | 房源业务入口/管理页面。 |
| `Login.vue` | 登录页面。 |
| `Notifications.vue` | 站内消息和公告页面。 |
| `Payments.vue` | 发布付费和支付记录页面。 |
| `PublishHouse.vue` | 发布房源和图片上传页面。 |
| `RecommendManage.vue` | 推荐积分充值和房源推荐点管理页面。 |
| `Register.vue` | 注册页面。 |
| `Tenants.vue` | 租客业务占位/展示页面。 |
| `UserCenter.vue` | 用户资料和个人房源入口。 |

## 19. 常见请求如何流转

### 普通房源查询

```text
HouseList.vue → api/house.js → HouseController
→ HouseService → HouseMapper → MySQL → HouseListView
```

### AI 找房

```text
AIChat.vue → api/ai.js → AiStreamController
→ AiConversationService → AiOrchestrator → HybridRagService
→ AiHouseSearchService → Chroma 召回 + MySQL 硬过滤
→ DeepSeek 生成说明 → SSE → 前端
```

### AI 代用户联系房东

```text
RentalActionTools → AiActionService 创建 Redis 确认令牌
→ 前端展示预览 → 用户确认 → AiActionController
→ ChatService 在事务中写入文字和房源卡片
→ ChatWebSocketHandler 实时推送
```

### 登录认证

```text
Login.vue → AuthController → AuthService
→ UserMapper + DjangoPbkdf2PasswordEncoder
→ JwtService → TokenPair
→ 后续请求由 Spring Security 校验 JWT
```
