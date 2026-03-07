# CHANGELOG

## 2026-03-07

### In progress
- Added request-scope query embedding reuse in backend retrieval so one normalized query vector can be shared across repeated intent-directed and vector-global retrieval branches during a single request.
- Added bounded and configurable vector-global fallback fan-out with `rag.search.channels.vector-global.max-collections` and `prefer-intent-collections`, keeping low-confidence intent collections prioritized before broader fallback expansion.
- Preserved KB retrieval provenance end to end by stamping source collection metadata on retrieved chunks, carrying it through deduplication and rerank, grouping intent chunks by actual source collection, and rendering compact `[来源: <collection>]` labels in KB prompt evidence.
- Added backend retrieval tests covering query-vector reuse, source-collection provenance stamping, provenance-aware deduplication, collection-based intent grouping, and prompt formatting of KB source labels.
- Verified the bootstrap backend module with targeted retrieval and prompt-path tests using `./mvnw -f /home/pejoy/code/ragent/.worktrees/optimization-implementation/pom.xml -pl bootstrap -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DefaultContextFormatterTests,DeduplicationPostProcessorTests,MilvusRetrieverServiceTests,MultiChannelRetrievalEngineTests,RetrievalEngineTests test`.
- Added route-level lazy loading for admin routes so dashboard, knowledge, intent, ingestion, traces, settings, sample question, and user screens load on demand behind router suspense fallbacks.
- Narrowed shell Zustand subscriptions in the chat header and sidebar so layout chrome listens only to the auth and chat slices it renders instead of subscribing to whole stores.
- Verified the frontend with lint, unit tests, coverage, production build, and Playwright e2e after the routing and shell subscription changes.
