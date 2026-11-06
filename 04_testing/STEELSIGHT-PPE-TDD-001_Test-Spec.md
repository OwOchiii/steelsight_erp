# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-PPE-TDD-001`
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
- `03_implement/STEELSIGHT-PPE-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-003`, `ADR-004`, `ADR-005`, `ADR-014`, `ADR-015`
- `Luật 91/2025/QH15` — accuracy, right to object

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `sh ./mvnw verify` chưa xanh. Synthetic workers, cameras and violations; snapshot paths are fake names.
>
> **Retrospective note & status legend:** see `STEELSIGHT-IAM-TDD-001`. 🟢V = re-run here on `f005861` · 🟢L = last local run 2026-11-06 15:31 (code of `94a2c3c`), not re-run (no Docker) · 🔴 = not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho PPE & Safety |

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
| **Feature / Gap ID** | `PPE` (existing) · gap `G8` (`ViolationService.get` loads before checking) |
| **Module** | `PPE — PPE & Safety (Safety monitoring)` |
| **Spec gốc** | `STEELSIGHT-PPE-IMP-001` |
| **Priority** | 🔴 P0 (scores affect pay) |
| **Sprint** | `Not defined — work done 2026-11-04 → 2026-11-06` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` |
| **Compliance Scope** | `Luật 91/2025/QH15` — DPO pending |
| **Upstream Dependencies** | `IAM`, `SCH`, `TKT`, detection service (inserts violations) |
| **Downstream Consumers** | `NTF`, `RPT`, dashboard |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` (`chatlog/ai-log.md` 2026-11-04 20:23; 2026-11-05 15:47) |
| **Constraint Source** | `STEELSIGHT-PPE-IMP-001 §17`, `ADR-014/015` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Score could be a stored counter | Computed by `safety_scores()` from rows, never stored | TCs read the function, never a column (PPE-TC-001…008) |
| L2 | Each detection costs points | Distinct (type, 30-min block) per worker; repeats in a block count once | PPE-TC-002, 003 |
| L3 | A disputed violation pauses the penalty | DISPUTED still counts until decided; OVERTURNED refunds | PPE-TC-004, 005 |
| L4 | Month = UTC month | Site-time month (`safety_policy.time_zone`) | PPE-TC-006 |
| L5 | Managers see violations of their team | Managers have no `VIOLATION_READ_*`; they see a worker's month only through `/workers/{id}` (`USER_READ_ALL`) | PPE-TC-024 |
| L6 | Missing violation → 404, foreign → 403 | Same, because `get` loads first (gap G8) — reveals ids | PPE-TC-030 🔴 |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
PPE bao gồm các layer:
├── SQL functions            safety_scores, safety_time_slots, violation_set_worker  (Testcontainers)
├── Services                 ViolationService, SafetyScoreService                     (Testcontainers)
├── Atomicity                ServiceAtomicityTest (no class-level @Transactional, real commits)
├── Controller / pages       ViolationController, WorkerController, SafetyController  (MockMvc)
└── Detection contract       schema_checks.sql (insert as steelsight_detection, no dispute changes)
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-WRK-002/003, US-HSE-001/002/005, US-MGR-004 | See, dispute, decide, assign, time slots, directory |
| `ADR-014`, `ADR-015`, `ADR-006` | Score in SQL; dispute+ticket atomic; concurrent decisions |
| `BR-PPE-001` … `BR-PPE-008`, `BR-IAM-011` | Rules |
| `safety_policy` defaults | 10 points, warning ≤ 70, danger ≤ 40, 30-minute repeat window |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | Score formula, repeat window, floor, reset, roles | `safety_scores()` | PPE-TC-001…008 |
| TC-COND-002 | Vest link fills worker; mismatch refused; unidentified queue | `violation_set_worker()` | PPE-TC-009, 010 |
| TC-COND-003 | Dispute: own, once, needs reason | `TicketService.openDispute` | PPE-TC-011…013 |
| TC-COND-004 | Decision: rights, valid outcome, not own, only DISPUTED, ticket resolved | `ViolationService.decide` | PPE-TC-014…018 |
| TC-COND-005 | Atomicity and concurrency | `ServiceAtomicityTest`, `@Version` | PPE-TC-019…021 |
| TC-COND-006 | Assign worker to unidentified | `assignWorker` | PPE-TC-022 |
| TC-COND-007 | Visibility own/all; managers via directory only | `ViolationService.get`, `/workers` | PPE-TC-023…026 |
| TC-COND-008 | Time slots ranking | `safety_time_slots()` | PPE-TC-027, 028 |
| TC-COND-009 | Review lists, paging | `/violations/review` | PPE-TC-029 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Boundary Value Analysis | Score 71/70 (WARNING), 41/40 (DANGER), 0 floor; 29:59 vs 30:00 repeat block; month start at 00:00 site time | Thresholds drive notices and pay |
| State Transition Testing | NONE → DISPUTED → UPHELD/OVERTURNED; UNIDENTIFIED → NONE | Final states, one dispute |
| Equivalence Partitioning | Actor: owner / other worker / HSE / HSE-who-is-the-subject / manager | Access classes |
| Error Guessing | Two officers deciding at once; failure mid-transaction; LIKE wildcards in search | Concurrency, atomicity, injection |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | DB seed | Zone `Zone A`, camera `CAM-1` | Violation camera |
| `FX-002` | DB seed | Workers `W001`, `W002`; HSE `H001`, `H002`; manager `M001` | Actors |
| `FX-003` | DB seed | Vest `V-01` linked to W001 | Vest-read path |
| `FX-004` | DB seed | Violations placed by 30-minute block (`hit(worker, type, block)`), and at `2026-10-31 16:59+00` / `17:00+00` (23:59 on 31 Oct / 00:00 on 1 Nov site time) | Score cases |
| `FX-005` | DB seed | Shifts with equal start/end for 3 workers; violations inside the slot | Time slots |
| `FX-006` | Default policy | `safety_policy` row from V8 | Thresholds |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```java
@SpringBootTest @Import(TestDatabase.class) @Transactional        // SafetyScoreTest pattern
class SafetyNewCasesTest {
    @Autowired JdbcTemplate jdbc;
    Long worker, camera;

    @BeforeEach
    void seed() {                                                  // FX-001/002, rolled back after each test
        Long zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('Zone A') RETURNING id", Long.class);
        camera = jdbc.queryForObject("INSERT INTO camera (camera_code, zone_id) VALUES ('CAM-1', ?) RETURNING id", Long.class, zone);
        worker = jdbc.queryForObject("""
            INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
            SELECT id, 'W001', 'Worker One', 'x' FROM role WHERE role_name = 'WORKER' RETURNING id""", Long.class);
    }
    int score() { return jdbc.queryForObject("SELECT score FROM safety_scores(now()) WHERE user_id = ?", Integer.class, worker); }
}
// ServiceAtomicityTest must NOT be @Transactional (it tests real commits); it cleans up in @AfterEach.
```

