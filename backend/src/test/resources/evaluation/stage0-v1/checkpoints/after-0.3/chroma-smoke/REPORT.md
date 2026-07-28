# 本地 BGE + Chroma 端到端冒烟报告

> 数据集：`stage0-v1`  
> 运行模式：`REAL_LOCAL_BGE_CHROMA_SMOKE`  
> Collection：`SpringAiTenant/SpringAiDatabase/housing-rag`  
> 索引文档：447 条（110 套房源 × 4 个分片 + 7 条 FAQ）

## 验证目标

本报告只验证真实链路 `用户文本 -> 本地 BGE Embedding -> Spring AI -> Chroma -> 房源 ID`。它不使用 MySQL 降级结果，也不重建或写入 Chroma Collection。

## 结果

| 指标 | 结果 |
|---|---:|
| 语义查询数 | 10 |
| `SUCCESS_WITH_RESULTS` | 10 |
| Top10 至少命中一套人工相关房源 | 10 |
| Top10 查询命中率 | 100% |
| MRR | 0.9000 |
| 平均 Recall@10 | 0.4421 |

10 条查询分别覆盖近地铁、家电齐全、安静舒适、海景、家庭居住、年轻人、学区房、装修品质、宠物友好和短租。

平均 Recall@10 不能单独解释为链路质量不足：部分规则组标注了 30 至 50 套相关房源，而单次最多只返回 10 套，指标存在天然上限。本轮的主要退出标准是所有查询成功经过真实向量链路、Top10 查询命中率不低于 80%，实际结果为 100%。

## 发现并修复的问题

1. `.env` 原先使用 `file:.runtime/...`，但 Maven/Surefire 的 JVM 工作目录是 `backend`，导致 tokenizer 路径不存在。现改为 `file:../.runtime/...`。
2. 量化 ONNX 的实际权重位于 `model_quantized.onnx_data`。Spring AI 从字节加载模型后，ONNX Runtime 会从进程工作目录解析该文件；安装脚本原先只复制到项目根目录。现同时复制到项目根目录和 `backend`。
3. Docker 不使用本地相对路径，`docker-compose.yml` 已显式覆盖为 `/app/.runtime/...`。
4. Chroma 默认租户为空不代表索引缺失。Spring AI 实际 Collection 位于 `SpringAiTenant/SpringAiDatabase`。

## 边界

- 本报告是 10 条查询的端到端冒烟验收，不是最终推荐系统质量报告。
- 当前房源字段仍然贫乏，尚不能评价通勤、详细设施、费用承担和起租时间等改造后能力。
- 逐查询房源 ID、分数、首个相关排名和 Recall@10 位于同目录 `report.json`。
- 测试前后 Collection 均为 447 条，确认本轮没有改写向量索引。
