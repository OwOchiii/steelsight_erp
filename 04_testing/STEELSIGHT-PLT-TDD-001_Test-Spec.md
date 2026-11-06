# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-PLT-TDD-001`
**Version:** `1.0`
**Date:** `2026-11-06`
**Status:** `Draft`
**Standard:** ISO/IEC/IEEE 29119-3:2021 — Software Testing Part 3: Test Documentation
**Author:** `Claude Code (Opus 5.5) — documentation, from the existing test suite at commit f005861`
**Reviewed by:** `[ ] Tech Lead — Pending`
**DPO Sign-off:** `[ ] Pending`
**Approved by:** `[ ] Pending`
**Classification:** `Internal — Confidential`

**References:**
- `02_design/MODULE-MAP.md` — BR / US catalog
- `03_implement/STEELSIGHT-PLT-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-002`, `ADR-003`, `ADR-004`, `ADR-006`, `ADR-018`, `ADR-023`, `ADR-024`
- `Luật 91/2025/QH15` — logging, processors

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `sh ./mvnw verify` chưa xanh. Synthetic data only.
>
> **Retrospective note & status legend:** see `STEELSIGHT-IAM-TDD-001`. 🟢V = re-run here on `f005861` · 🟢L = last local run 2026-11-06 15:31 (code of `94a2c3c`), not re-run (no Docker) · 🔴 = not written.
>
> **Suite-wide numbers (last local run):** 244 tests in 26 classes, 0 failures, 0 errors, 0 skipped. Re-run in this session: the 39 database-free tests in 6 classes, all passing.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Platform |

---

## MỤC LỤC