---

### PPE-TC-002 — Repeats in the same half hour count once

**Severity:** `HIGH` *(pay-relevant)*
**Feature Under Test:** `safety_scores()`
**Test File:** `SafetyScoreTest.java` — `repeatsInTheSameHalfHourCountOnce`, `theNextHalfHourCountsAgain`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-PPE-005`, `ADR-014 §Decision` (`date_bin(repeat_limit, …)`)

**Test Steps:** FX-004 — three NO_VEST in one block; then a NO_HARD_HAT in the same block; separately, NO_VEST in block 0 and block 1
**Expected Result (PASS):** 90 after the three NO_VEST; 80 once NO_HARD_HAT is added (another type counts separately); 80 for two NO_VEST in consecutive blocks
**Expected Result (FAIL):** 70 after three repeats (camera repeats stacking)
**Current Status:** 🟢L

---

### PPE-TC-004 — An overturned violation gives its points back; a disputed one still counts

**Severity:** `HIGH`
**Feature Under Test:** `safety_scores()` filter `dispute_status <> 'OVERTURNED'`
**Test File:** `SafetyScoreTest.java` — `anOverturnedViolationGivesItsPointsBack`, `aDisputedViolationStillCounts`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-PPE-005`, `ADR-014`

**Expected Result (PASS):** DISPUTED → 90; OVERTURNED → 100
**Expected Result (FAIL):** points not returned after an overturn (worker underpaid)
**Current Status:** 🟢L

---

### PPE-TC-006 — The score resets at the start of the month in site time

