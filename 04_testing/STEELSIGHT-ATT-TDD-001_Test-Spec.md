# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-ATT-TDD-001`
**Version:** `1.0`
**Date:** `2026-11-06`
**Status:** `Draft`
**Standard:** ISO/IEC/IEEE 29119-3:2021 — Software Testing Part 3: Test Documentation
**Author:** `Claude Code (Opus 5.5) — documentation, from the existing test suite at commit f005861`
**Reviewed by:** `[ ] Tech Lead — Pending`
**DPO Sign-off:** `[ ] Pending` *(mandatory — biometric data)*
**Approved by:** `[ ] Pending`
**Classification:** `Internal — Confidential`

**References:**
- `02_design/MODULE-MAP.md` — BR / US catalog
- `03_implement/STEELSIGHT-ATT-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-004`, `ADR-005`, `ADR-006`, `ADR-011`, `ADR-012`, `ADR-013`
- `Luật 91/2025/QH15`, `NĐ 356/2025` — sensitive (biometric) data

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `sh ./mvnw verify` chưa xanh. Synthetic data; embedding vectors in tests are fake numbers, never from a real face.
>
> **Retrospective note & status legend:** see `STEELSIGHT-IAM-TDD-001`. 🟢V = re-run here on `f005861` · 🟢L = last local run 2026-11-06 15:31 (code of `94a2c3c`), not re-run (no Docker) · 🔴 = not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Attendance |

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
| **Feature / Gap ID** | `ATT` (existing) · gaps `G1` (no shift link), `G7` (auto-close only by detection), `G11` (no consent record), `G12` (`CHECKIN_READ_OWN` unused) |
| **Module** | `ATT — Attendance (Site presence)` |
| **Spec gốc** | `STEELSIGHT-ATT-IMP-001` |
| **Priority** | 🔴 P0 |
| **Sprint** | `Not defined — work done 2026-11-04 → 2026-11-06` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` + `Sensitive-PII` (embeddings) |
| **Compliance Scope** | `Luật 91/2025/QH15`, `NĐ 356/2025` — DPO pending |
| **Upstream Dependencies** | `IAM`, `SCH`, `PPE` (camera, vest link), detection service (DB writer) |
| **Downstream Consumers** | `RPT`, dashboard roster, worker page, detection (reads READY embeddings) |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` (`chatlog/ai-log.md` 2026-11-04 13:57, 21:13, 21:37) |
| **Constraint Source** | `STEELSIGHT-ATT-IMP-001 §17`, `ADR-011/012/013` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | V1 auto-close: shift end + 2 h, or check-in + 16 h | V7: after the site-local day ends, at shift end or midnight; cancelled shift = none | AutoClose TCs assert V7 behaviour (ATT-TC-020…024) |
| L2 | Late tag "if after start" | Grace period per team with make-up capped at the grace (`requiredCheckOut = end + min(late, grace)`) | Rules TCs use the exact 08:10→17:10, 08:15→17:15, 08:40→late cases (ATT-TC-001…013) |
| L3 | Manual check-in can name a shift (`ManualCheckIn.shiftId`) | The page always sends `null` (gap G1) → manual visits get no tags | ATT-TC-040 🔴 states the expected linking; today's tests pass `shiftId` only at service level |
| L4 | Workers can see their own visits (`CHECKIN_READ_OWN`) | No page shows them (gap G12) | ATT-TC-041 🔴 |
| L5 | `@Version` catches stale edits | Detection updates with plain SQL → needs `bump_check_in_version()` | ATT-TC-031 asserts a plain-SQL update makes the form stale |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
ATT bao gồm các layer:
├── Domain (pure logic — no deps)   AttendanceRules → VisitStatus     (unit, 13 cases)
├── Services                        CheckInService                     (Testcontainers PostgreSQL)
├── Controller / pages              AttendanceController               (MockMvc)
├── Database rules                  uq_check_in_open, CHECKs, check_in_same_worker, bump_check_in_version,
│                                   close_stale_check_ins, queueForEnrolment, trg_user_erase_embedding
└── Detection-role contract         grants exercised by schema_checks.sql (SET ROLE steelsight_detection)
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-MGR-003, US-HSE-004, US-SYS-002, US-ADM-003 | Team view, manual entries, detection writes, enrolment |
| `ADR-011`, `ADR-012`, `ADR-013`, `ADR-005`, `ADR-006` | Queue; tags on read; auto-close; idempotency; versioning |
| `BR-ATT-001` … `BR-ATT-010` | Rules |
| Luật 91/2025/QH15 | Erasure of biometric data on deletion |
| `docs/user-guide.md` §4.3 | Worked example of the grace period |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | On-time, early arrival, late within/past grace, early leave | `AttendanceRules.assess` | ATT-TC-001…008 |
| TC-COND-002 | Team grace overrides the default | `graceFor` | ATT-TC-009 |
| TC-COND-003 | Seconds ignored; still on site; estimated check-out; no/cancelled shift | `assess` | ATT-TC-010…013 |
| TC-COND-004 | Auto-close by day | `close_stale_check_ins` | ATT-TC-020…024 |
| TC-COND-005 | One open visit; manual needs recorder; same-worker shift/vest | DB constraints/trigger | ATT-TC-025…028 |
| TC-COND-006 | Manual add/correct by HSE only; validation | `CheckInService`, `AttendanceController` | ATT-TC-029, 030, 032, 033 |
| TC-COND-007 | Stale correction, incl. plain-SQL change | `bump_check_in_version` | ATT-TC-031 |
| TC-COND-008 | Visibility own/team/all | `CheckInService.list*` | ATT-TC-034, 035 |
| TC-COND-009 | Enrolment queue resets on new photo; erased on delete | `queueForEnrolment`, trigger | ATT-TC-036…038 |
| TC-COND-010 | Detection idempotency and limits | `uq_check_in_event`, grants | ATT-TC-039 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Boundary Value Analysis | Arrival at grace −1, = grace, grace +1 minute; seconds within a minute | The rule hinges on these boundaries |
| Decision Table | (late?, left before required?, auto-closed?, on site?) → tags | Four interacting conditions |
| State Transition Testing | Visit ON_SITE → CLOSED / AUTO_CLOSED → CLOSED; enrolment PENDING → READY/FAILED → PENDING | Final and estimate states |
| Error Guessing | Detection writes with plain SQL; another worker's shift/vest id | Second-writer hazards |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | In-memory | `new AttendanceRules(15)`; `Shift` 08:00–17:00 on a fixed date; `CheckIn` built per test | Unit rules |
| `FX-002` | In-memory | `Team.lateAfterMinutes = 5` | Team grace |
| `FX-003` | DB seed | HSE `H001`, manager `M001` leading worker `W001`, outsider `W003` | Scope |
| `FX-004` | DB seed | Open visit for W001 with shift ended yesterday / today / none / cancelled | Auto-close |
| `FX-005` | DB seed | `face_embedding` READY with a fake 512-dim vector `'[0.1, …]'` and `model_name` | Reset on new photo |
| `FX-006` | DB role | `SET ROLE steelsight_detection` inside `schema_checks.sql` | Detection contract |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```java
// Unit — the existing pattern in AttendanceRulesTest: one rules object, and new entities built per call
class AttendanceRulesTest {
    private final AttendanceRules rules = new AttendanceRules(15);     // FX-001 (JUnit: new instance per test)

    private VisitStatus assess(String in, String out) {
        return rules.assess(visit(at(in), out == null ? null : at(out), shift()));
    }
    private CheckIn visit(OffsetDateTime in, OffsetDateTime out, Shift shift) {
        CheckIn visit = new CheckIn();
        visit.setUser(new AppUser());              // no team → default grace (15)
        visit.setCheckInAt(in); visit.setCheckOutAt(out); visit.setShift(shift);
        return visit;
    }
    private Shift shift() {                        // 08:00–17:00 on 2026-11-02, +07:00
        Shift shift = new Shift(); shift.setStartAt(at("08:00")); shift.setEndAt(at("17:00"));
        return shift;
    }
    private OffsetDateTime at(String time) { return OffsetDateTime.parse("2026-11-02T" + time + ":00+07:00"); }
}
// Integration: @SpringBootTest @Import(TestDatabase.class) @Transactional + @BeforeEach seed() (see IAM spec)
```

