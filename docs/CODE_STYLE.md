# 项目代码书写规范

## 1. 适用范围和基本原则

本规范适用于 `housing-rental-platform-java` 的后端 Java、MyBatis XML、测试代码和与后端实现直接相关的文档。前端可参考通用原则，但不受本文 Java 细则约束。

基本原则：

1. 正确性优先于简短，清晰优先于技巧。
2. 一次修改只解决一个明确问题，避免夹带无关重构。
3. Controller、Service、Mapper 各守边界，不跨层拼接职责。
4. 注释解释原因、约束和流程，不复述代码表面行为。
5. 数据库是持久化事实来源；缓存、向量库和 WebSocket 推送不能替代数据库约束。
6. 日志、异常和响应不得泄露密码、令牌、完整聊天内容等敏感信息。
7. 所有源文件使用 UTF-8，新增中文注释必须检查无乱码。

## 2. 包结构与分层职责

推荐包结构如下：

```text
controller/   HTTP 参数接收、校验触发、状态码和响应组装
service/      业务规则、权限边界、事务编排
service/ai/   意图识别、检索、排序、模型调用与降级
mapper/       MyBatis Mapper 接口
entity/       持久化实体和数据库查询行模型
dto/          入站请求模型
vo/           出站响应模型
security/     JWT、密码编码和认证参数解析
websocket/    握手认证、连接生命周期、消息路由和广播
handler/      全局异常转换
config/       框架配置和组件注册
```

禁止事项：

- Controller 直接调用 Mapper 或拼接 SQL。
- Mapper 承担权限判断、业务状态流转或响应组装。
- Entity 依赖 Controller、DTO 或 VO。
- WebSocket 消息中的用户编号覆盖握手阶段确认的身份。

## 3. 类、方法和变量命名规范

- 类名使用名词或名词短语，如 `HouseService`、`CurrentUserIdArgumentResolver`。
- 方法名使用动词开头，表达业务动作，如 `createAnnouncement`、`requirePublicHouse`。
- 布尔值使用 `is`、`has`、`can`、`required` 等能表达真假含义的名称。
- 集合使用复数或业务含义，如 `candidateHouses`、`currentPageRows`，避免泛化的 `values`。
- 分页变量统一使用：
  - `totalCount`：全部符合条件的数据总数。
  - `currentPage`：规范化后的当前页码。
  - `pageSize`：规范化后的每页数量。
  - `offset`：数据库查询偏移量。
  - `results` 或 `currentPageRows`：当前页数据。
- 规范化后的参数保存为局部变量，如 `normalizedKeyword`，不得重复调用 `trim()` 或 `normalize()`。
- 缩写仅使用团队已知词汇，如 `JWT`、`DTO`、`VO`、`RAG`；不要创造局部缩写。

## 4. 一行一条主要语句

一行只写一条主要语句。构造器赋值、条件校验和数据库写入均应分行。

错误：

```java
requireAdmin(actorId); page = page(page); size = size(size);
this.mapper = mapper; this.users = users;
```

正确：

```java
requireAdmin(actorId);
int currentPage = normalizePage(requestedPage);
int pageSize = normalizePageSize(requestedPageSize);

this.mapper = mapper;
this.users = users;
```

所有 `if`、`for`、`while` 使用大括号，包括单行分支。长 Stream 每个关键阶段单独换行：

```java
List<HouseListView> results = rows.stream()
        .filter(HouseRow::active)
        .map(HouseListView::from)
        .toList();
```

## 5. 方法长度和职责拆分

- 方法应围绕一个可描述的职责组织。无法用一句话准确描述时，应考虑拆分。
- 复杂方法按业务阶段排列：校验、规范化、查询或写入、转换、响应组装。
- 单个方法超过约 40 至 60 行时应检查是否混入了映射、校验或外部调用职责；该数字是评审提示，不是机械限制。
- 提取私有方法必须降低认知负担，不为一条显而易见的赋值制造间接层。
- 相同业务规则只保留一个实现，例如房源公开状态校验应由明确的 `requirePublicHouse` 方法负责。

