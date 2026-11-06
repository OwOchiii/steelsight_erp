# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-SCH-TDD-001`
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
- `03_implement/STEELSIGHT-SCH-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-001`, `ADR-006`, `ADR-017`, `ADR-018`
- `Luật 91/2025/QH15` — working-time data (DPO pending)

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `sh ./mvnw verify` chưa xanh. Synthetic data only.
>
> **Retrospective note & status legend:** see `STEELSIGHT-IAM-TDD-001` header. 🟢V = re-run here on `f005861` · 🟢L = last local run 2026-11-06 15:31 (code of `94a2c3c`, no source changes since), not re-run (no Docker) · 🔴 = not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Scheduling |

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
| **Feature / Gap ID** | `SCH` (existing) · gap `G8` (`TeamService.setLateAfterMinutes` loads before checking) |
| **Module** | `SCH — Scheduling (Workforce planning)` |
| **Spec gốc** | `STEELSIGHT-SCH-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined — work done 2026-11-04 → 2026-11-06` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` (who works when) |
| **Compliance Scope** | `Luật 91/2025/QH15` — DPO pending |
| **Upstream Dependencies** | `IAM`, `PLT` (`SiteTime`) |
| **Downstream Consumers** | `ATT`, `RPT`, `PPE` (time slots), dashboard |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` (`chatlog/ai-log.md` 2026-11-04 14:52, 20:23, 22:39; 2026-11-06) |
| **Constraint Source** | `STEELSIGHT-SCH-IMP-001 §17`, `ADR-017/018 §AI Prompt Constraint` |
| **Constraints Injected** | Not recorded for original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | V1: a manager may run several teams | V11: one team per leader (`uq_team_manager`) | TCs assert a second team for the same leader is refused by service **and** DB (SCH-TC-015, 016) |
| L2 | A week = `BETWEEN monday AND sunday` (legacy repository methods) | Pages use `[monday, monday+7)` half-open, by start day | TCs check the midnight shift belongs to the next week only (SCH-TC-004) |
| L3 | Overlap is a Java check | GiST exclusion constraint, cancelled excluded | TCs assert the race-proof DB refusal and that cancelling frees the time (SCH-TC-008) |
| L4 | Missing team returns 403 for a stranger | `setLateAfterMinutes` loads first → 404 vs 403 reveals ids (gap G8) | SCH-TC-030 🔴 pins the desired behaviour (same answer for missing and foreign) |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
SCH bao gồm các layer:
├── Pure helpers                 ShiftGrid, SiteTime (exercised through page tests)
├── Services                     ShiftService, TeamService — Testcontainers PostgreSQL
├── Controller / pages           ShiftController, TimetableController, TeamController (teams) — MockMvc
└── Database rules               EXCLUDE gist, CHECK end>start, generated duration, uq_team_manager, can_assign_shift, can_edit_shift
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-MGR-001/002, US-WRK-001, US-ADM-001 | Plan week, late rule, own timetable, teams |
| `ADR-017`, `ADR-018`, `ADR-006` | One leader; exclusion + site days; versioned edits |
| `BR-SCH-001` … `BR-SCH-008` | Rules |
| `STEELSIGHT-SCH-IMP-001` §16 | Authorization matrix |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | Grid shows only the leader's team; navigation by week | `GET /shifts`, `ShiftService.listManaged` | SCH-TC-001, 002 |
| TC-COND-002 | Shift on the day it starts; overnight; midnight → next week | `ShiftGrid`, `[from,to)` | SCH-TC-003, 004 |
| TC-COND-003 | Weekly hours exclude cancelled | `ShiftGrid.hours` | SCH-TC-005 |
| TC-COND-004 | Plan only own team's active workers; admin anyone | `can_assign_shift` | SCH-TC-006, 007 |
| TC-COND-005 | Overlap refused; cancel frees time | Exclusion constraint | SCH-TC-008, 009 |
| TC-COND-006 | Form errors re-show the form | `ShiftController` + `submit` | SCH-TC-010 |
| TC-COND-007 | Stale edit 409; final shifts locked | `requireVersion`, `scheduled()` | SCH-TC-011, 012 |
| TC-COND-008 | Another manager can't open/change | `can_edit_shift` | SCH-TC-013 |
| TC-COND-009 | Own timetable only | `TimetableController` | SCH-TC-014 |
| TC-COND-010 | One leader per team; only admins set up teams | `TeamService`, `uq_team_manager` | SCH-TC-015, 016, 017 |
| TC-COND-011 | Late rule: own team or admin; 0–240 | `setLateAfterMinutes` | SCH-TC-018, 019 |
| TC-COND-012 | Reads follow permissions | `ShiftService.list*` | SCH-TC-020 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Equivalence Partitioning | Workers: own team / other team / inactive / no team | Scope classes |
| Boundary Value Analysis | Late rule −1, 0, 240, 241; shift end = start; midnight starts | Boundaries of CHECK and week ranges |
| State Transition Testing | SCHEDULED → COMPLETED / CANCELLED, edits after final | Final states must be locked |
| Error Guessing | Concurrent overlapping inserts; tampered `workerId`; malformed `week` | Race and tampering |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | DB seed | Leader `M001` of team `Tổ 1` with workers `W001`, `W002`; leader `M002` of `Tổ 2` with `W003` | Scope |
| `FX-002` | DB seed | Zones `Zone A`, `Zone B` | Shift zone |
| `FX-003` | DB seed | Shift `W001` Tue 08:00–17:00 site time (= 01:00–10:00 UTC) | Overlap / edit |
| `FX-004` | DB seed | Shift `W001` Tue 22:00 → Wed 06:00; shift Mon 00:00 next week | Day boundaries |
| `FX-005` | DB seed | Inactive worker `W009` with a past shift | Deactivated row |
| `FX-006` | config | `steelsight.time-zone=Asia/Ho_Chi_Minh` | Site time |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```java
@SpringBootTest @AutoConfigureMockMvc @Import(TestDatabase.class) @Transactional
class SchedulingNewCasesTest extends PageTestSupport {
    Long leader, otherLeader, worker, outsider, zoneA;

    @BeforeEach
    void seed() {                                   // FX-001 / FX-002, fresh per test, rolled back after
        leader = userWithRole("M001", "Leader One", "MANAGER");
        otherLeader = userWithRole("M002", "Leader Two", "MANAGER");
        worker = userWithRole("W001", "Worker One", "WORKER");
        outsider = userWithRole("W003", "Worker Three", "WORKER");
        joinTeam(worker, team("Tổ 1", leader));
        joinTeam(outsider, team("Tổ 2", otherLeader));
        zoneA = zone("Zone A");
    }
}
```