**Severity:** `HIGH`
**Feature Under Test:** `safety_scores(p_at)` month window
**Test File:** `SafetyScoreTest.java` — `theScoreResetsAtTheStartOfTheMonthInSiteTime`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-PPE-005` (site-calendar month), `safety_policy.time_zone`

**Expected Result (PASS):** a violation at `2026-10-31 16:59+00` (23:59 site time) gives 90 when read at 16:59:30 UTC and 100 at 17:00 UTC (already 1 Nov at the site); one at 17:00 UTC counts in November
**Expected Result (FAIL):** month boundary at UTC midnight (7 h off)
**Current Status:** 🟢L

---

### PPE-TC-014 — Worker disputes and HSE decides, with the ticket resolved together

**Severity:** `CRITICAL`
**Feature Under Test:** `TicketService.openDispute` → `ViolationService.decide`
**Test File:** `ServiceIntegrationTest.java` — `workerDisputesAndHseDecidesWithTicketResolvedTogether`; `HsePagesIntegrationTest.java` — `decidingShowsTheReasonThenResolvesTheTicketWithTheNote`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `ADR-015 §Decision`, `BR-PPE-004`

**Test Steps:**
1. W001 disputes their violation with a reason
2. H001 decides OVERTURNED with a note
3. Read violation, ticket, comments

**Expected Result (PASS):** violation `OVERTURNED`, `reviewed_by = H001`, `reviewed_at` set; ticket `RESOLVED`, `resolver = H001`; note saved as a comment; page shows the reason before deciding
**Expected Result (FAIL):** violation decided but ticket open (or the reverse)
**Current Status:** 🟢L

---

### PPE-TC-017 — An officer can see but not decide their own violation

**Severity:** `HIGH`
**OWASP:** `A01:2021 — Broken Access Control`
**CWE:** `CWE-284` (separation of duties)
**Feature Under Test:** `ViolationService.decide` (`belongsTo(actorId)`)
**Test File:** `HsePagesIntegrationTest.java` — `anOfficerCanSeeButNotDecideTheirOwnViolation`; `ServiceIntegrationTest.java` — `hseCannotDecideTheirOwnViolation`
**TDD Phase:** 🟢 GREEN
**Oracle Source:** `BR-PPE-004`, `ADR-015` constraint 3

**Expected Result (PASS):** page shows no decision form; a forged POST → 403
**Expected Result (FAIL):** an officer overturns their own violation
**Current Status:** 🟢L

---

### PPE-TC-020 — Two officers deciding at once can't both win

**Severity:** `HIGH`
**CWE:** `CWE-362 — Race Condition`
**Feature Under Test:** `Violation.@Version` in `decide()`
**Test File:** `ServiceAtomicityTest.java` — `twoOfficersDecidingAtOnceCannotBothWin`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-005`
**Oracle Source:** `ADR-006`, `ADR-015`

**Test Steps:** officer two loads the violation; officer one's decision is written (version + 1); officer two calls `decide(…, OVERTURNED)` in the same transaction
**Expected Result (PASS):** `OptimisticLockingFailureException`; the transaction rolls back and the violation is still `DISPUTED`
**Expected Result (FAIL):** officer two's decision overwrites officer one's (last write wins)
**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### PPE-TC-023 — Someone else's violation is forbidden

**Severity:** `HIGH`
**OWASP:** `A01:2021`
**CWE:** `CWE-639 — Authorization Bypass Through User-Controlled Key`
**Feature Under Test:** `GET /violations/{id}`, `POST /violations/{id}/dispute`
**Test File:** `PagesIntegrationTest.java` — `someoneElsesViolationIsForbidden`, `youCannotDisputeSomeoneElsesViolation`, `violationListShowsOnlyYourOwn`
**TDD Phase:** 🟢 GREEN

**Test Steps (Attack Simulation):** W002 requests W001's violation id and posts a dispute for it
**Expected Result (PASS = hệ thống an toàn):** `403`; no ticket; status stays NONE
**Expected Result (FAIL = lỗ hổng tồn tại):** W002 sees W001's snapshot or disputes for them
**Current Status:** 🟢L

---

### PPE-TC-025 — Worker search treats wildcards as plain text

**Severity:** `MEDIUM`
**OWASP:** `A03:2021 — Injection`
**CWE:** `CWE-89` (LIKE pattern injection)
**Feature Under Test:** `SafetyScoreService.listWorkers(search)`
**Test File:** `WorkerAccessPagesTest.java` — `searchTreatsWildcardsAsPlainText`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** `q=%` or `q=_` matches only names containing those characters
**Expected Result (FAIL):** `%` lists every worker
**Current Status:** 🟢L

---

### PPE-TC-030 — A missing violation and a foreign one get the same answer

**Severity:** `LOW`
**CWE:** `CWE-203 — Observable Discrepancy`
**Feature Under Test:** `ViolationService.get` (gap G8)
**Test File:** *(new)* `PagesIntegrationTest.java`
**TDD Phase:** 🔴 RED — expected to fail today

**Test Steps (Attack Simulation):** W002 GETs `/violations/{W001's id}` and `/violations/999999`
**Expected Result (PASS):** both 403 (or both 404)
**Expected Result (FAIL):** 403 vs 404 → ids enumerable
**Current Status:** 🔴 Not written