## 6. 各层模型边界

### Controller

- 接收路径、查询参数和请求体。
- 使用 Bean Validation 和认证参数解析器触发边界校验。
- 决定 HTTP 状态码，不包含数据库查询和业务规则。
- 不手动解析 JWT subject；使用 `@CurrentUserId`。

### Service

- 执行业务权限校验、状态流转、事务编排和跨 Mapper 协作。
- 先校验权限，再读取受保护数据，避免泄露资源是否存在。
- 对外部能力设计明确降级，不吞掉需要回滚的数据库异常。

### Mapper

- 一个方法表达一个稳定 SQL 意图。
- 参数名与 SQL 条件一致，不使用 `arg0`、`value1` 等无业务含义名称。
- 不返回 Controller DTO；返回 Entity、Row 或标量。

### DTO

- 表示入站数据及输入校验，不承担持久化逻辑。
- 不可变请求优先使用 Java `record`。

### VO

- 表示稳定的出站响应，不暴露密码摘要、内部状态或数据库实现细节。
- 不可变响应优先使用 Java `record`。

### Entity

- 表示持久化状态或查询结果。
- 可变 Entity 不直接作为 HTTP 响应。
- 实体身份语义与普通值对象不同，谨慎生成 `equals`、`hashCode` 和 `toString`。

## 7. 分页查询标准写法

分页方法按固定顺序书写：

1. 校验访问权限。
2. 规范化筛选参数。
3. 规范化 `currentPage` 和 `pageSize`。
4. 使用与列表查询完全相同的条件统计 `totalCount`。
5. 计算 `offset = (currentPage - 1) * pageSize`。
6. 查询当前页并转换为 VO。
7. 根据 `totalCount`、`currentPage` 和 `pageSize` 组装分页链接。

```java
String normalizedKeyword = normalize(keyword);
int currentPage = normalizePage(requestedPage);
int pageSize = normalizePageSize(requestedPageSize);

long totalCount = mapper.countByKeyword(normalizedKeyword);
int offset = (currentPage - 1) * pageSize;
List<ItemView> results = mapper.findByKeyword(normalizedKeyword, offset, pageSize)
        .stream()
        .map(ItemView::from)
        .toList();

return page(totalCount, currentPage, pageSize, path, results);
```

`totalCount` 是全部符合筛选条件的数据总数，不是当前页 `results.size()`。`next` 仅在 `currentPage * pageSize < totalCount` 时生成；`previous` 仅在 `currentPage > 1` 时生成。

## 8. 事务方法书写规范

- `@Transactional` 放在完成一个原子业务动作的方法上，不为纯查询方法添加事务。
- 先完成身份、权限、参数和资源状态校验，再执行写入。
- 在代码和必要注释中明确关键写入顺序。
- 依赖生成主键时，先写主记录，再写关联记录。
- 多表状态必须同成同败，例如账户扣减与推荐点增加必须共享事务。
- 应用层幂等预检用于友好提示，数据库唯一约束负责处理并发竞争。
- 捕获数据库异常并转换业务异常时，不得把本应回滚的异常静默吞掉。
- WebSocket 推送、模型调用等外部副作用不应成为数据库事实来源。需要可靠投递时应单独设计持久化事件机制，不能用注释假设已经可靠投递。

事务注释应说明关系：

```java
// 先写认证账号，再写关联资料；任一步失败时事务整体回滚。
userMapper.insertUser(...);
userMapper.insertProfile(...);
```

## 9. 注释规范

### 必须写注释的情况