---

### SCH-TC-006 — A shift can't be given to another team's worker

**Severity:** `CRITICAL`
**OWASP:** `A01:2021 — Broken Access Control`
**CWE:** `CWE-639 — Authorization Bypass Through User-Controlled Key`
**Feature Under Test:** `POST /shifts` → `ShiftService.create` → `can_assign_shift`
**Test File:** `ManagerPagesIntegrationTest.java` — `aShiftCannotBeGivenToAnotherTeam`; `ServiceIntegrationTest.java` — `managerSchedulesOnlyTheirTeamAndAdminSchedulesAnyone`, `managerCannotMoveAShiftToAnotherTeamsWorker`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `BR-SCH-003`, `ADR-001`

**Test Steps (Attack Simulation):**
1. Leader M001 posts a shift with `workerId` = W003 (team 2)
2. Leader edits an own-team shift and changes `workerId` to W003

**Expected Result (PASS):** `403`; no shift row for W003
**Expected Result (FAIL):** shift created/moved for another team → scope bypass
**Current Status:** 🟢L

---

### SCH-TC-008 — Overlapping shift is a rule error; cancelling frees the time

**Severity:** `HIGH`
**Feature Under Test:** `ShiftService.create/cancel`, `EXCLUDE USING gist … WHERE status <> 'CANCELLED'`
**Test File:** `ServiceIntegrationTest.java` — `overlappingShiftIsARuleErrorAndCancellingFreesTheTime`; `ShiftOverlapPageTest.java` — `anOverlappingShiftShowsTheFormAgainWithTheProblem`; `RepositoryIntegrationTest.java` — `shiftGetsItsComputedDurationAndOverlapsAreRefused`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-005`
**Oracle Source:** `BR-SCH-001`, `ADR-018`

**Test Steps:**
1. FX-003; create W001 12:00–20:00 the same day
2. Cancel the first; create again

**Expected Result (PASS):** first attempt `BusinessRuleException("This worker already has a shift at that time")` (page: 400 + form); after cancel, saved; `duration_hours` is filled by the database
**Expected Result (FAIL):** two live overlapping shifts, or a 500
**Current Status:** 🟢L

---

### SCH-TC-004 — Overnight shift belongs to its start day; a midnight one to the next week

**Severity:** `MEDIUM`
**Feature Under Test:** `ShiftGrid.rows`, `ShiftRepository.findTeamShiftsStartingIn` (`[from, to)`)
**Test File:** `ManagerPagesIntegrationTest.java` — `anOvernightShiftBelongsToTheDayItStartsAndAMidnightOneToTheNextWeek`, `aShiftAppearsUnderTheDayItStarts`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-002`
**Oracle Source:** `BR-SCH-005`, `ADR-018`