---

### ATT-TC-003 — Arriving 08:15 means working until 17:15 without a late tag

**Severity:** `HIGH` *(pay-relevant)*
**Feature Under Test:** `AttendanceRules.assess()`
**Test File:** `AttendanceRulesTest.java` — `comingAt0815MeansWorkingUntil1715WithoutALateTag`, `lateTimeWithinTheGracePeriodIsMadeUpMinuteForMinute`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-ATT-004`; worked example in `AttendanceRules` Javadoc and `docs/user-guide.md` §4.3

**Test Steps:** FX-001; check-in 08:15, check-out 17:15
**Expected Result (PASS):** `minutesLate = 0`, `requiredCheckOut = 17:15`, `isOnTime() = true`
**Expected Result (FAIL):** late tag at 08:15, or required check-out 17:00
**Current Status:** 🟢V

---

### ATT-TC-005 — The required check-out stops at the grace period however late the arrival

**Severity:** `HIGH`
**Feature Under Test:** `AttendanceRules.assess()`
**Test File:** `AttendanceRulesTest.java` — `theRequiredCheckOutStopsAtTheGracePeriodHoweverLateTheArrival`, `oneMinutePastTheGracePeriodIsLate`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `ADR-012 §Context` (08:40 with 15 min grace → late, leave from 17:15)

**Expected Result (PASS):** 08:40 → `minutesLate = 40`, `requiredCheckOut = 17:15`; 08:16 → late by 16
**Expected Result (FAIL):** required check-out 17:40 (uncapped make-up)
**Current Status:** 🟢V

---

### ATT-TC-012 — An estimated check-out is not judged early

**Severity:** `HIGH`
**Feature Under Test:** `AttendanceRules.assess()`, `VisitStatus.isMissingCheckOut()`
**Test File:** `AttendanceRulesTest.java` — `anEstimatedCheckOutIsNotJudgedAsEarly`, `someoneStillOnSiteIsNotEarlyYetButCanBeLate`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-003`
**Oracle Source:** `BR-ATT-005`, `ADR-013`

