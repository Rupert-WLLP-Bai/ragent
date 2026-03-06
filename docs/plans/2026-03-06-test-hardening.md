# Test Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Repair the repository test/lint infrastructure and raise automated verification to a baseline acceptable standard with backend core coverage at 60%+, frontend key-page/state coverage at 40%+, and 2-3 passing end-to-end user journeys.

**Architecture:** Keep the automated suite deterministic by making unit tests the main source of coverage and using mocks/fakes for model providers, MCP, and vector-store boundaries. Add a thin layer of integration tests for module collaboration and a very small number of smoke/E2E checks that can exercise the locally running app stack without depending on brittle exact LLM text.

**Tech Stack:** Maven, Spring Boot 3.5, JUnit 5, Mockito, Surefire, JaCoCo (to be added), React 18, Vite, Vitest, Testing Library, Playwright, ESLint.

---

### Task 1: Repair backend test runner configuration

**Files:**
- Modify: `pom.xml`
- Verify: `bootstrap/pom.xml`

**Step 1: Write the failing reproduction command**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test
```
Expected: FAIL with Surefire fork startup error referencing the literal `@{argLine}` token.

**Step 2: Update the Surefire configuration minimally**

Adjust `pom.xml` so the Surefire `argLine` does not pass a literal unresolved `@{argLine}` token into the JVM. Keep the Mockito javaagent behavior only if it is truly required for the existing tests.

**Step 3: Re-run the reproduction command**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test
```
Expected: The runner starts tests instead of crashing during fork startup.