**Expected Result (PASS):** Tue 22:00–Wed 06:00 is in Tuesday's cell only; Mon 00:00 next week isn't on this week's grid
**Expected Result (FAIL):** shift shown on two days or two weeks (double hours)
**Current Status:** 🟢L

---

### SCH-TC-011 — Editing saves; a stale form is refused

**Severity:** `HIGH`
**Feature Under Test:** `POST /shifts/{id}` → `ShiftService.update(…, expectedVersion)`
**Test File:** `ManagerPagesIntegrationTest.java` — `editingAShiftSavesAndAStaleFormIsRefused`; `ServiceIntegrationTest.java` — `finishedShiftIsLockedAndStaleEditsFail`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-007`
**Oracle Source:** `ADR-006`, `BR-SCH-004`

**Expected Result (PASS):** current version saves; old version → `409`; COMPLETED/CANCELLED → "Only a scheduled shift can be changed"
**Expected Result (FAIL):** silent overwrite of someone else's change
**Current Status:** 🟢L

---

### SCH-TC-015 — Admin sets up teams with one leader each; the database refuses a second team too

**Severity:** `HIGH`
**Feature Under Test:** `POST /teams`, `/teams/{id}/leader`; `uq_team_manager`
**Test File:** `TeamsTicketsAndReportTest.java` — `adminSetsUpTeamsWithOneLeaderEach`, `theDatabaseAlsoRefusesALeaderForTwoTeams`, `onlyAdminsSetUpTeams`; `ServiceIntegrationTest.java` — `teamManagerMustBeAManager`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-010`
**Oracle Source:** `ADR-017`, `BR-SCH-006`

**Expected Result (PASS):** a busy leader → "This manager already leads another team"; non-MANAGER → "A team's manager must have the MANAGER role"; direct SQL insert of a second team for the same manager → unique violation; non-admins → 403
**Expected Result (FAIL):** a leader with two teams (report scope ambiguous)
**Current Status:** 🟢L

---

### SCH-TC-018 — Only the team's leader or an admin changes the late rule; it must be 0–240