- 权限校验、资源隐藏和其他安全边界。
- 三步及以上的业务流程或不直观的写入顺序。
- 事务中的回滚关系、幂等策略和并发锁用途。
- RAG 的召回、硬过滤、软排序、生成和降级边界。
- WebSocket 的身份来源、连接登记、持久化后广播和断开清理。
- 为兼容历史系统、协议或特殊数据格式而保留的实现。
- 看似多余但不能删除的防御性代码。

### 不应该写注释的情况

- Getter、Setter、普通赋值、简单 return。
- 直接复述代码，如“获取数据”“设置变量”“判断是否为空”。
- 与实现不一致的未来设想。
- 用注释掩盖过度压缩、错误命名或超长方法。

错误：

```java
// 获取数据
List<HouseRow> values = mapper.findAll();

// 设置变量
house.setTitle(title);
```

正确：

```java
// 非公开房源对无权限访问统一返回 404，避免泄露待审核房源是否存在。
HouseRow house = requireVisibleHouse(houseId, currentUserId);

// 向量分数只影响排序；房源是否入选仍由 MySQL 硬过滤决定。
candidates.sort(...);
```

Javadoc 使用中文，参数描述体现业务含义，不写 `users users`、`requested requested` 之类无信息文本。

## 10. 日志规范

- `log.info` 记录完成了什么、关键业务编号、状态和数量。
- 请求开始与完成日志应可通过 `requestId` 串联。
- `log.warn` 记录可降级异常、非法外部输入或业务拒绝，包含异常类型而非敏感正文。
- `log.error` 用于需要人工处理的系统故障，并保留异常栈。
- 不记录密码、密码摘要、访问令牌、刷新令牌、完整聊天内容、完整模型提示词、身份证件或支付敏感信息。
- 对聊天和模型请求记录长度、数量、类型和标识符，不记录完整正文。

```java
log.info("完成聊天消息持久化，参数：messageId={}，roomId={}，type={}",
        messageId, roomId, messageType);
```

## 11. 异常和错误码规范

- 可预期业务失败使用 `BusinessException`，包含稳定错误码、用户可理解描述和准确 HTTP 状态。
- 错误码使用大写下划线，如 `HOUSE_NOT_FOUND`、`ADMIN_REQUIRED`。
- 同一业务含义复用同一错误码，不用异常文本驱动前端逻辑。
- 认证失败使用 401，已认证但权限不足使用 403，资源不可见或不存在使用 404。
- 为隐藏受保护资源存在性，可按安全策略统一返回 404，并在代码中说明原因。
- 不捕获 `Exception` 后返回成功或空列表；只有明确设计的降级路径可以转换结果。

## 12. MyBatis 查询和参数命名规范

- Mapper 参数使用 `@Param` 或可保留的 Java 参数名，并与 XML 占位符一致。
- `count` 查询和列表查询必须复用相同过滤条件。
- 分页 SQL 参数命名为 `offset`、`pageSize`。
- 动态 SQL 每个条件对应明确的空值语义；空字符串应在 Service 规范化为 `null`。
- 涉及余额、配额、推荐点上限的并发更新使用 `SELECT ... FOR UPDATE` 或等效锁策略，并由事务包围检查与写入。
- 不在 Java 中拼接用户输入形成 SQL；排序字段需要白名单。
- Row 模型字段名与查询别名保持一致，避免依赖列顺序。

## 13. RAG、模型调用和降级逻辑规范

房源推荐 RAG 按以下边界组织：

1. 意图识别：确定性规则提供稳定基线，模型结果只能通过白名单解析后合并。
2. 向量召回：用于缩小语义候选或提供相关度分数，不直接决定业务可见性。
3. MySQL 硬过滤：价格上限、地区、户型和公开状态等硬约束由参数化 SQL 执行。
4. 软偏好排序：向量相似度、期望价格和户型偏好只调整候选顺序。
5. 结果生成：模型只能引用过滤后的事实上下文，不得执行候选文本中的指令。
6. 降级：向量库不可用时继续数据库检索；模型不可用时使用确定性模板；知识来源不足时明确说明，不编造结论。