**Expected Result (PASS):** auto-closed visit → `minutesEarly = 0`, `isMissingCheckOut() = true`; open visit → may be late, never early
**Expected Result (FAIL):** a worker penalised for a check-out the system guessed
**Current Status:** 🟢V

---

### ATT-TC-021 — A visit to a shift that ended yesterday closes at the shift's end

**Severity:** `HIGH`
**Feature Under Test:** `close_stale_check_ins('Asia/Ho_Chi_Minh')` (V7)
**Test File:** `AutoCloseCheckInTest.java` — `aVisitToAShiftThatEndedYesterdayClosesAtTheShiftsEnd`, `aVisitToAShiftThatEndedTodayStaysOpen`, `aVisitWithoutAShiftClosesAtMidnightOnceItsDayHasPassed`, `aCheckInAfterTheShiftEndedStillClosesAfterItStarted`, `aCancelledShiftCountsAsNoShift`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `BR-ATT-006`, `ADR-013 §Decision`

**Test Steps:** FX-004 rows; run the function; read rows
**Expected Result (PASS):** yesterday's → `check_out_at = shift end`, `auto_closed = true`; today's stays open; no shift → next site midnight; late check-in → `check_in + 1 min` floor; cancelled → treated as no shift
**Expected Result (FAIL):** a worker blocked from checking in next morning (open visit kept), or overtime cut off today
**Current Status:** 🟢L

---

### ATT-TC-031 — A stale correction fails even if only plain SQL changed the visit

**Severity:** `HIGH`
**Feature Under Test:** `CheckInService.correct(…, expectedVersion)` + `bump_check_in_version()`
**Test File:** `ServiceIntegrationTest.java` — `aStaleCorrectionFailsEvenIfOnlyPlainSqlChangedTheVisit`, `correctionClosesAnAutoClosedVisitProperly`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-007`
**Oracle Source:** `ADR-006` (bump trigger), `BR-ATT-007`

**Test Steps:**
1. HSE records a manual visit; keep its version (form)
2. Native `UPDATE check_in SET check_out_at = now()` (what detection does)
3. `correct(…, versionInForm)`

**Expected Result (PASS):** `StaleEditException`; a fresh correction clears `auto_closed`
**Expected Result (FAIL):** HSE overwrites detection's check-out silently
**Current Status:** 🟢L

---

### ATT-TC-036 — A new photo resets enrolment and clears the old vector

**Severity:** `CRITICAL`
**Legal:** `Luật 91/2025/QH15 — sensitive data accuracy`
**Feature Under Test:** `FaceEmbeddingRepository.queueForEnrolment()`
**Test File:** `RepositoryIntegrationTest.java` — `newPhotoResetsEnrolmentAndClearsTheOldVector`; `ServiceIntegrationTest.java` — `newPhotoIsSavedAndQueuedTogether`, `onlyAnAdminHandlesFacePhotos`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-009`
**Oracle Source:** `ADR-011 §Decision`, `BR-ATT-009`