**Severity:** `MEDIUM`
**Feature Under Test:** `POST /teams/{id}/late-after` → `TeamService.setLateAfterMinutes`
**Test File:** `ManagerPagesIntegrationTest.java` — `onlyTheTeamsManagerOrAnAdminCanChangeTheLateRule`, `theLateRuleMustBeASensibleNumber`, `attendanceTagsFollowTheTeamsRuleWhichTheManagerCanChange`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-011`
**Oracle Source:** `BR-SCH-008`, `ADR-012`

**Expected Result (PASS):** another leader → 403; −1 / 241 / empty → "Enter between 0 and 240 minutes"; 0 and 240 accepted; tags change at once
**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### SCH-TC-030 — A missing team and another leader's team get the same answer

**Severity:** `LOW`
**OWASP:** `A01:2021`
**CWE:** `CWE-203 — Observable Discrepancy`
**Feature Under Test:** `TeamService.setLateAfterMinutes` (loads the team before checking permission — gap G8)
**Test File:** *(new)* `ManagerPagesIntegrationTest.java`
**TDD Phase:** 🔴 RED — **expected to fail today**

**Test Steps (Attack Simulation):**
1. As leader M001, POST `/teams/{otherTeamId}/late-after` and `/teams/999999/late-after`
2. Compare statuses

**Expected Result (PASS = hệ thống an toàn):** both `403` (permission checked before lookup)
**Expected Result (FAIL = lỗ hổng tồn tại):** `403` vs `404` → team ids can be enumerated
**Current Status:** 🔴 Not written
**Implementation Note:** check `SHIFT_UPDATE_TEAM`/`USER_MANAGE` before `teams.require(teamId)`, or a SQL `can_edit_team()`.

---

### SCH-TC-031 — Tampered or malformed `week` parameter

**Severity:** `LOW`
**CWE:** `CWE-20 — Improper Input Validation`
**Feature Under Test:** `GET /shifts?week=…`, `GET /timetable?week=…`
**Test File:** `WorkerAccessPagesTest.java` — `theTimetableMovesByWeekAndABadWeekIsNotFound`; `HsePagesIntegrationTest.java` — `oddPageNumbersDoNotBreakTheList`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** `week=not-a-date` → 404 page (`MethodArgumentTypeMismatchException`), never 500; negative page numbers clamp to 0
**Current Status:** 🟢L

---

### INTEGRATION TEST CASES

---

### SCH-TC-INT-001 — Empty grid cell → prefilled form → saved shift → back on the right week

**Severity:** `HIGH`
**Feature Under Test:** Full flow: `GET /shifts/new?workerId&date` → `POST /shifts` → `GET /shifts?week=`
**Test File:** `ManagerPagesIntegrationTest.java` — `anEmptyCellOpensTheFormWithItsWorkerAndDate`, `newShiftOffersOnlyYourTeamAndTakesSiteLocalTime`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`, `TC-COND-002`

**Preconditions:** Testcontainers PostgreSQL; FX-001, FX-002

**Test Steps:**
1. Open the cell for W001 on 2026-11-10 → form shows 08:00–17:00
2. Post it
3. Read the row

**Expected Result (PASS):** worker list = own team only; `start_at` = `2026-11-10 01:00:00+00` (08:00 site time); redirect to `/shifts?week=2026-11-10`

**DB Assertion:**
```java
OffsetDateTime start = jdbc.queryForObject("SELECT start_at FROM shift WHERE user_id = ?", OffsetDateTime.class, worker);
assertThat(start.toInstant()).isEqualTo(Instant.parse("2026-11-10T01:00:00Z"));
```

**Current Status:** 🟢L

---