---

### PPE-TC-031 — Detection can't change a dispute

**Severity:** `CRITICAL`
**CWE:** `CWE-250`
**Feature Under Test:** grants on `violation`
**Test File:** `SchemaChecksSqlTest.java` (`schema_checks.sql`: "detection changed a dispute")
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** `UPDATE violation SET dispute_status` as `steelsight_detection` → permission denied
**Current Status:** 🟢L

---

### INTEGRATION TEST CASES

---

### PPE-TC-INT-001 — An unidentified violation is given a worker, who can then dispute it

**Severity:** `HIGH`
**Feature Under Test:** Full flow: detection insert without vest → `/violations/review?view=UNIDENTIFIED` → `POST /violations/{id}/worker` → worker sees it → dispute
**Test File:** `HsePagesIntegrationTest.java` — `anUnidentifiedViolationCanBeGivenAWorkerWhoCanThenDisputeIt`; `ServiceIntegrationTest.java` — `hseAssignsUnidentifiedViolationsThenTheWorkerSeesThem`; `SafetyScoreTest.java` — `anUnidentifiedViolationCostsNothingUntilHseNamesTheWorker`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-006`

**Preconditions:** Testcontainers PostgreSQL; FX-001, FX-002

**Test Steps:**
1. Insert a violation with no vest link
2. HSE assigns W001
3. W001 lists violations and disputes

**Expected Result (PASS):** in the UNIDENTIFIED list before; W001's score unchanged until assigned, then 90; dispute opens a ticket; a second assignment → "This violation already has a worker"

**DB Assertion:**
```java
Map<String, Object> v = jdbc.queryForMap("SELECT user_id, dispute_status FROM violation WHERE id = ?", violation);
assertThat(v.get("user_id")).isEqualTo(worker);
assertThat(v.get("dispute_status")).isEqualTo("DISPUTED");
```

**Current Status:** 🟢L

---

### TC catalogue — remaining existing tests

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| PPE-TC-001 | `SafetyScoreTest` — `aWorkerStartsAt100AndOtherRolesHaveNoScore` | COND-001 | HIGH | 🟢L |
| PPE-TC-003 | `SafetyScoreTest` — `theNextHalfHourCountsAgain` | COND-001 | HIGH | 🟢L |
| PPE-TC-005 | `SafetyScoreTest` — `aDisputedViolationStillCounts` | COND-001 | HIGH | 🟢L |
| PPE-TC-007 | `SafetyScoreTest` — `theScoreStopsAtZero` | COND-001 | MEDIUM | 🟢L |
| PPE-TC-009 | `RepositoryIntegrationTest` — `vestReadFillsInTheWorkerAndFindsThemByColumn` | COND-002 | HIGH | 🟢L |
| PPE-TC-010 | `RepositoryIntegrationTest` — `unidentifiedViolationQueuesForHseAndCanBeAssignedWithoutAVest` | COND-002 | HIGH | 🟢L |
| PPE-TC-011 | `PagesIntegrationTest` — `disputingAViolationOpensATicketAndCannotBeRepeated` | COND-003 | HIGH | 🟢L |
| PPE-TC-012 | `PagesIntegrationTest` — `aDisputeNeedsAReason` | COND-003 | MEDIUM | 🟢L |
| PPE-TC-013 | `ServiceIntegrationTest` — `onlyTheOwnerDisputesAndOnlyOnce` | COND-003 | HIGH | 🟢L |
| PPE-TC-015 | `ServiceIntegrationTest` — `decisionsNeedReviewRightsAndAValidOutcome`; `HsePagesIntegrationTest` — `aDecisionMustBeUpheldOrOverturned` | COND-004 | HIGH | 🟢L |
| PPE-TC-016 | `HsePagesIntegrationTest` — `aSecondDecisionIsRefused` | COND-004 | MEDIUM | 🟢L |
| PPE-TC-018 | `HsePagesIntegrationTest` — `managersCannotDecideDisputes`, `workersCannotReachTheReviewPagesOrActions` | COND-004 | HIGH | 🟢L |
| PPE-TC-019 | `ServiceAtomicityTest` — `failedTicketInsertLeavesTheViolationUndisputed` | COND-005 | HIGH | 🟢L |
| PPE-TC-021 | `ServiceAtomicityTest` — `failedDecisionLeavesBothTheViolationAndTheTicketUntouched`; `RepositoryIntegrationTest` — `staleEditOfAViolationFailsInsteadOfOverwriting` | COND-005 | HIGH | 🟢L |
| PPE-TC-022 | `ServiceIntegrationTest` — `hseAssignsUnidentifiedViolationsThenTheWorkerSeesThem` | COND-006 | HIGH | 🟢L |
| PPE-TC-024 | `WorkerAccessPagesTest` — `managerStillCannotOpenViolationPages`, `managerSeesAnotherTeamsWorkerButCannotChangeThem`, `onlyWorkersHaveAWorkerPage` | COND-007 | HIGH | 🟢L |
| PPE-TC-026 | `WorkerAccessPagesTest` — `managerAndHseSeeEveryWorkerWithTheirScoreLowestFirst`, `workerCannotSeeTheWorkerPages` | COND-007 | MEDIUM | 🟢L |
| PPE-TC-027 | `SafetyScoreTest` — `timeSlotsGroupShiftsAndCompareByWorkerHours` | COND-008 | MEDIUM | 🟢L |
| PPE-TC-028 | `WorkerAccessPagesTest` — `hseSeesHowUnsafeEachTimeSlotWas` | COND-008 | MEDIUM | 🟢L |
| PPE-TC-029 | `HsePagesIntegrationTest` — `reviewListsDisputesAndViolationsWithNoWorker`, `homeCountsWhatIsWaiting` | COND-009 | MEDIUM | 🟢L |
| PPE-TC-032 | *(new)* Changing `safety_policy` (points, thresholds) changes the current month's scores at once | COND-001 | MEDIUM | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `PPE-TC-001…008` | `SafetyScoreTest.java` | `[ ]` not evidenced | `94a2c3c` (last local run) | — |
| `PPE-TC-009…029, 031` | `RepositoryIntegrationTest`, `ServiceIntegrationTest`, `ServiceAtomicityTest`, `HsePagesIntegrationTest`, `PagesIntegrationTest`, `WorkerAccessPagesTest`, `SchemaChecksSqlTest` | `[ ]` | `94a2c3c` | `ViolationService` depends on ticket repositories (accepted cycle, ADR-015) |
| `PPE-TC-030, 032` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```java
public Violation decide(Long actorId, Long violationId, DisputeStatus decision, String note) {
    throw new UnsupportedOperationException("Not implemented — Red Phase stub");
}
// For SQL rules: a scratch migration replacing safety_scores() with "SELECT id, 100, 'SAFE' FROM app_user"
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `PPE-TC-002…007` | constant-100 `safety_scores()` | 🔴 FAIL (except TC-001, which expects 100 — **AP-AI-002 risk**) | ☐ FAIL ☐ PASS — not run | ☐ Tautology ☐ Shared state ☐ Hallucinated import |
| `PPE-TC-014` | `throw` in `decide` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `PPE-TC-017` | `decide` without the `belongsTo` check | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3)
- Log file: `target/surefire-reports/`