**Test Steps:** FX-005 READY row; queue a new photo
**Expected Result (PASS):** `status = PENDING`, `embedding IS NULL`, `model_name IS NULL`, `source_file_path` = new; still one row per user
**Expected Result (FAIL):** worker still matched against the replaced face
**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### ATT-TC-033 — Managers can't record or correct visits

**Severity:** `HIGH`
**OWASP:** `A01:2021 — Broken Access Control`
**CWE:** `CWE-285`
**Feature Under Test:** `CheckInService.recordManual/correct` (`CHECKIN_EDIT`)
**Test File:** `ServiceIntegrationTest.java` — `hseRecordsManualCheckInsAndManagersCannot`; `ManagerPagesIntegrationTest.java` — `attendanceShowsYourTeamReadOnly`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** manager → `AccessDeniedException` / 403; manager's list shows only own team, no edit links
**Expected Result (FAIL):** a leader edits their team's hours (pay fraud risk)
**Current Status:** 🟢L

---

### ATT-TC-039 — The detection role can do its job and nothing else

**Severity:** `CRITICAL`
**OWASP:** `A01:2021`
**CWE:** `CWE-250 — Execution with Unnecessary Privileges`
**Legal:** `Luật 91/2025/QH15 — least privilege for sensitive data`
**Feature Under Test:** grants and RLS policies for `steelsight_detection`
**Test File:** `SchemaChecksSqlTest.java` — `schemaRulesHold` (runs `src/test/resources/db/schema_checks.sql`)
**TDD Phase:** 🟢 GREEN

**Test Steps (Attack Simulation):**
1. `SET ROLE steelsight_detection`
2. Insert a check-in and a vest link with an `event_id`; retry the same `event_id`
3. Try `SELECT password_hash`, `UPDATE violation SET dispute_status`, `SELECT FROM ticket`

**Expected Result (PASS = hệ thống an toàn):** inserts work; retry changes nothing; the three forbidden operations raise permission errors; script ends with `ALL CHECKS PASSED`
**Expected Result (FAIL = lỗ hổng tồn tại):** detection reads password hashes or changes disputes
**Current Status:** 🟢L

---

### ATT-TC-042 — A new detection-facing column without a grant/policy is caught

**Severity:** `MEDIUM`
**CWE:** `CWE-1188 — Insecure Default Initialization`
**Feature Under Test:** migration review (ADR-004 constraint 1)
**Test File:** *(new)* `RepositoryIntegrationTest.java`
**TDD Phase:** 🔴 RED

**Test Steps:** list every column `steelsight_detection` has privileges on (`information_schema.column_privileges`) and compare with an allow-list in the test
**Expected Result (PASS):** exact match with the allow-list from ATT/PPE TDS §5.2
**Expected Result (FAIL):** an unexpected grant (privilege creep) or a missing one (detection breaks)
**Current Status:** 🔴 Not written

---

### INTEGRATION TEST CASES

---

### ATT-TC-INT-001 — HSE adds and corrects a check-in through the pages

