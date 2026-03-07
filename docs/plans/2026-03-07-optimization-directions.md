# Optimization Directions Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Evolve Ragent toward a more scalable and maintainable architecture by first improving observability and system boundaries, then reducing backend hot-path structural costs, and finally consolidating frontend/admin architecture.

**Architecture:** Use a phased approach. First add platform observability and clearer environment/service boundaries so optimization work can be measured. Then refactor the backend request path around stage/pipeline boundaries and shared intermediate results, especially in retrieval and intent flows. Finally consolidate the frontend/admin architecture with route-level splitting, a shared server-state/query layer, and smaller feature modules.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, Redis/Redisson, Milvus, React 18, Vite, TypeScript, Zustand, Playwright, Vitest, Docker Compose, custom RAG trace infra.

---

### Task 1: Establish baseline observability and optimization metrics

**Files:**
- Modify: `bootstrap/pom.xml`
- Modify: `bootstrap/src/main/resources/application.yaml`
- Modify: `mcp-server/pom.xml`
- Inspect: `framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/**`
- Inspect: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/**`

**Step 1: Write a failing verification checklist**

Document the exact missing operator surfaces you are about to add:
- no stable readiness endpoint
- no standard JVM/app metrics export
- no shared correlation ID in platform logs

**Step 2: Add the minimum standard observability substrate**

Add Spring Boot Actuator and Micrometer/Prometheus support where appropriate.

**Step 3: Add health/readiness checks for core dependencies**

Implement explicit readiness for at least:
- MySQL
- Redis
- Milvus
- RustFS
- MCP server
- configured model providers (coarse probe only)

**Step 4: Add correlation between custom trace IDs and logs**

Ensure trace/request identifiers are available in MDC/log context so custom RAG tracing and platform logs can be correlated.

**Step 5: Run verification commands**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test
curl -i http://localhost:9090/actuator/health
curl -i http://localhost:9090/actuator/prometheus
```
Expected:
- tests pass
- health endpoint responds
- metrics endpoint responds

**Step 6: Commit**

```bash
git add bootstrap/pom.xml bootstrap/src/main/resources/application.yaml mcp-server/pom.xml
# plus any new health/metrics/logging files
git commit -m "feat: add observability and readiness foundation"
```

---

### Task 2: Externalize environment configuration and service topology

**Files:**
- Modify: `bootstrap/src/main/resources/application.yaml`
- Modify: `mcp-server/src/main/resources/application.yml`
- Possibly create: profile-specific config files under `bootstrap/src/main/resources/`
- Possibly create: env example docs or runtime config notes under `docs/`

**Step 1: Write the failing config portability check**

List the values currently hardcoded in committed config (DB/Redis/RustFS/model/MCP endpoints and credentials).

**Step 2: Move secrets and environment-specific topology to env-driven config**

Replace hardcoded credentials/endpoints with environment-variable driven defaults that are safe for local development but portable across environments.

**Step 3: Separate local-dev assumptions from deployment assumptions**

Use profiles or explicit environment overlays instead of one shared committed topology.

**Step 4: Verify local startup still works**

Run the current local backend startup path and verify the app still boots with explicit env vars or local defaults.

**Step 5: Commit**

```bash
git add bootstrap/src/main/resources/application*.y*ml mcp-server/src/main/resources/application*.y*ml docs/
git commit -m "refactor: externalize runtime configuration"
```

---

### Task 3: Align trace lifecycle with real streaming lifecycle

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/ChatRateLimitAspect.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/RagTraceAspect.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamTaskManager.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RagTraceRecordServiceImpl.java`
- Test: `bootstrap/src/test/java/**`

**Step 1: Write a failing test or verification around stream completion semantics**

Model a streaming request whose method returns before the SSE stream actually completes.

**Step 2: Run the verification and confirm current behavior is wrong/incomplete**

Expected: trace run finishes too early or misses stream completion status.

**Step 3: Implement minimal lifecycle alignment**

Move success/failure/final-duration handling to the actual stream completion/cancel/error boundary.

**Step 4: Re-run targeted tests**

Expected: trace end-state reflects real stream completion.

**Step 5: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/ChatRateLimitAspect.java bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamTaskManager.java
# plus related trace files/tests
git commit -m "fix: align rag trace lifecycle with streaming completion"
```

---

### Task 4: Introduce request-scope query embedding reuse

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusRetrieverService.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/**`
- Possibly create: a request-scope retrieval context/value object under `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/**`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/**`

**Step 1: Write a failing test showing duplicate embedding work**

Mock the embedding service and assert a fan-out retrieval request calls embedding more than once today.

**Step 2: Run the test to confirm failure**

Expected: repeated calls for the same query.

**Step 3: Implement shared query-vector reuse in the retrieval request scope**

Keep it local to one request first; do not jump to cross-request caching yet.

**Step 4: Re-run the test**

Expected: embedding happens once per query/request.

**Step 5: Run the backend test suite**

```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap clean test
```
Expected: PASS.

**Step 6: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve
git commit -m "perf: reuse query embeddings across retrieval fan-out"
```

---

### Task 5: Bound global fallback search fan-out

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/VectorGlobalSearchChannel.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/SearchChannelProperties.java`
- Possibly modify: retrieval selection logic under `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/**`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/**`

**Step 1: Write a failing test for unbounded fallback behavior**

Construct a case with many collections and verify current fallback tries all of them.

**Step 2: Confirm failure**

Expected: all collections are searched.

**Step 3: Implement bounded fallback policy**

Examples of allowed strategies:
- cap collection count
- scope by intent/domain/tenant
- adaptive fallback tiers

Keep it simple and explicit.

**Step 4: Re-run targeted tests**

Expected: bounded behavior is enforced.

**Step 5: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/VectorGlobalSearchChannel.java bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/SearchChannelProperties.java
# plus tests
git commit -m "perf: bound global retrieval fallback fan-out"
```

---

### Task 6: Preserve retrieval provenance through post-processing and prompt assembly

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/dto/**`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MultiChannelRetrievalEngine.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/RAGPromptService.java`
- Modify: trace-related persistence/VOs if needed
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/**`

**Step 1: Write a failing test showing provenance loss**

Demonstrate that after retrieval/post-processing/prompt assembly, origin metadata is missing or flattened away.

**Step 2: Confirm failure**

Expected: prompt context cannot distinguish source provenance.

**Step 3: Add minimal provenance fields to retrieval results/context**

Track at least:
- channel
- collection/knowledge base
- related intent/sub-question
- post-processor lineage if practical

**Step 4: Pass provenance through prompt assembly and trace**

Make it inspectable, not just internal.

**Step 5: Re-run targeted tests**

Expected: provenance survives the pipeline.

**Step 6: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/dto bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt
# plus tests
git commit -m "refactor: preserve retrieval provenance across rag pipeline"
```

---

### Task 7: Refactor retrieval orchestration into explicit stages

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java`
- Create/Modify: stage classes under `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/**`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/**`

**Step 1: Write a failing architectural characterization test**

Capture the current behavior of mixed KB retrieval + MCP execution + context merge so refactoring preserves behavior.

**Step 2: Run the test and confirm it protects current semantics**

**Step 3: Extract explicit stages**

Target shape:
- kb retrieval stage
- mcp/tool stage
- context merge/format stage

Keep behavior the same; this is structural work, not feature expansion.

**Step 4: Re-run characterization tests and backend suite**

Expected: no behavioral regression.

**Step 5: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve
git commit -m "refactor: split retrieval engine into explicit stages"
```

---

### Task 8: Reduce intent-classification scaling costs

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/DefaultIntentClassifier.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentTreeCacheManager.java`
- Possibly create: staged/prompt-fragment helper classes under `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/**`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/intent/**`

**Step 1: Write a failing test for repeated tree rebuild or oversized prompt generation**

**Step 2: Confirm failure or inefficiency signal**

**Step 3: Implement one bounded optimization**

Pick one for the first pass:
- cached flattened tree fragments
- staged classification over smaller candidate sets
- reusable prompt fragments

Do not attempt all strategies at once.

**Step 4: Re-run tests**

Expected: same classification semantics, lower internal work.

**Step 5: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/intent
git commit -m "perf: reduce intent classification rebuild cost"
```

---

### Task 9: Introduce deterministic MCP parameter extraction fast path

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/LLMMCPParameterExtractor.java`
- Modify: MCP registry/client abstractions under `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/**`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/mcp/**`

**Step 1: Write a failing test for a simple structured tool schema**

Show that a simple tool still goes through LLM extraction today.

**Step 2: Confirm failure**

**Step 3: Add a deterministic extraction path for simple/typed schemas**

Keep LLM extraction as fallback only.

**Step 4: Re-run targeted tests**

Expected: simple schemas skip the LLM path.

**Step 5: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/mcp
git commit -m "perf: add deterministic mcp parameter extraction fast path"
```

---

### Task 10: Add admin route-level lazy loading

**Files:**
- Modify: `frontend/src/router.tsx`
- Possibly create: route module wrappers under `frontend/src/pages/**`
- Test: `frontend/src/**/*.test.tsx`

**Step 1: Write a failing characterization test or bundle expectation note**

Document which route groups are currently eagerly loaded.

**Step 2: Introduce React.lazy/Suspense for admin-heavy routes**

Keep public/chat routes straightforward; focus on large admin sections first.

**Step 3: Re-run frontend tests and build**

Run:
```bash
npm --prefix "/home/pejoy/code/ragent/frontend" run test
npm --prefix "/home/pejoy/code/ragent/frontend" run build
```
Expected: PASS.

**Step 4: Commit**

```bash
git add frontend/src/router.tsx frontend/src/pages
git commit -m "perf: lazy load admin route modules"
```

---

### Task 11: Introduce a shared server-state/query layer for admin pages

**Files:**
- Modify: `frontend/package.json`
- Create/Modify: `frontend/src/services/**`, `frontend/src/hooks/**`, or query-layer files
- Modify: admin pages under `frontend/src/pages/admin/**`
- Test: `frontend/src/**/*.test.tsx`

**Step 1: Pick the smallest viable shared query layer**

Examples:
- TanStack Query
- a lightweight custom query abstraction

Choose one and keep scope tight.

**Step 2: Write one failing test around duplicated fetch/loading behavior**

Target one representative admin page first.

**Step 3: Implement the shared query layer for one page family**

Start with one cluster (e.g. knowledge pages or traces) before broad rollout.

**Step 4: Re-run tests and verify behavior**

**Step 5: Expand to the next admin page cluster only after the first cluster is stable**

**Step 6: Commit**

```bash
git add frontend/package.json frontend/src/services frontend/src/hooks frontend/src/pages/admin
# plus tests
git commit -m "refactor: introduce shared admin server-state layer"
```

---

### Task 12: Break giant admin pages into feature modules and hooks

**Files:**
- Modify: large pages such as:
  - `frontend/src/pages/admin/dashboard/DashboardPage.tsx`
  - `frontend/src/pages/admin/ingestion/IngestionPage.tsx`
  - `frontend/src/pages/admin/knowledge/KnowledgeDocumentsPage.tsx`
- Create: supporting feature folders/components/hooks under `frontend/src/pages/admin/**`
- Test: `frontend/src/**/*.test.tsx`

**Step 1: Write characterization tests for one page before splitting**

**Step 2: Extract presentational sections and async hooks/view-model logic**

Split one giant page at a time.

**Step 3: Re-run tests after each extraction**

Expected: no behavior regressions.

**Step 4: Commit after each page split**

Example:
```bash
git add frontend/src/pages/admin/dashboard
git commit -m "refactor: split dashboard page into feature modules"
```

Repeat for ingestion and knowledge documents only if still warranted.

---

### Task 13: Narrow Zustand subscriptions and reduce shell rerenders

**Files:**
- Modify: `frontend/src/components/layout/Header.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/pages/admin/AdminLayout.tsx`
- Modify: `frontend/src/stores/chatStore.ts`
- Test: `frontend/src/**/*.test.tsx`

**Step 1: Write a failing characterization test or profiling note**

Identify components subscribing to the whole chat store.

**Step 2: Replace broad subscriptions with selectors**

Only subscribe to the state each component actually uses.

**Step 3: Re-run frontend tests**

Expected: PASS.

**Step 4: Commit**

```bash
git add frontend/src/components/layout/Header.tsx frontend/src/components/layout/Sidebar.tsx frontend/src/pages/admin/AdminLayout.tsx frontend/src/stores/chatStore.ts
git commit -m "perf: narrow frontend store subscriptions"
```

---

### Task 14: Move UI side effects out of API client

**Files:**
- Modify: `frontend/src/services/api.ts`
- Modify: auth/bootstrap flow files such as `frontend/src/main.tsx`, router/auth hooks, or page-level handlers
- Test: `frontend/src/**/*.test.tsx`

**Step 1: Write a failing test or behavior characterization around auth-expiry handling**

**Step 2: Remove toast/redirect side effects from the low-level API client**

Return typed errors/events upward instead.

**Step 3: Handle navigation/notification at the app or page boundary**

**Step 4: Re-run tests**

Expected: PASS.

**Step 5: Commit**

```bash
git add frontend/src/services/api.ts frontend/src/main.tsx frontend/src/router.tsx
# plus related handlers/tests
git commit -m "refactor: move api side effects to app layer"
```

---

### Task 15: Add an opt-in real-backend/model smoke lane

**Files:**
- Modify: `frontend/package.json`
- Modify: Playwright config/scripts under `frontend/`
- Possibly add: backend readiness endpoint/controller if not yet present
- Test: `frontend/e2e/**`

**Step 1: Keep mocked E2E as the default stable path**

Do not replace the deterministic suite.

**Step 2: Add a separate smoke command**

Examples:
```json
"test:e2e:smoke": "..."
```

**Step 3: Define one minimal live flow**

Example:
- login
- send one message
- assert non-empty assistant response or explicit failure state

**Step 4: Re-run both lanes**

Expected:
- default mocked E2E still passes reliably
- smoke lane is opt-in and documented as such

**Step 5: Commit**

```bash
git add frontend/package.json frontend/playwright.config.ts frontend/scripts frontend/e2e
git commit -m "test: add opt-in live smoke e2e lane"
```

---

### Task 16: Re-measure, compare, and document the post-optimization state

**Files:**
- Coverage and report outputs from backend/frontend
- Docs/plans and any architecture notes produced during implementation

**Step 1: Re-run the canonical verification set**

Backend:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap clean test
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test jacoco:report
```

Frontend:
```bash
npm --prefix "/home/pejoy/code/ragent/frontend" run lint
npm --prefix "/home/pejoy/code/ragent/frontend" run test
npm --prefix "/home/pejoy/code/ragent/frontend" run coverage
npm --prefix "/home/pejoy/code/ragent/frontend" run test:e2e
```

**Step 2: Compare against baseline assumptions**

Measure:
- default test stability
- key class coverage
- frontend target coverage
- E2E pass rate
- observability/readiness surfaces now available

**Step 3: Document residual gaps explicitly**

Examples:
- smoke paths still opt-in only
- broad module coverage still lower than target
- route-level lazy loading not yet rolled out across every admin section

**Step 4: Commit final documentation updates**

```bash
git add docs/plans
git commit -m "docs: record optimization implementation outcomes"
```