**Step 4: Confirm no unintended build regressions**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap -am test -DskipTests
```
Expected: Build wiring remains valid.

---

### Task 2: Diagnose and stabilize the existing backend tests

**Files:**
- Modify: existing files under `bootstrap/src/test/java/**` only as needed
- Inspect: `bootstrap/src/test/java/com/nageoffer/ai/ragent/**`

**Step 1: Run the full existing backend test suite and capture exact failures**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test
```
Expected: A concrete list of failing tests and failure causes.

**Step 2: Classify each failing test**

Create a simple mapping of each existing test into one of these categories:
- deterministic unit/integration test that should remain automated
- environment-dependent smoke test
- obsolete/brittle test needing rewrite

**Step 3: Convert brittle tests away from uncontrolled external dependencies**

For tests that call real external model providers or assume specific seed data, replace brittle assertions with deterministic setup and assertions, or move them into an explicit smoke-test bucket if they truly need live dependencies.

**Step 4: Re-run backend tests**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test
```
Expected: Existing test suite becomes stable enough to use as a foundation.

---

### Task 3: Add backend coverage reporting

**Files:**
- Modify: `pom.xml`

**Step 1: Add JaCoCo plugin configuration**

Add JaCoCo with report generation at test time and a clear report output location.

**Step 2: Run tests with coverage**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test jacoco:report
```
Expected: JaCoCo XML/HTML reports are generated.

**Step 3: Record the baseline backend coverage**

Capture the starting coverage numbers for the core module areas you intend to raise.

---

### Task 4: Identify backend core modules to target for 60%+

**Files:**
- Inspect only, then document in plan notes / execution notes
- Likely focus areas under:
  - `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/**`
  - `bootstrap/src/main/java/com/nageoffer/ai/ragent/service/**`
  - `infra-ai/src/main/java/**` if needed for logic-heavy provider wrappers

**Step 1: Map the most testable core logic**

Prioritize areas with deterministic business logic such as:
- intent classification orchestration
- query rewrite orchestration
- conversation/message service logic
- retrieval post-processing or pipeline logic

**Step 2: Exclude obviously poor coverage targets for now**

Do not chase coverage in thin configuration classes, trivial DTOs, or highly framework-driven bootstrapping unless required.

**Step 3: Write down the specific classes selected for coverage work**

This selection should be explicit before adding many tests.

---

### Task 5: Add backend unit tests around core deterministic logic

**Files:**
- Create/Modify tests under: `bootstrap/src/test/java/...`
- Modify production files only when necessary to improve testability with minimal surface-area changes

**Step 1: For each chosen class, write one failing test first**

Use JUnit 5 + Mockito. Prefer small, deterministic tests that isolate one behavior each.

**Step 2: Run the single new test and verify it fails**

Example pattern:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap -Dtest=ClassNameTests#methodName test
```
Expected: FAIL for the intended missing or incorrect behavior.

**Step 3: Implement the minimal code or seam needed**

Prefer constructor injection, narrow mocks, and no unrelated refactors.

**Step 4: Re-run the single test, then the class test file**

Expected: PASS.

**Step 5: Repeat until the targeted core modules approach the coverage goal**

Keep each test addition focused and small.

---

### Task 6: Add backend integration tests for key collaborations

**Files:**
- Create/Modify tests under: `bootstrap/src/test/java/**`
- Possibly create test-only configuration helpers under `bootstrap/src/test/java/**`

**Step 1: Pick 2-4 high-value collaboration points**

Examples:
- service + repository + seed/test data flow
- MCP registry + client registration behavior with stubbed responses
- query rewrite / intent flow with mocked provider edges

**Step 2: Write failing integration tests with controlled setup**

Use test data you explicitly insert or prepare in test lifecycle hooks. Avoid relying on incidental local state.

**Step 3: Run only those tests**

Expected: FAIL for a clear reason before implementation/adjustment.

**Step 4: Make minimal changes to stabilize the integration path**

Use local test doubles or explicit fixtures rather than live internet services.

**Step 5: Re-run and confirm pass**

---

### Task 7: Repair frontend lint compatibility

**Files:**
- Modify: `frontend/.eslintrc.cjs`
- Verify versions in: `frontend/package.json`

**Step 1: Reproduce the current lint failure**

Run:
```bash
cd "/home/pejoy/code/ragent/frontend" && npm run lint
```
Expected: FAIL with `plugin:react-refresh/recommended` config incompatibility.

**Step 2: Adjust ESLint config or plugin versions minimally**

Choose the smallest fix that makes the repository’s current lint command valid with its installed ESLint major version.

**Step 3: Re-run lint**

Run:
```bash
cd "/home/pejoy/code/ragent/frontend" && npm run lint
```
Expected: Command runs successfully, or surfaces actual source lint issues instead of config-schema failure.

**Step 4: Fix only real source lint violations that block the command**

Do not perform broad style churn.

---

### Task 8: Establish frontend unit/integration test tooling

**Files:**
- Modify: `frontend/package.json`
- Create/Modify likely config files such as:
  - `frontend/vitest.config.ts` or `frontend/vite.config.ts`
  - `frontend/src/test/setup.ts`
- Create tests under: `frontend/src/**`

**Step 1: Add a frontend test runner and coverage stack**

Use Vitest + Testing Library + jsdom and coverage reporting.

**Step 2: Add scripts**

Add explicit scripts such as:
```json
"test": "vitest run",
"test:watch": "vitest",
"coverage": "vitest run --coverage"
```

**Step 3: Create a trivial failing smoke test for the frontend harness**

Example: render a known component and assert expected text.

**Step 4: Run the single smoke test**

Expected: FAIL before setup is complete, then PASS after setup is correct.

**Step 5: Run the whole frontend test command**

Expected: Harness is operational.

---

### Task 9: Add frontend tests for key pages and state

**Files:**
- Create tests under the actual feature paths in `frontend/src/**`
- Inspect likely state/store files and page components before selecting targets

**Step 1: Identify key frontend targets**

Prefer the most business-relevant and deterministic areas such as:
- login/auth flow state
- sidebar/admin navigation state
- chat input / message rendering / request state
- knowledge-base list/detail actions if present

**Step 2: For each target, write one failing test first**

Use Testing Library with user-oriented assertions.

**Step 3: Run the test and verify it fails**

Expected: FAIL for the intended reason.

**Step 4: Make the minimal fix or add the minimal harness/mock needed**

Do not rewrite component architecture unless unavoidable.

**Step 5: Re-run tests and build coverage**

Run:
```bash
cd "/home/pejoy/code/ragent/frontend" && npm run coverage
```
Expected: Frontend key pages/state trends toward 40%+ coverage.

---

### Task 10: Add Playwright end-to-end coverage for critical flows

**Files:**
- Create/Modify Playwright config and tests under `frontend/` or project-root E2E directory depending on existing conventions

**Step 1: Add Playwright setup**

Create the minimum configuration needed to run against the locally started frontend and backend.

**Step 2: Write Flow 1 failing test**

Flow 1: login.

**Step 3: Run Flow 1 and make it pass**

Prefer robust selectors and minimal brittle waits.

**Step 4: Write Flow 2 failing test**

Flow 2: enter main UI / admin area.

**Step 5: Run Flow 2 and make it pass**

**Step 6: Write Flow 3 failing test**

Flow 3: complete one key chat or knowledge-base workflow.

**Step 7: Run Flow 3 and make it pass**

For LLM-linked behavior, assert structural success (request completes, response area updates, no fatal error) rather than exact wording.

---

### Task 11: Add optional local-model smoke coverage

**Files:**
- Create dedicated smoke tests under backend or E2E test directories
- Add opt-in scripts in package/build tooling as needed

**Step 1: Make these tests opt-in, not default**

Name them clearly, such as `test:smoke:ollama` or a Maven profile.

**Step 2: Target only a very small number of scenarios**

Use local Ollama (`llama3.1:8b`) for one or two smoke paths if it helps validate the AI pipeline.

**Step 3: Keep assertions coarse and stable**

Assert that a non-empty answer is returned and no server error occurs.

---

### Task 12: Measure coverage, close the biggest gaps, and verify the whole suite

**Files:**
- Coverage outputs and any modified tests/config from earlier tasks

**Step 1: Run backend coverage**

Run:
```bash
mvn -f "/home/pejoy/code/ragent/pom.xml" -pl bootstrap test jacoco:report
```

**Step 2: Run frontend coverage**

Run:
```bash
cd "/home/pejoy/code/ragent/frontend" && npm run coverage
```

**Step 3: Run frontend lint**

Run:
```bash
cd "/home/pejoy/code/ragent/frontend" && npm run lint
```

**Step 4: Run E2E suite**

Run the Playwright command you added.

**Step 5: Compare against acceptance criteria**

Confirm:
- backend core modules 60%+
- frontend key pages/state 40%+
- 2-3 E2E flows passing
- default automated test commands stable

**Step 6: Document any remaining explicit gaps**

If any coverage target is still short, identify the exact remaining weak spots instead of hand-waving.