**Severity:** `HIGH`
**Feature Under Test:** Full flow: `GET /attendance/new` → `POST /attendance` → `GET /attendance/{id}/edit` → `POST /attendance/{id}`
**Test File:** `HsePagesIntegrationTest.java` — `anOfficerCanAddAndCorrectACheckIn`, `aManualCheckInNeedsAWorkerAndATime`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-006`

**Preconditions:** Testcontainers PostgreSQL; FX-003

**Test Steps:**
1. HSE posts worker + site-local times
2. Opens edit, changes check-out, posts with the form's version
3. Posts a form with no worker / no time

**Expected Result (PASS):** row `method='MANUAL'`, `recorded_by = HSE`; correction saved; missing fields → 400 with "Choose a worker" / "A check-in time is required"

**DB Assertion:**
```java
Map<String, Object> row = jdbc.queryForMap("SELECT method, recorded_by, auto_closed FROM check_in WHERE user_id = ?", worker);
assertThat(row.get("method")).isEqualTo("MANUAL");
assertThat(row.get("recorded_by")).isEqualTo(hse);
```

**Current Status:** 🟢L

---

### TC catalogue — remaining existing tests

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| ATT-TC-001 | `AttendanceRulesTest` — `arrivingAndLeavingOnTimeHasNoTags` | COND-001 | HIGH | 🟢V |
| ATT-TC-002 | `AttendanceRulesTest` — `arrivingEarlyDoesNotAllowLeavingEarly` | COND-001 | HIGH | 🟢V |
| ATT-TC-004 | `AttendanceRulesTest` — `leavingAtTheUsualTimeAfterComing15MinutesLateIsEarly` | COND-001 | HIGH | 🟢V |
| ATT-TC-006 | `AttendanceRulesTest` — `oneMinutePastTheGracePeriodIsLate` | COND-001 | HIGH | 🟢V |
| ATT-TC-008 | `AttendanceRulesTest` — `someoneTaggedLateWhoLeavesBeforeTheRequiredTimeIsAlsoEarly` | COND-001 | MEDIUM | 🟢V |
| ATT-TC-009 | `AttendanceRulesTest` — `theWorkersTeamSetsTheGracePeriod` | COND-002 | MEDIUM | 🟢V |
| ATT-TC-010 | `AttendanceRulesTest` — `secondsDoNotCount` | COND-003 | LOW | 🟢V |
| ATT-TC-011 | `AttendanceRulesTest` — `someoneStillOnSiteIsNotEarlyYetButCanBeLate` | COND-003 | MEDIUM | 🟢V |
| ATT-TC-013 | `AttendanceRulesTest` — `aVisitWithoutAShiftOrWithACancelledOneHasNoTags` | COND-003 | MEDIUM | 🟢V |
| ATT-TC-020 | `AutoCloseCheckInTest` — `aVisitToAShiftThatEndedTodayStaysOpen` | COND-004 | MEDIUM | 🟢L |
| ATT-TC-022 | `AutoCloseCheckInTest` — `aVisitWithoutAShiftClosesAtMidnightOnceItsDayHasPassed` | COND-004 | MEDIUM | 🟢L |
| ATT-TC-023 | `AutoCloseCheckInTest` — `aCheckInAfterTheShiftEndedStillClosesAfterItStarted` | COND-004 | MEDIUM | 🟢L |
| ATT-TC-024 | `AutoCloseCheckInTest` — `aCancelledShiftCountsAsNoShift` | COND-004 | MEDIUM | 🟢L |
| ATT-TC-025 | `RepositoryIntegrationTest` — `openVisitIsFoundAndAWorkerCannotCheckInTwice` | COND-005 | HIGH | 🟢L |
| ATT-TC-026 | `RepositoryIntegrationTest` — `manualCheckInMustSayWhoRecordedIt` | COND-005 | MEDIUM | 🟢L |
| ATT-TC-027 | `RepositoryIntegrationTest` — `visitCannotPointAtAnotherWorkersShift` | COND-005 | HIGH | 🟢L |
| ATT-TC-028 | `RepositoryIntegrationTest` — `visitCannotPointAtAnotherWorkersVest` | COND-005 | HIGH | 🟢L |
| ATT-TC-029 | `ServiceIntegrationTest` — `manualCheckInRejectsAnotherWorkersShiftAndBadTimes` | COND-006 | HIGH | 🟢L |
| ATT-TC-030 | `HsePagesIntegrationTest` — `aManualCheckInNeedsAWorkerAndATime` | COND-006 | MEDIUM | 🟢L |
| ATT-TC-032 | `ServiceIntegrationTest` — `correctionClosesAnAutoClosedVisitProperly` | COND-006 | MEDIUM | 🟢L |
| ATT-TC-034 | `ServiceIntegrationTest` — `checkInReadsFollowPermissions` | COND-008 | HIGH | 🟢L |
| ATT-TC-035 | `ManagerPagesIntegrationTest` — `attendanceTagsFollowTheTeamsRuleWhichTheManagerCanChange` | COND-008 | MEDIUM | 🟢L |
| ATT-TC-037 | `RepositoryIntegrationTest` — `softDeletingAWorkerErasesTheirEmbedding`; `ServiceIntegrationTest` — `softDeleteErasesTheEmbedding` | COND-009 | CRITICAL | 🟢L |
| ATT-TC-038 | `AccountPagesTest` — `adminCreatesAnAccountWithAFacePhotoThatIsQueuedForEnrolment` | COND-009 | HIGH | 🟢L |
| ATT-TC-040 | *(new)* A manual check-in links the worker's overlapping SCHEDULED shift so tags apply (gap G1) | COND-001 | HIGH | 🔴 |
| ATT-TC-041 | *(new)* A worker sees their own visits (`CHECKIN_READ_OWN`), or the permission is removed (gap G12) | COND-008 | LOW | 🔴 |
| ATT-TC-043 | *(new)* The ERP closes stale visits when detection hasn't (gap G7), e.g. on `/attendance` load | COND-004 | MEDIUM | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `ATT-TC-001…013` | `AttendanceRulesTest.java` | `[ ]` not evidenced | `f005861` (re-run 2026-11-06, 13/13) | — |
| `ATT-TC-020…039` | `AutoCloseCheckInTest`, `RepositoryIntegrationTest`, `ServiceIntegrationTest`, `HsePagesIntegrationTest`, `ManagerPagesIntegrationTest`, `AccountPagesTest`, `SchemaChecksSqlTest` | `[ ]` | `94a2c3c` (last local run) | `CheckInService.listOwn/listTeam/listOnSite` unused — remove or use (G12) |
| `ATT-TC-040…043` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```java
public VisitStatus assess(CheckIn visit) {
    throw new UnsupportedOperationException("Not implemented — Red Phase stub");
}
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `ATT-TC-001…013` | `throw` in `assess` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | ☐ Tautology ☐ Shared state ☐ Hallucinated import |
| `ATT-TC-040` | current code (`shiftId = null`) | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `ATT-TC-042` | add a stray `GRANT` in a scratch migration | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3)
- Log file: `target/surefire-reports/orochi.steelsight.AttendanceRulesTest.txt`

