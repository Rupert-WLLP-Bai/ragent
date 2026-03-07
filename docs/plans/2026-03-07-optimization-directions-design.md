# Ragent Optimization Directions Design

## Goal
为 Ragent 制定一份偏中长期的系统级优化地图，重点关注性能优化与架构优化，而不是局部微调。

## Scope
本设计聚焦四个层面：
1. 请求主链路与核心编排
2. 检索与模型性能
3. 前端与管理台架构
4. 运维与可观测性

不在本轮设计中直接进入代码实现，也不把真实模型 smoke、部署平台重构、全量模块重写作为默认前提。

## Current Assessment
Ragent 的基础并不薄弱：
- 模块分层清晰，`framework / infra-ai / bootstrap` 有明确职责边界
- RAG 主链路完整，具备 rewrite、intent、retrieval、MCP、memory、trace、model routing 等完整能力
- 前端具备 chat + admin 全链路能力
- 后端默认测试、前端测试、mocked E2E 已有自动化基线

当前真正的问题不是“缺少架构”，而是系统已经进入“能力累积后的结构膨胀期”：
- 少数 orchestrator 正在承担越来越多职责
- 检索与模型链路存在重复工作和无限 fan-out 风险
- 前端 admin 页面和 chat store 出现单体化趋势
- 平台级 observability 与 deployment readiness 落后于业务能力本身

## Optimization Thesis
推荐把系统从“能力叠加型单体编排”演进为：
- 中间态可追踪（provenance-preserving）
- 检索成本可控（bounded fan-out + shared intermediate results）
- 主链路阶段化（stage/pipeline-oriented orchestration）
- 平台可观测（standard readiness/metrics/log correlation）
- 前后端结构可持续演进（feature/module-oriented frontend + narrower backend orchestrators）

## System Map

### A. Request Path and Orchestration
当前优势：
- 主链路清晰：memory → rewrite → intent → retrieval → prompt → model routing → streaming
- `RAGChatServiceImpl` 可作为核心编排入口理解全链路
- retrieval / postprocessor / model routing 都已有清晰抽象

主要风险：
- `RAGChatServiceImpl` 逐渐成为高 blast-radius 的超级编排器
- `RetrievalEngine` 同时承担 KB retrieval、MCP execution、context merge，职责过重
- rewrite → intent → retrieval → prompt → trace 之间的 provenance 丢失，影响 explainability、rerank 与效果分析
- trace 生命周期与真实 SSE 生命周期不完全一致，影响用户视角性能判断

优化方向：
- 把核心链路拆成显式 stage pipeline
- 给中间态保留来源信息（channel / intent / collection / postprocessor provenance）
- 让 `RAGChatServiceImpl` 更偏调度器而不是细节汇聚点
- 让 trace completion 对齐真实 streaming lifecycle

### B. Retrieval and Model Performance
高优先级问题：
- 同一 query 在多 retrieval branch 中重复做 embedding
- 低置信度时全局检索 fallback 搜索所有 collection，fan-out 无边界
- rerank 前候选集膨胀过大
- 首 token 前控制面模型调用过多（rewrite / intent / rerank / MCP 参数提取）

次级问题：
- intent tree 每次请求仍有较重的重建成本
- 线程池背压策略会把负载打回请求线程
- 入库 embedding 路径策略不一致
- Ollama batch 接口未真正批量化

优化方向：
- 对 query embedding 做 request-scope 复用
- 对 global fallback 加边界与分层策略
- 提前剪枝，减少 rerank 输入规模
- 按场景设计高质量链路与低成本链路
- 对 intent classification 做 staged classification / prompt fragment caching

### C. Frontend and Admin Architecture
当前优势：
- chat 与 admin 路由结构清晰
- Zustand 使用务实
- 已有测试与 mocked E2E 基线

主要风险：
- admin route 缺少按路由拆包
- admin 页面缺少统一 server-state/query 层
- 多个页面已成为大体量单体页面
- `chatStore` 责任过重
- Header / Sidebar 等壳层组件 store 订阅粒度偏粗
- API client 混入 toast / redirect 等 UI 副作用

优化方向：
- route-level lazy loading
- 为 admin 引入统一 server-state/query 层
- giant pages 拆成 feature module + hooks/view-model
- chatStore 按职责拆层
- store 访问改成 selector 粒度
- 把 UI 副作用从 API client 上提到应用层

### D. Operations and Observability
当前优势：
- 自定义 RAG trace 很强
- 异步上下文透传与线程池拆分有生产意识
- queueing / streaming / Redis coordination / model failover 都有明显工程化设计

主要缺口：
- 缺少 Actuator / Micrometer / Prometheus / OTel 等标准 operability substrate
- 缺少标准 readiness / liveness / downstream dependency health surface
- 自定义 trace 强，但 HTTP/JVM/DB/Redis/provider 维度 observability 弱
- 默认配置中包含固定 credentials 与 localhost 拓扑，环境外置不足
- MCP server 更像本地组件，不像生产级 service boundary
- 部署拓扑不完整，repo 内缺少一体化 app deployment artifacts

优化方向：
- 补标准健康检查、metrics、log correlation
- 把自定义 trace 与平台级 observability 桥接
- 配置外置化与 profile/secret 治理
- 完整化部署/启动拓扑
- 增加 SLO-oriented 指标：queue depth、first-token latency、model failover count、provider latency/error rate 等

## Recommended Roadmap
推荐采用三阶段路线：

### Phase A: Observability and Boundaries First
目标：把系统“看清楚”并建立可靠的优化测量基础。

包含：
- readiness / liveness / dependency health
- metrics / log correlation / trace bridge
- 配置外置化
- 明确 default tests、mocked E2E、opt-in smoke 的验证边界

### Phase B: Core Path Restructuring
目标：控制主链路复杂度，保留中间态语义，减少结构性性能浪费。

包含：
- stage 化 `RAGChatServiceImpl`
- pipeline 化 `RetrievalEngine`
- 保留 provenance
- intent classification staged 化
- query embedding 复用
- bounded fallback 与 rerank 前剪枝

### Phase C: Frontend/Admin Architecture Consolidation
目标：防止前端后台进一步单体化，提升可维护性和可演进性。

包含：
- admin route lazy loading
- shared server-state/query 层
- giant pages 拆分
- chat store 拆层
- UI side effects 上提

## Recommendation
推荐路线为：
**先补观测与边界，再重构主链路与检索，再收敛前端架构。**

原因：
- 这条路线最符合“系统性架构审视”的目标
- 能同时兼顾性能收益与结构可持续性
- 可以避免在缺少测量手段的情况下盲目做性能重构

## Acceptance for This Design
本设计产出应作为后续实施计划的基础，重点回答：
- 先做哪些结构性工作，才能让后续优化有可验证收益
- 哪些瓶颈属于 request-scope 重复工作，哪些属于系统边界问题
- 哪些优化应该做成默认路径，哪些应该作为 opt-in 路径
