# Stage 0 房源检索评测集

## 目的

该数据集用于固定当前 Chroma + MySQL 检索链路的质量基线，并验证后续检索改动是否真正改善召回、排序和零命中行为。

数据集版本为 `stage0-v1`，来源是 2026-07-27 本地 MySQL 中 `status=approved AND is_active=true` 的虚构房源数据。

## 文件

- `houses.jsonl`：110 套公开房源的检索字段快照，按 `house_id` 排序。
- `queries.jsonl`：150 条评测查询、结构化约束、预期结果和三级相关性标注。
- `manifest.json`：数量、类别分布、标注状态以及两个 JSONL 文件的 SHA-256。
- `baselines/mysql-fallback/baseline.md`：改造前 MySQL 降级路径的人类可读基线。
- `baselines/mysql-fallback/baseline.json`：同一基线的机器可读指标。
- `baselines/mysql-fallback/predictions.jsonl`：145 条可执行查询的逐查询返回结果。
- `checkpoints/after-0.2/COMPARISON.md`：E1 + U1 改造前后指标、状态分布和结论。
- `checkpoints/after-0.2/mysql-fallback/`：0.2 后的报告、机器指标和逐查询结果。

## 查询分布

| 类别 | 数量 | 说明 |
|---|---:|---|
| `STRUCTURED` | 45 | 地区、价格和户型硬条件 |
| `SEMANTIC` | 40 | 地铁、家电、环境、海景、家庭等软偏好 |
| `MIXED` | 40 | 硬条件与语义偏好的组合 |
| `ZERO_HIT` | 20 | 10 条硬条件零命中和 10 条语义零命中 |
| `CONFLICT_OR_DIRTY` | 5 | 4 条非法/冲突约束和 1 条区域完整性场景 |

## 相关性约定

- `2`：明确相关。房源满足全部硬条件，或文本包含语义特征的明确证据。
- `1`：弱相关。房源满足全部硬条件，但语义特征只有间接证据。
- `0`：不相关。`judgments` 中未列出的房源默认相关性为 0。

`label_status=AUTO_DERIVED` 表示标签由确定性约束推导。`label_status=HUMAN_CONFIRMED` 表示语义标签已经按 V1/F2/L2/Z1 完成人工确认。

## 指标口径

按决策 `M3 + K1` 输出指标：

- 宽松口径将相关性 `>= 1` 视为相关，严格口径只将相关性 `= 2` 视为相关。
- 两种口径均输出 `Recall@10/20/50`、`Precision@5/10` 和 `MRR`。
- 三级相关性直接用于 `nDCG@10/20`，增益函数为 `2^grade - 1`。
- Recall、Precision、MRR 和 nDCG 均在 125 条预期有结果的查询上做宏平均；Precision 的分母固定为 K。
- 20 条预期空结果查询单独计算零命中正确率，不混入上述排序指标。

## 0.2 检查点

同一数据集、向量关闭环境下，E1 + U1 使零命中正确率从 50% 提升到 100%，错误返回房源数从 200 降为 0。95 条带硬条件查询标记为 `DEGRADED_STRUCTURED`，50 条无硬条件查询标记为 `RETRIEVAL_UNAVAILABLE`。

Recall@20 同时从 0.6460 降至 0.6125，因为纯语义查询不再用任意房源虚增召回。该下降是故障语义变得真实后的预期结果；向量正常质量必须在 Chroma 恢复后单独评测。

## 数据质量标记

20 套房源直接关联到城市级“大连”，缺少区县和街道。它们被保留，并在 `data_quality_flags` 中标记：

- `REGION_NOT_STREET`
- `REGION_HIERARCHY_INCOMPLETE`

这些标记用于区分检索算法问题和源数据质量问题，不会自动修改数据库。

## 重新生成

在项目根目录编译生成器：

```powershell
javac -encoding UTF-8 -d backend\target\classes scripts\evaluation\Stage0DatasetBuilder.java
```

设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 后运行：

```powershell
java -cp "backend\target\classes;PATH_TO_MYSQL_CONNECTOR_JAR" Stage0DatasetBuilder backend\src\test\resources\evaluation\stage0-v1
```

生成器只执行 `SELECT`，并在数量或 Q1 分布不符合决策时直接失败。数据源发生实质变化时应创建新版本目录，不应覆盖已经出具基线报告的版本。

## 校验

```powershell
cd backend
.\mvnw.cmd -Dtest=Stage0EvaluationDatasetTest test
```

校验内容包括文件数量、类别分布、标注状态、相关性范围、房源 ID 引用、区域质量标记和 SHA-256。

指标实现的单元测试：

```powershell
cd backend
.\mvnw.cmd -Dtest=RetrievalMetricsTest test
```

## 运行 MySQL 降级基线

为避免加载本地 AI、OSS 等密钥，基线使用隔离配置 `baseline-application.yml`。设置数据库环境变量后执行：

```powershell
cd backend
$env:SPRING_CONFIG_LOCATION = 'classpath:/baseline-application.yml'
$env:DB_URL = 'jdbc:mysql://localhost:3306/housing_rental_platform'
$env:DB_USERNAME = '...'
$env:DB_PASSWORD = '...'
.\mvnw.cmd -Dtest=Stage0CurrentBaselineIT test
```

临时产物写入 `target/evaluation/stage0-v1/`。只有数据集哈希和运行模式一致时，结果才可直接比较；确认后再固化到 `baselines/`，不要用一次新运行无审核地覆盖历史证据。

## 注意事项

- 当前房源均为虚构数据，因此本版本未执行脱敏；接入真实数据后必须更换导出规则。
- 当前 110 套房源只有 50 种不同描述，语义指标会受到重复文本影响。
- 通勤时间、详细家电等当前未建模字段不计入本版本的检索质量指标。
- 语义标签已按 V1/F2/L2/Z1 完成确认；后续修改规则必须创建新数据集版本或重新执行人工复核。
- 当前固化报告强制关闭向量检索，只代表 MySQL 降级路径；Chroma 正常路径仍需使用同一数据集补跑。
- 本机热数据库的毫秒级耗时只用于同环境回归比较，不能作为生产 SLA。