> `AttendanceRulesTest` is DB-free and fast; it is the cheapest place to back-fill the Red Gate.

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-ATT-IMP-001` approved
- [ ] **DPO sign-off** (biometric processing; consent gap G11)
- [ ] Logic Issues confirmed (L3, L4 need product decisions)
- [ ] Detection team agrees any change to grants or `close_stale_check_ins`

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` green (Docker), including `SchemaChecksSqlTest` (`ALL CHECKS PASSED`)
- [ ] Coverage ≥ 80% for new files — **no JaCoCo**; add or waive
- [ ] No embedding values in logs or pages (`grep -rn "embedding" src/main/resources/templates` → none)
- [ ] `AttendanceRulesTest` 13/13 green

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)**
- [ ] **Contract Existence:** `sh ./mvnw -q -o test-compile` clean
- [ ] **Props Isolation:** new integration classes `@Transactional`; `AutoCloseCheckInTest` style for SQL-function tests
- [ ] **Oracle Source** cited

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable
- Detection service contract change pending
- DPO objection to biometric processing

---

## 7. Rollback Plan

```bash
# Testcontainers DB is throwaway. Local dev DB: recreate; Flyway rebuilds.
git checkout -- src/main/java/orochi/steelsight/attendance src/main/resources/db/migration
# Never remove an applied migration from a shared database; write a forward fix.
# Gaps G1, G7, G11, G12 stay OPEN in 02_design/MODULE-MAP.md §6
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ not verified | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ ATT-TC-040, 043 assume decisions not yet in an ADR — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ none; rules tested on `AttendanceRules` directly | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☑ all exist at `f005861` | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-003` | `ATT-TC-040` | Shift linking on manual entry has no ADR | Write ADR (linking rule: overlap? closest start?) before the test | ☐ |
| `AP-AI-003` | `ATT-TC-043` | ERP-side auto-close contradicts ADR-013 constraint 1 | Amend ADR-013 first, or drop the TC | ☐ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