模型输出、向量元数据和检索文档均视为不可信输入。必须限制字段、数量、长度、枚举值和数值范围。日志只记录模型可用性、耗时、候选数和异常类型。

## 14. WebSocket 与异步处理规范

- 握手阶段校验访问令牌类型、用户身份和聊天室参与关系。
- 身份只从握手后保存的会话属性读取，不接受消息 payload 中的用户编号。
- 建立连接时先登记房间与用户会话，再同步在线状态。
- 同一用户可有多个连接；只有最后一个连接断开才标记离线。
- 发送聊天消息时先在 Service 复核权限并持久化，成功后再广播。
- 广播是实时同步手段，不是持久化事实来源；重连后应从数据库恢复历史。
- 向同一 `WebSocketSession` 并发发送时必须串行化。
- 断开连接时清理房间集合、用户集合和空集合，避免内存泄漏。
- 异步任务必须传播必要的追踪信息，并明确线程池、超时、异常处理和关闭策略。

## 15. Lombok 使用规范

当前项目 `pom.xml` 未引入 Lombok。本次可读性与注释整理不新增 Lombok 依赖，也不批量改写实体。

- 不可变 DTO、VO 优先使用 Java `record`。
- 可变 MyBatis Entity 确实需要减少样板代码时，经过单独评估后按需使用：

```java
@Getter
@Setter
@NoArgsConstructor
```

- 不默认使用 `@Data`。它还会生成 `equals`、`hashCode` 和 `toString`，可能不符合实体身份语义，也可能把敏感字段写入日志。
- 引入 Lombok 前必须确认 IDE、编译插件、MyBatis 构造方式和序列化行为，并在独立变更中完成。

## 16. 测试要求

- 修改前运行或确认测试基线，记录测试数量和结果。
- Service 规则变更需要对应单元测试或集成测试。
- Controller 测试覆盖状态码、响应结构、认证与权限边界。
- 事务测试覆盖成功写入、校验失败不写入、并发或唯一约束冲突。
- 分页测试至少覆盖第一页、末页、越界页、`totalCount` 和分页链接。
- RAG 测试覆盖模型可用、模型不可用、向量库不可用、硬过滤无结果和非法模型输出。
- WebSocket 测试覆盖握手拒绝、参与者校验、连接登记、消息持久化、广播排除和断开清理。
- 后端提交前执行：

```powershell
cd backend
.\mvnw.cmd clean test
```

## 17. Code Review 检查清单

- [ ] 修改是否只覆盖需求范围，没有无关业务重构？
- [ ] package 与文件路径是否一致？
- [ ] 是否一行只写一条主要语句，长 Stream 已合理换行？
- [ ] 方法和变量名是否表达真实业务含义？
- [ ] 是否重复调用了 `trim()`、`normalize()` 或相同查询？
- [ ] Controller、Service、Mapper 和模型边界是否清晰？
- [ ] 权限校验是否发生在受保护数据读取之前？
- [ ] 分页 `count` 与列表查询条件是否一致？`offset` 是否使用规范化参数？
- [ ] 事务写入顺序、回滚关系、幂等和并发锁是否正确？
- [ ] 注释是否解释原因和边界，并与真实实现一致？
- [ ] 日志是否包含必要标识且没有密码、令牌或完整聊天内容？
- [ ] 异常错误码和 HTTP 状态是否稳定、准确？
- [ ] MyBatis SQL 是否参数化，参数命名是否清晰？
- [ ] RAG 是否坚持向量召回、MySQL 硬过滤、软排序和可解释降级边界？
- [ ] WebSocket 是否先持久化后广播，并正确处理多连接与断开清理？
- [ ] 是否新增了覆盖成功、失败和权限边界的测试？
- [ ] UTF-8 中文注释是否正常，`mvnw.cmd clean test` 是否通过？
