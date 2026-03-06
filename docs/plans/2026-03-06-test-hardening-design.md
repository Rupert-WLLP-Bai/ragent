# Test Hardening Design

## Goal
Raise the repository to a baseline acceptable testing standard:
- Backend core-module coverage: 60%+
- Frontend key pages/state coverage: 40%+
- Add essential unit, integration, and 2-3 end-to-end user journeys

## Current Findings
- Backend has a small number of Spring Boot tests, many of which depend on real infrastructure or external model behavior.
- Frontend has no project-owned automated tests and no `test` script.
- Existing backend test execution is blocked by Maven Surefire configuration issues.
- Existing frontend quality checks are blocked by ESLint/plugin compatibility issues.
- The project can run locally with MariaDB, Redis, Milvus, RustFS, MCP server, and the frontend/backend apps.

## Testing Strategy

### 1. Unit Tests
- Primary source of stable coverage
- No dependency on external LLMs
- Mock or fake external dependencies such as model providers, MCP, vector storage, and persistence boundaries where appropriate

### 2. Integration Tests
- Validate collaboration between application components
- Prefer deterministic local dependencies or mocks/fakes
- Keep a small number of real local dependency smoke tests where useful

### 3. End-to-End Tests
- Cover 2-3 critical user journeys:
  - login
  - enter core UI / admin area
  - complete a key chat or knowledge-base workflow
- Assertions should focus on flow success and expected structure, not brittle exact LLM wording

### 4. Real Model Usage
- Do not make external or local LLMs the basis of the whole automated suite
- Local Ollama models may be used for limited smoke/integration validation only
- External MiniMax integration, if added later, should be optional and reserved for a very small number of explicit smoke/E2E checks

## Execution Order
1. Repair backend test execution infrastructure
2. Repair frontend lint/test infrastructure
3. Establish frontend test framework and scripts
4. Expand backend unit and integration tests around core modules
5. Expand frontend tests around key pages and state flows
6. Add E2E coverage for critical user journeys
7. Measure coverage, identify weak spots, and close the largest gaps

## Team Plan
A coordinated team execution will split work across:
- backend test and coverage lead
- frontend lint/test lead
- E2E lead
- verification/review lead

## Acceptance Criteria
- Backend test command runs successfully
- Frontend lint/test commands run successfully
- Coverage reporting is available and measurable
- Backend reaches 60%+ across agreed core modules
- Frontend reaches 40%+ across key pages/state areas
- 2-3 E2E critical flows pass reliably