1. [Thông tin Module](#1-thông-tin-module)
   - 1.1 [AI Generation Context (CASE 2.0)](#11-ai-generation-context-case-20) ⭐ *Mới*
2. [Logic Issues Resolved](#2-logic-issues-resolved)
3. [Test Design Specification (TDS)](#3-test-design-specification-tds)
4. [Test Case Specification](#4-test-case-specification)
   - Props Isolation Boilerplate ⭐ *Mới*
5. [Red-Green-Refactor Tracker](#5-red-green-refactor-tracker)
   - 5.1 [Red Gate Protocol (CASE 2.0)](#51-red-gate-protocol-case-20--gate-2) ⭐ *Mới*
6. [Entry / Exit Criteria](#6-entry--exit-criteria)
7. [Rollback Plan](#7-rollback-plan)
8. [CASE 2.0 Anti-Pattern Detection](#8-case-20-anti-pattern-detection-ai-assisted-tcs) ⭐ *Mới*

---

## 1. Thông tin Module

| Field | Value |
|-------|-------|
| **Feature / Gap ID** | `PLT` (existing) · gaps `G4` (no error codes), `G6` (in-memory sessions); no JaCoCo; no Semgrep/Trivy yet |
| **Module** | `PLT — Platform (shared kernel)` |
| **Spec gốc** | `STEELSIGHT-PLT-IMP-001` |
| **Priority** | 🔴 P0 (everything stands on it) |
| **Sprint** | `Not defined — work done 2026-11-02 → 2026-11-06` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `Internal` (+ employee codes in logs) |
| **Compliance Scope** | `Luật 91/2025/QH15` — DPO pending |
| **Upstream Dependencies** | Spring Boot 4, PostgreSQL 17 + pgvector, Testcontainers 2.0.2, Render, Supabase |
| **Downstream Consumers** | All modules |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` (`chatlog/ai-log.md`, all dates) |
| **Constraint Source** | `STEELSIGHT-PLT-IMP-001 §17`, `ADR-002/003/024` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C6 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Template: `npm test`, Prisma, `.spec.ts` | `sh ./mvnw verify`, Flyway, JUnit 6 + AssertJ + MockMvc + Testcontainers | All commands in this spec are Maven |
| L2 | Template: other actuator endpoints → 404 | Anonymous requests to them redirect to `/login` (302) because the page chain requires sign-in | PLT-TC-004 asserts 3xx |
| L3 | Template: RLS optional | RLS on every public table, re-applied after every migrate | PLT-TC-005 |
| L4 | Coverage ≥ 80% | No coverage tool configured | Exit criteria mark it "not measurable" until JaCoCo is added (PLT-TC-030 🔴) |
| L5 | CI scans (Semgrep, Trivy) | Planned for GitLab; not in the repository | PLT-TC-031 🔴 |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
PLT bao gồm các layer:
├── Context / wiring          SteelsightErpApplicationTests (Spring context starts against Testcontainers)
├── Schema                    Flyway + SchemaChecksSqlTest (schema_checks.sql), RLS check
├── Base entities             equality by id, no recursion on cycles
├── Error handling / logging  PageExceptionHandler, RequestLogFilter (CapturedOutput)
├── i18n                      TranslationsTest (properties files, templates, code messages)
└── Web shell                 menus per role, CSRF, escaping, paging, 404 for bad ids, actuator exposure
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-ALL-001 | Bilingual, role-based menus |
| `ADR-002`, `ADR-003`, `ADR-004`, `ADR-024`, `ADR-023` | Pages; Flyway; RLS; logging; health only |
| `BR-PLT-001` … `BR-PLT-008` | Rules |
| `docs/deployment.md` §6 | Live-site checks |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | App context starts; schema validates | `@SpringBootTest`, `ddl-auto: validate` | PLT-TC-001 |
| TC-COND-002 | Schema rules hold in SQL alone | `schema_checks.sql` | PLT-TC-002 |
| TC-COND-003 | Entities equal by id; no stack overflow on cycles | `IdentifiedModel` | PLT-TC-003, 006 |
| TC-COND-004 | Health public and terse; others closed | actuator | PLT-TC-004, 007 |
| TC-COND-005 | RLS on every public table | `afterMigrate__enable_rls.sql` | PLT-TC-005 |
| TC-COND-006 | Translations complete | `TranslationsTest` | PLT-TC-008…010 |
| TC-COND-007 | Logging: stack trace + reference; 404 not a bug; refused requests logged | `RequestLogFilter`, `PageExceptionHandler` | PLT-TC-011…013 |
| TC-COND-008 | CSRF, escaping, menus per role, bad ids, paging | web shell | PLT-TC-014…020 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Equivalence Partitioning | Roles × menu items | Each role sees its own work |
| Error Guessing | Malformed ids (`/tickets/abc`), negative page numbers, unknown paths, script in text | Robustness |
| Static analysis (in test) | Properties keys vs template keys vs code messages | Translation drift |
| Fault Injection | A controller that throws (`/test-broken`) | Error path and logging |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | Container | `pgvector/pgvector:pg17` started once (`TestDatabase`) and shared via `@ServiceConnection` | Real PostgreSQL with extensions |
| `FX-002` | Files | `messages.properties`, `messages_vi.properties`, `templates/**/*.html`, `src/main/java/**` | Translation checks |
| `FX-003` | Output capture | `OutputCaptureExtension` / `CapturedOutput` | Log assertions |
| `FX-004` | Test controller | `/test-broken` throwing `IllegalStateException("the database fell over")` | 500 path |
| `FX-005` | DB seed | One user per role via `PageTestSupport.userWithRole` | Menus |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```java
// Shared, immutable infrastructure — one container for the whole run (safe: tests never change the schema)
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {
    static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
    static { CONTAINER.start(); }
    @Bean @ServiceConnection PostgreSQLContainer postgres() { return CONTAINER; }
}
// Mutable data — per test: @Transactional class (rollback) + @BeforeEach seed(); tests that must commit
// (ServiceAtomicityTest, FacePhotoCleanupTest) use unique codes and/or clean up in @AfterEach.
```

---

### PLT-TC-002 — Schema rules hold (SQL-only checks)

**Severity:** `CRITICAL`
**Feature Under Test:** constraints, triggers, grants, policies across V1–V11
**Test File:** `SchemaChecksSqlTest.java` — `schemaRulesHold` (runs `src/test/resources/db/schema_checks.sql`)
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-002`
**Oracle Source:** `ADR-003`, `ADR-004`, `ADR-005`

**Test Steps:** run the script in one transaction that rolls back; it raises `CHECK_FAILED: …` on any broken rule
**Expected Result (PASS):** ends with `ALL CHECKS PASSED` (generated duration; unidentified violation can't be disputed; vest read fills worker; mismatched worker refused; READY needs a vector; forgotten check-out closes; every outbox table refuses a repeated event; detection role can do its job and can't read `password_hash`, change a dispute, or read tickets)
**Expected Result (FAIL):** any `CHECK_FAILED` line
**Current Status:** 🟢L

---

### PLT-TC-005 — Every public table has Row Level Security

**Severity:** `CRITICAL`
**OWASP:** `A05:2021 — Security Misconfiguration`
**CWE:** `CWE-284`
**Legal:** `Luật 91/2025/QH15 — security measures` (Supabase Data API exposure)
**Feature Under Test:** `afterMigrate__enable_rls.sql`
**Test File:** `RepositoryIntegrationTest.java` — `everyPublicTableHasRowLevelSecurity`
**TDD Phase:** 🟢 GREEN
**Oracle Source:** `ADR-004 §Decision`

**Expected Result (PASS):** `SELECT tablename FROM pg_tables WHERE schemaname='public' AND NOT rowsecurity` returns nothing
**Expected Result (FAIL):** a table readable through Supabase's public API
**Current Status:** 🟢L

---

### PLT-TC-008 — Both languages have the same keys, and every message has a translation

**Severity:** `MEDIUM`
**Feature Under Test:** message files, templates, code messages
**Test File:** `TranslationsTest.java` — `bothLanguagesHaveTheSameKeys`, `everyKeyATemplateUsesExists`, `everyMessageWrittenInTheCodeHasAVietnameseTranslation`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-006`
**Oracle Source:** `BR-PLT-006`, `ADR-002` constraint 3

**Expected Result (PASS):** equal key sets; every `#{key}` in templates exists; every English rule/flash message in the code has a `msg.<slug>` key in Vietnamese
**Expected Result (FAIL):** a Vietnamese reader sees English or `??key??`
**Current Status:** 🟢V (3/3)

---

### PLT-TC-011 — An unexpected failure is logged with its stack trace and a reference the user can quote

**Severity:** `HIGH`
**CWE:** `CWE-209 — Information Exposure Through an Error Message`
**Feature Under Test:** `PageExceptionHandler.unexpected`, `RequestLogFilter`
**Test File:** `RequestLoggingTest.java` — `anUnexpectedFailureIsLoggedWithItsStackTraceAndAReferenceTheUserCanQuote`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-007`
**Oracle Source:** `ADR-024 §Decision`

**Test Steps:** GET `/test-broken` as `LOG-1`
**Expected Result (PASS):** 500 page contains "reference <8 hex>"; the log has the exception and message, `[<reference>]` and `GET /test-broken -> 500 user=LOG-1`; the page does **not** contain the exception message
**Expected Result (FAIL):** stack trace shown to the user, or no way to link the report to the log
**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### PLT-TC-004 — Health is public and shows no details; other actuator paths stay closed

**Severity:** `HIGH`
**OWASP:** `A05:2021`
**CWE:** `CWE-200`
**Feature Under Test:** `management.endpoints.web.exposure.include: health`
**Test File:** `DeploymentEndpointsTest.java` — `healthIsPublicAndShowsNoDetails`, `otherActuatorPathsStayClosed`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS = hệ thống an toàn):** `/actuator/health` → 200 `{"status":"UP"}` with no `components`; anonymous `/actuator/env` and `/actuator` → 3xx (to sign-in)
**Expected Result (FAIL = lỗ hổng tồn tại):** environment (secrets) or metrics exposed
**Current Status:** 🟢L

---

### PLT-TC-014 — Forms without a CSRF token are refused; page text is escaped

**Severity:** `HIGH`
**OWASP:** `A01:2021`, `A03:2021`
**CWE:** `CWE-352`, `CWE-79`
**Feature Under Test:** Spring Security CSRF; Thymeleaf escaping
**Test File:** `PagesIntegrationTest.java` — `formsWithoutACsrfTokenAreRefused`, `pageTextIsEscaped`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** 403 without `_csrf`; `<script>` rendered as text
**Current Status:** 🟢L

---

### PLT-TC-032 — The language switch never redirects off-site

**Severity:** `MEDIUM`
**OWASP:** `A01:2021`
**CWE:** `CWE-601 — Open Redirect`
**Feature Under Test:** `LanguageController.pageBefore`
**Test File:** *(new)* `PagesIntegrationTest.java`
**TDD Phase:** 🔴 RED (expected to pass — pins behaviour)

**Test Steps (Attack Simulation):** POST `/language` with `Referer: https://evil.example/phish`, `Referer: //evil.example/x`, and no Referer
**Expected Result (PASS):** `Location` is `/phish`, `/`, `/` respectively — never another host
**Expected Result (FAIL):** redirect to `evil.example`
**Current Status:** 🔴 Not written

---

### INTEGRATION TEST CASES

---

### PLT-TC-INT-001 — Menus and pages follow each role's permissions

**Severity:** `HIGH`
**Feature Under Test:** Full flow: sign in as each role → dashboard and sidebar → forbidden pages
**Test File:** `PagesIntegrationTest.java` — `menuAndPagesFollowTheRolesPermissions`; `HsePagesIntegrationTest.java` — `menuShowsHseWorkAndHidesManagerWork`; `ManagerPagesIntegrationTest.java` — `menuShowsManagerWorkAndHidesHseWork`, `adminCanOpenEveryManagementPage`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-008`

**Preconditions:** Testcontainers PostgreSQL; FX-005

**Expected Result (PASS):** worker menu links `/violations`; HSE menu doesn't, and `GET /violations` as HSE is 403; HSE sees review/queue but not shift planning; manager sees planning but not HSE review; admin opens every management page

**DB Assertion:**
```java
// No DB assertion: menu content is checked in the rendered HTML
mvc.perform(get("/").with(as(hse))).andExpect(content().string(not(containsString("href=\"/violations\""))));
```

**Current Status:** 🟢L

---

### TC catalogue — remaining existing tests

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| PLT-TC-001 | `SteelsightErpApplicationTests` — `contextLoads` | COND-001 | CRITICAL | 🟢L |
| PLT-TC-003 | `RepositoryIntegrationTest` — `entitiesAreEqualById` | COND-003 | MEDIUM | 🟢L |
| PLT-TC-006 | `RepositoryIntegrationTest` — `managerInOwnTeamDoesNotOverflowTheStack` | COND-003 | MEDIUM | 🟢L |
| PLT-TC-007 | `DeploymentEndpointsTest` — `otherActuatorPathsStayClosed` | COND-004 | HIGH | 🟢L |
| PLT-TC-009 | `TranslationsTest` — `everyKeyATemplateUsesExists` | COND-006 | MEDIUM | 🟢V |
| PLT-TC-010 | `TranslationsTest` — `everyMessageWrittenInTheCodeHasAVietnameseTranslation` | COND-006 | MEDIUM | 🟢V |
| PLT-TC-012 | `RequestLoggingTest` — `anUnknownAddressIsStillNotFoundAndNotABug` | COND-007 | LOW | 🟢L |
| PLT-TC-013 | `RequestLoggingTest` — `aRefusedRequestIsLoggedEvenBeforeSignIn` | COND-007 | MEDIUM | 🟢L |
| PLT-TC-015 | `PagesIntegrationTest` — `aMissingOrMalformedIdIsNotFound` | COND-008 | MEDIUM | 🟢L |
| PLT-TC-016 | `HsePagesIntegrationTest` — `longListsArePagedAndKeepTheirFilter` | COND-008 | LOW | 🟢L |
| PLT-TC-017 | `HsePagesIntegrationTest` — `oddPageNumbersDoNotBreakTheList` | COND-008 | LOW | 🟢L |
| PLT-TC-030 | *(new)* JaCoCo report generated in `mvn verify`; threshold enforced for new code | — | MEDIUM | 🔴 |
| PLT-TC-031 | *(new)* CI runs Semgrep and Trivy and fails on high findings (GitLab move) | — | HIGH | 🔴 |
| PLT-TC-033 | *(new)* Every template renders in both languages without `??` markers (smoke over all pages) | COND-006 | LOW | 🔴 |
| PLT-TC-034 | *(new)* The Docker image runs as uid 1001 and starts with `SPRING_PROFILES_ACTIVE=prod` refusing missing Supabase settings | — | MEDIUM | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `PLT-TC-008…010` | `TranslationsTest.java` | `[ ]` not evidenced | `f005861` (re-run 2026-11-06, 3/3) | — |
| `PLT-TC-001…007, 011…017` | `SteelsightErpApplicationTests`, `SchemaChecksSqlTest`, `RepositoryIntegrationTest`, `DeploymentEndpointsTest`, `RequestLoggingTest`, `PagesIntegrationTest`, `HsePagesIntegrationTest`, `ManagerPagesIntegrationTest` | `[ ]` | `94a2c3c` (last local run) | — |
| `PLT-TC-030…034` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```sql
-- Scratch migration for the Red Gate of PLT-TC-005 (never commit):
ALTER TABLE zone DISABLE ROW LEVEL SECURITY;   -- with afterMigrate also renamed, the RLS test must FAIL
```

```java
// For PLT-TC-011: make PageExceptionHandler.unexpected() return the exception message in the page.
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `PLT-TC-005` | RLS off on one table | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | ☐ Tautology ☐ Shared state (afterMigrate re-enabled it) ☐ Hallucinated import |
| `PLT-TC-008` | delete one key from `messages_vi.properties` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `PLT-TC-011` | message shown on page | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3)
- Log file: `target/surefire-reports/`

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-PLT-IMP-001` approved
- [ ] Docker available to the developer and CI (Testcontainers)
- [ ] `JAVA_HOME` points at a JDK 17 (this session needed `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`)

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` — **all 244 tests green**, none skipped (re-confirm in CI; only 39 were re-run in this session)
- [ ] GitHub Actions `test` job green on the branch
- [ ] Line coverage ≥ 80% for new files — **not measurable: no JaCoCo** (PLT-TC-030)
- [ ] No secret in the repository (`git grep -nE 'SUPABASE_SERVICE_KEY=.+|DB_PASSWORD=.+'` → only `.env.example` empties)
- [ ] `SchemaChecksSqlTest` ends with `ALL CHECKS PASSED`

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)** — at least PLT-TC-005, 008, 011 back-filled
- [ ] **Contract Existence:**
  ```bash
  sh ./mvnw -q -o test-compile 2>&1 | grep -E "cannot find symbol|package .* does not exist"
  # Expected: no output
  ```
- [ ] **Props Isolation:**
  ```bash
  grep -L -E '@Transactional|@AfterEach|UUID.randomUUID' src/test/java/orochi/steelsight/*Test.java
  # Review any listed class that writes to the database
  ```
- [ ] **Oracle Source** cited

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable (as in this session) — only DB-free tests can run
- Flyway checksum mismatch (someone edited an applied migration)
- CI broken by another change

---

## 7. Rollback Plan

```bash
# Testcontainers DB is created per run and thrown away.
# Local dev DB (port 55433): drop and recreate; Flyway rebuilds from V1.
docker exec -it steelsight-db psql -U postgres -c 'DROP DATABASE steelsight_local;' -c 'CREATE DATABASE steelsight_local;'
git checkout -- src/main/java/orochi/steelsight/{model,repository,exception,validation,config,web}
# Production rollback: STEELSIGHT-PLT-IMP-001 §12
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ not verified; PLT-TC-005 stub must also defeat `afterMigrate` | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ PLT-TC-030/031 depend on CI decisions (GitLab move) — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ none | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☑ all exist at `f005861` (`/test-broken` is a test-only controller inside `RequestLoggingTest`) | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-002` (risk) | all 🟢 | Suite written alongside AI-generated code; Red Gate never run | Back-fill §5.1 for the CRITICAL TCs of each module | ☐ |
| `AP-AI-003` | `PLT-TC-030, 031` | Tooling not decided | Decide with the GitLab migration | ☐ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
