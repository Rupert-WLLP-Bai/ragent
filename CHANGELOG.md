# CHANGELOG

## 2026-03-08

### In progress
- Documented a verified local startup playbook in `README.md`, including required service startup order, concrete local credentials used in the current environment, backend/frontend/MCP startup commands, health/readiness checks, and the exact Redis/MySQL/RustFS/Milvus/MCP pitfalls encountered during bring-up.

## 2026-03-07

### In progress
- Added request-scope query embedding reuse in backend retrieval so one normalized query vector can be shared across repeated intent-directed and vector-global retrieval branches during a single request.
- Added bounded and configurable vector-global fallback fan-out with `rag.search.channels.vector-global.max-collections` and `prefer-intent-collections`, keeping low-confidence intent collections prioritized before broader fallback expansion.
- Preserved KB retrieval provenance end to end by stamping source collection metadata on retrieved chunks, carrying it through rerank, keeping a canonical collection during deduplication, recording merged collection origins separately, grouping intent chunks by actual source collection, and rendering compact `[来源: <collection>]` labels in KB prompt evidence.
- Added backend retrieval tests covering query-vector reuse, source-collection provenance stamping, provenance-aware deduplication, collection-based intent grouping, and prompt formatting of KB source labels.
- Verified the bootstrap backend module with targeted retrieval and prompt-path tests using `./mvnw -f /home/pejoy/code/ragent/.worktrees/optimization-implementation/pom.xml -pl bootstrap -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DefaultContextFormatterTests,DeduplicationPostProcessorTests,MilvusRetrieverServiceTests,MultiChannelRetrievalEngineTests,RetrievalEngineTests test`.
- Aligned stream trace completion with the real SSE lifecycle by deferring `finishRun` until emitter completion, timeout, or error instead of closing the trace when the stream method returns.
- Added aspect tests covering deferred trace success on SSE completion and error finalization on SSE failure callbacks.
- Split `RetrievalEngine` one step further toward explicit stages by separating per-subquestion KB retrieval, MCP execution, and final context merge helpers without broad request-path churn.
- Added retrieval tests covering the explicit merge stage so multi-subquestion KB context composition remains stable through the refactor.
- Fixed `RetrievalEngine` multi-subquestion intent chunk merging so same-intent results append instead of overwrite across subquestions, while duplicate chunks still merge and retain provenance.
- Added focused retrieval tests covering cross-subquestion per-intent append behavior and provenance-preserving duplicate chunk merge.
- Added route-level lazy loading for admin routes so dashboard, knowledge, intent, ingestion, traces, settings, sample question, and user screens load on demand behind router suspense fallbacks.
- Narrowed shell Zustand subscriptions in the chat header and sidebar so layout chrome listens only to the auth and chat slices it renders instead of subscribing to whole stores.
- Moved low-level frontend API auth side effects upward by replacing axios interceptor toast/redirect logic with an unauthorized callback, handling session expiry in auth store state, and triggering login/logout toasts plus navigation at app/page boundaries.
- Split DashboardPage's right-rail AI performance and operational insight widgets into a dedicated `DashboardSidebar` component so the admin dashboard page keeps data orchestration while the sidebar presentation lives in a focused module.
- Added a deterministic typed fast path for simple MCP parameter schemas so obvious string/boolean/number/enum inputs can bypass LLM extraction, while ambiguous or incomplete cases still fall back to the existing LLM path.
- Added focused MCP extraction tests covering simple typed fast-path hits, enum extraction, backward-compatible LLM fallback for incomplete required parameters, and the safety fallback to LLM when multiple numeric parameters would be ambiguous.
- Added snapshot reuse inside `DefaultIntentClassifier` so unchanged intent trees can reuse flattened nodes, leaf lists, ID maps, and rendered prompts while still re-reading the latest tree roots on each classification call.
- Added focused intent-classifier tests covering lazy prompt rendering for node lookups, prompt reuse when the tree snapshot is unchanged, and prompt rebuild when the tree snapshot changes.
- Added a shared `AdminPageShell` with reusable list-state and pagination helpers, applied it to `UserListPage` and `SampleQuestionPage`, and lightly aligned `SystemSettingsPage` loading/empty presentation with the same admin shell.
- Verified the frontend with lint, unit tests, coverage, production build, and Playwright e2e after the routing, shell subscription, API side-effect boundary, dashboard structure, and admin shell convergence changes.
- Verified the backend intent-classifier optimization with `"/home/pejoy/code/ragent/.worktrees/optimization-implementation/mvnw" -f "/home/pejoy/code/ragent/.worktrees/optimization-implementation/pom.xml" -pl bootstrap -am -Dtest=DefaultIntentClassifierTests -Dsurefire.failIfNoSpecifiedTests=false test`.
