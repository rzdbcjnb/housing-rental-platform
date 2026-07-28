# RAG 向量检索健康检查

## 目的

RAG 健康检查用于判断房源语义检索是否真正可用，而不是只判断 Chroma 端口能否连接。
应用进程启动与向量服务解耦：向量链路异常不会阻止应用启动，也不会影响 liveness；但会使 readiness 不可用，并由业务层按 `U1` 规则降级。

## 探针

| 接口 | 成功状态 | 含义 |
|---|---:|---|
| `GET /actuator/health/liveness` | 200 | Java 进程和 Web 服务仍可工作，不检查 RAG |
| `GET /actuator/health/readiness` | 200 | 应用可接收包含 RAG 的完整业务流量 |
| `GET /actuator/health/readiness` | 503 | RAG 未配置、Chroma 不可用、Collection 缺失/为空或索引陈旧 |
| `GET /api/health` | 200 | 兼容旧客户端的进程级接口，不代表 RAG 健康 |

探针无需登录，但默认不向匿名请求公开组件详情。日志和受保护的 Actuator 详情中不得输出 Chroma 凭据、内部地址或房源文档内容。

## 检查内容

`ragVector` readiness 组件按顺序检查：

1. `VectorStore` Bean 和 Chroma 客户端是否可以创建。
2. `SpringAiTenant/SpringAiDatabase/housing-rag` Collection 是否存在。
3. Collection 文档数量是否大于零。
4. 是否存在且仅存在一个 `type=index_state` 的索引水位文档。
5. 实际文档数是否等于 `公开房源数 * 4 + FAQ 数 + 1 个水位文档`。
6. 水位中的房源数、索引结构版本和源库最新 `update_time` 是否与 MySQL 当前值一致。

第 6 项可以识别“房源数量未变化，但标题、描述或状态已经更新”的陈旧索引。

## 状态原因

| reason | 状态 | 处理 |
|---|---|---|
| `VECTOR_STORE_UNAVAILABLE` | `OUT_OF_SERVICE` | 检查 embedding 与 vector store 开关 |
| `CHROMA_CLIENT_UNAVAILABLE` | `OUT_OF_SERVICE` | 检查 Chroma 自动配置 |
| `COLLECTION_MISSING` | `OUT_OF_SERVICE` | 核对租户、数据库和 Collection 名称后执行同步 |
| `COLLECTION_EMPTY` | `OUT_OF_SERVICE` | 执行全量同步 |
| `INDEX_MARKER_MISSING` | `OUT_OF_SERVICE` | 旧索引尚无水位，使用新版后端执行一次全量同步 |
| `INDEX_STALE` | `OUT_OF_SERVICE` | MySQL 与索引不一致，执行全量同步并定位漏同步原因 |
| `VECTOR_HEALTH_CHECK_FAILED` | `DOWN` | 查看异常类型及 Chroma、MySQL、模型运行状态 |

## 全量同步

使用管理员访问令牌调用：

```http
POST /api/admin/ai/index/sync/
Authorization: Bearer <admin-access-token>
```

同步开始时先删除旧水位标记，随后重建房源和 FAQ 文档，所有写入成功后才生成新水位。同步中途失败时 readiness 会保持不可用，避免继续使用或宣称不完整索引健康。

## 当前边界

- 现阶段同步仍是管理员触发的全量任务，不保证秒级更新。
- 水位检查能发现公开房源总数或最新更新时间变化，但不能指出具体缺失的文档。
- 阶段 3 引入 Outbox 增量同步后，应增加事件积压量、最老未处理事件年龄、失败重试次数和逐房源索引版本检查。
- 健康检查只验证链路和新鲜度，不代表召回质量达标；召回率、MRR、nDCG 仍由离线评测和真实 Chroma 集成测试负责。