### TC catalogue — remaining existing tests

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| SCH-TC-001 | `ManagerPagesIntegrationTest` — `gridShowsOnlyYourTeamAndNavigates` | COND-001 | HIGH | 🟢L |
| SCH-TC-002 | `ManagerPagesIntegrationTest` — `aLongGridIsPagedAndKeepsItsWeek`, `aManagerWithNoPeopleSeesAMessageInsteadOfAnEmptyGrid` | COND-001 | LOW | 🟢L |
| SCH-TC-003 | `ManagerPagesIntegrationTest` — `aShiftAppearsUnderTheDayItStarts` | COND-002 | MEDIUM | 🟢L |
| SCH-TC-005 | `ManagerPagesIntegrationTest` — `gridTotalsHoursWithoutCancelledShiftsAndMarksThem` | COND-003 | MEDIUM | 🟢L |
| SCH-TC-007 | `ManagerPagesIntegrationTest` — `aDeactivatedWorkerKeepsTheirRowButCannotGetNewShifts` | COND-004 | MEDIUM | 🟢L |
| SCH-TC-009 | `RepositoryIntegrationTest` — `managerEditsOnlyTheirTeamsShiftsAndAdminEditsAny` | COND-004 | HIGH | 🟢L |
| SCH-TC-010 | `ManagerPagesIntegrationTest` — `badShiftFormsShowTheFormAgainWithTheProblem` | COND-006 | MEDIUM | 🟢L |
| SCH-TC-012 | `ManagerPagesIntegrationTest` — `aFinishedShiftIsShownButCanNoLongerBeChanged` | COND-007 | MEDIUM | 🟢L |
| SCH-TC-013 | `ManagerPagesIntegrationTest` — `anotherManagerCannotOpenOrChangeYourShifts` | COND-008 | HIGH | 🟢L |
| SCH-TC-014 | `PagesIntegrationTest` — `timetableShowsOnlyYourOwnShifts`, `anEmptyTimetableSaysSoInsteadOfFailing`, `homeShowsTheWorkersNextShift` | COND-009 | MEDIUM | 🟢L |
| SCH-TC-016 | `ManagerPagesIntegrationTest` — `workersAndHseCannotManageShiftsOrTeams`, `teamShowsYourPeopleOnly`, `teamIsPaged` | COND-010 | HIGH | 🟢L |
| SCH-TC-017 | `WorkerAccessPagesTest` — `managerCanPlanTheirOwnWorkersShiftsButNotEditThem` | COND-004 | MEDIUM | 🟢L |
| SCH-TC-019 | `AccountPagesTest` — `aManagerStillPlansShiftsAndSetsTheLateRule` | COND-011 | MEDIUM | 🟢L |
| SCH-TC-020 | `ServiceIntegrationTest` — `shiftReadsFollowPermissions` | COND-012 | HIGH | 🟢L |
| SCH-TC-021 | `ManagerPagesIntegrationTest` — `adminCanOpenEveryManagementPage` | COND-001 | MEDIUM | 🟢L |
| SCH-TC-032 | *(new)* Deleting a team with live members is refused; deleted members are detached | COND-010 | MEDIUM | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `SCH-TC-001…021` | `ManagerPagesIntegrationTest`, `ServiceIntegrationTest`, `RepositoryIntegrationTest`, `ShiftOverlapPageTest`, `TeamsTicketsAndReportTest`, `PagesIntegrationTest`, `WorkerAccessPagesTest`, `AccountPagesTest` | `[ ]` not evidenced | `94a2c3c` (last local run) | Legacy `BETWEEN` methods in `ShiftRepository` unused by pages — candidates to remove |
| `SCH-TC-030…032` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```java
public Shift create(Long actorId, ShiftDetails details) {
    throw new UnsupportedOperationException("Not implemented — Red Phase stub");
}
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `SCH-TC-006` | `throw` in `create` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | ☐ Tautology (403 from the stub's 500?) ☐ Shared state ☐ Hallucinated import |
| `SCH-TC-030` | current code | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `SCH-TC-032` | `throw` in `TeamService.delete` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |

> ⚠️ For "is refused" TCs (e.g. SCH-TC-006), a throwing stub yields 500, not 403 — the assertion must check the **exact** status 403, or the test is green-from-birth.

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3)
- Log file: `target/surefire-reports/`

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-SCH-IMP-001` approved
- [ ] Logic Issues confirmed (L4 needs a decision)
- [ ] Fixtures via `PageTestSupport` (`team`, `zone`, `shift`, `joinTeam`)

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` green (Docker), no `@Disabled`
- [ ] Coverage ≥ 80% for new files — **no JaCoCo configured**; add or waive
- [ ] No `ZoneId.systemDefault()` in new code (`grep -rn systemDefault src/main/java` → none)
- [ ] Overlap message, not 500, on the page (`ShiftOverlapPageTest`)

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)** for new TCs
- [ ] **Contract Existence:** `sh ./mvnw -q -o test-compile` → no "cannot find symbol"
- [ ] **Props Isolation:** new classes `@Transactional` or `@AfterEach` cleanup
- [ ] **Oracle Source** cited in every TC

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable
- A change to the one-leader rule (needs ADR-017 update)
- CI broken by another change

---

## 7. Rollback Plan

```bash
# Testcontainers DB is throwaway. Local dev DB: recreate, Flyway rebuilds.
git checkout -- src/main/java/orochi/steelsight/scheduling src/main/java/orochi/steelsight/web/ShiftController.java
# Gap G8 stays OPEN in 02_design/MODULE-MAP.md §6
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ not verified; risk on "refused" TCs noted in §5.1 | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ SCH-TC-030 assumes "same answer" policy — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ none | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☑ all exist at `f005861` | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-002` (risk) | `SCH-TC-006`, `013`, `016` | A throwing stub could satisfy a loose "not 2xx" assertion | Confirm assertions use exact `status().isForbidden()` | ☐ |
| `AP-AI-003` | `SCH-TC-030` | 404-vs-403 policy not in an ADR | Add to ADR-001 consequences or a new ADR | ☐ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