> PPE-TC-001 ("starts at 100") passes against a constant-100 stub by design; it must be paired with PPE-TC-002 so the pair can't be green-from-birth.

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-PPE-IMP-001` approved
- [ ] HSE management confirms `safety_policy` values
- [ ] Logic Issues confirmed (L6 needs a decision)

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` green (Docker)
- [ ] `ServiceAtomicityTest` green (real commits, cleaned up)
- [ ] Coverage ≥ 80% for new files — **no JaCoCo**; add or waive
- [ ] A hand-computed score for one synthetic worker equals `safety_scores()` (TDS §14.1 query)

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)**
- [ ] **Contract Existence:** `sh ./mvnw -q -o test-compile` clean
- [ ] **Props Isolation:** `@Transactional` per class except atomicity tests, which clean up in `@AfterEach`
- [ ] **Oracle Source** cited

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable
- `safety_policy` change requested mid-month (affects live scores)
- Detection contract change

---

## 7. Rollback Plan

```bash
# Testcontainers DB is throwaway. Local dev DB: recreate; Flyway rebuilds.
git checkout -- src/main/java/orochi/steelsight/ppe src/main/java/orochi/steelsight/web/ViolationController.java
# Gap G8 stays OPEN in 02_design/MODULE-MAP.md §6
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ PPE-TC-001 passes a constant stub by design — paired with TC-002 | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ PPE-TC-030 — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ none | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☑ all exist at `f005861` | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-002` | `PPE-TC-001` | Passes against a constant-100 stub | Keep paired with PPE-TC-002 in the same run; never run alone as evidence | ☐ |
| `AP-AI-003` | `PPE-TC-030` | 404-vs-403 policy not in an ADR | Decide and record | ☐ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
