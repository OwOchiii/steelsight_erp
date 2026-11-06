# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-RPT-TDD-001`
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
- `03_implement/STEELSIGHT-RPT-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-012`, `ADR-014`, `ADR-017`, `ADR-021`
- `Luật 91/2025/QH15` — personal data exported to accounting

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `sh ./mvnw verify` chưa xanh. Synthetic team "Team R" and workers `TTR-*`.
>
> **Retrospective note & status legend:** see `STEELSIGHT-IAM-TDD-001`. 🟢L = last local run 2026-11-06 15:31 (code of `94a2c3c`), not re-run (no Docker) · 🔴 = not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Reporting |

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
| **Feature / Gap ID** | `RPT` (existing) · gap `G1` (shift links) affects late/early figures |
| **Module** | `RPT — Reporting (Payroll input)` |
| **Spec gốc** | `STEELSIGHT-RPT-IMP-001` |
| **Priority** | 🔴 P0 (pay data) |
| **Sprint** | `Not defined — work done 2026-11-06` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` |
| **Compliance Scope** | `Luật 91/2025/QH15` — DPO pending |
| **Upstream Dependencies** | `IAM`, `SCH`, `ATT`, `PPE` |
| **Downstream Consumers** | Accounting (outside the system) |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` (commit `94a2c3c`, `chatlog/ai-log.md` 2026-11-06) |
| **Constraint Source** | `STEELSIGHT-RPT-IMP-001 §17`, `ADR-021` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | "Worked" = a visit linked to the shift | Detection doesn't link shifts yet; a linked **or overlapping** visit counts | RPT-TC-001 asserts worked/no-show by overlap |
| L2 | Score = current score | Score at the end of the chosen month (`safety_scores(period.to − 1 µs)`) | RPT-TC-001 asserts the past month's score (90) |
| L3 | Report covers everyone on the team | Only live WORKER-role members; the leader isn't a row | RPT-TC-001 asserts another team's worker (`TTR-W2`) is absent |
| L4 | Late/early from the visit's shift | Same, so manual visits without a shift have no tags (gap G1) | RPT-TC-030 🔴 |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
RPT bao gồm các layer:
├── Service                 MonthlyReportService.build (scope, period, SQL)     (Testcontainers)
├── Writer                  MonthlyReportWorkbook (fastexcel)                  (read back with fastexcel-reader)
└── Controller              ReportController (month parsing, headers, slug)    (MockMvc)
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-MGR-005, US-ADM-005 | Leader and admin exports |
| `ADR-021`, `ADR-017`, `ADR-014`, `ADR-012` | Format; scope; score; tags |
| `BR-RPT-001` … `BR-RPT-006` | Rules |
| `docs/user-guide.md` §4.6 | Columns accounting uses |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | Leader's month figures correct | `build`, `MonthlyReportWorkbook` | RPT-TC-001 |
| TC-COND-002 | Admin exports every worker | `scopeOf` ALL | RPT-TC-002 |
| TC-COND-003 | Only leaders/admins; leader without team; bad/future month | `scopeOf`, `period`, `monthOf` | RPT-TC-003 |
| TC-COND-004 | Language follows the reader | `MonthlyReportWorkbook` | RPT-TC-004 |
| TC-COND-005 | Headers: type, attachment name, no-store | `ReportController` | RPT-TC-001 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Decision Table | Violation (OVERTURNED/DISPUTED/NONE) × type → counted columns | Pay columns |
| Boundary Value Analysis | Month start/end in site time; running month; future month | Period rules |
| Equivalence Partitioning | Actor: worker / HSE / leader / leader-without-team / admin | Scope |
| Error Guessing | `month=not-a-month`, Vietnamese team names in file names | Parsing and header safety |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | DB seed | Team "Team R" led by `leader`; worker `TTR-W1` (Wanda Worker); worker `TTR-W2` on another team | Scope |
| `FX-002` | DB seed (`seedLastMonth()`) | Last month: one NO_VEST (counted), one NO_HARD_HAT OVERTURNED; 3 shifts (1 cancelled, 1 attended, 1 no-show); visits giving 7.5 h, 30 min late, 75 min early | Figures |
| `FX-003` | Cookie | `steelsight-language=vi` | Vietnamese file |
| `FX-004` | DB seed | HSE, worker, `freeManager` (MANAGER with no team) | Refusals |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```java
@SpringBootTest @AutoConfigureMockMvc @Import(TestDatabase.class) @Transactional
class ReportNewCasesTest extends PageTestSupport {
    Long leader, worker;
    @BeforeEach void seed() {                                   // FX-001, rolled back after each test
        leader = userWithRole("TTR-L", "Leader R", "MANAGER");
        worker = userWithRole("TTR-W1", "Wanda Worker", "WORKER");
        joinTeam(worker, team("Team R", leader));
    }
    // Read the file back for every assertion; never reuse bytes between tests.
}
```

---

### RPT-TC-001 — A leader exports their team's month with the right figures

**Severity:** `CRITICAL` *(pay)*
**Legal:** `Luật 91/2025/QH15 — accuracy of personal data`
**Feature Under Test:** `GET /team/report?month=` → `MonthlyReportService.build` → `MonthlyReportWorkbook.write`
**Test File:** `TeamsTicketsAndReportTest.java` — `aLeaderExportsTheirTeamsMonthWithTheRightFigures`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`, `TC-COND-005`
**Oracle Source:** `BR-RPT-001…006`, `BR-PPE-005`

**Preconditions:** FX-001, FX-002

**Test Steps:**
1. Leader downloads last month
2. Read the xlsx with fastexcel-reader
3. Check headers, sheets and Wanda's row

**Expected Result (PASS):**
- `Content-Type` spreadsheetml; `Content-Disposition` contains `steelsight-team-r-<YYYY-MM>.xlsx`; `Cache-Control: no-store`
- Sheets exactly: Summary, Violations, Attendance, No-shows
- Summary says "Team: Team R"; no `TTR-W2`
- Wanda: score 90, level "Safe", violations 1, no hard hat 0 (overturned), no vest 1, shifts scheduled 2 (cancelled left out), worked 1, no-shows 1, hours 7.5, late 1 time / 30 min, early 1 time / 75 min
- Violations sheet lists both violations (including the overturned one); Attendance 2 rows; No-shows 2 rows (header + 1)

**Expected Result (FAIL — dấu hiệu lỗi):**
- Overturned violation counted, cancelled shift counted, another team's worker in the file

**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### RPT-TC-003 — The report is only for leaders and admins

**Severity:** `CRITICAL`
**OWASP:** `A01:2021 — Broken Access Control`
**CWE:** `CWE-285`
**Feature Under Test:** `MonthlyReportService.scopeOf`, `ReportController.monthOf`
**Test File:** `TeamsTicketsAndReportTest.java` — `theReportIsOnlyForLeadersAndAdmins`
**TDD Phase:** 🟢 GREEN

**Test Steps (Attack Simulation):** worker and HSE request the report; a MANAGER with no team; a future month; `month=not-a-month`
**Expected Result (PASS = hệ thống an toàn):** worker 403; HSE 403; free manager 400 "You don't lead a team yet"; future month 400; bad month 400; the Team page shows "Download Excel" to the leader only
**Expected Result (FAIL = lỗ hổng tồn tại):** pay data for people outside the actor's team
**Current Status:** 🟢L

---

### RPT-TC-031 — A leader can't export another team by any parameter

**Severity:** `HIGH`
**CWE:** `CWE-639`
**Feature Under Test:** `GET /team/report` with extra params (`teamId`, `team`)
**Test File:** *(new)* `TeamsTicketsAndReportTest.java`
**TDD Phase:** 🔴 RED (expected to pass — pins behaviour)

**Expected Result (PASS):** unknown params ignored; still only the leader's own team
**Current Status:** 🔴 Not written

---

### INTEGRATION TEST CASES

---

### RPT-TC-INT-001 — An admin exports every worker; a Vietnamese reader gets Vietnamese sheets

**Severity:** `HIGH`
**Feature Under Test:** Full flow with `REPORT_EXPORT_ALL` and locale cookie
**Test File:** `TeamsTicketsAndReportTest.java` — `anAdminExportsEveryWorker`, `theReportIsInVietnameseForAVietnameseReader`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-002`, `TC-COND-004`

**Expected Result (PASS):** admin's file includes workers of every team; with `steelsight-language=vi` the sheet names include "Tổng hợp" and "Chấm công"

**DB Assertion:**
```java
// Not a DB assertion: the file is read back
assertThat(sheets(file).keySet()).contains("Tổng hợp", "Chấm công");
```

**Current Status:** 🟢L

---

### TC catalogue — remaining and new

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| RPT-TC-002 | `TeamsTicketsAndReportTest` — `anAdminExportsEveryWorker` | COND-002 | HIGH | 🟢L |
| RPT-TC-004 | `TeamsTicketsAndReportTest` — `theReportIsInVietnameseForAVietnameseReader` | COND-004 | MEDIUM | 🟢L |
| RPT-TC-030 | *(new)* A manual visit with no shift link still gets late/early figures once G1 is fixed | COND-001 | HIGH | 🔴 |
| RPT-TC-032 | *(new)* Running month counts up to now; a shift that hasn't ended isn't a no-show | COND-003 | MEDIUM | 🔴 |
| RPT-TC-033 | *(new)* A soft-deleted worker is absent from the Summary | COND-001 | MEDIUM | 🔴 |
| RPT-TC-034 | *(new)* The download writes one log line with the file name and no figures | COND-005 | LOW | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `RPT-TC-001…004` | `TeamsTicketsAndReportTest.java` | `[ ]` not evidenced | `94a2c3c` (last local run) | Column indexes in the test are positional; a header lookup would be sturdier |
| `RPT-TC-030…034` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```java
public Report build(Long actorId, YearMonth month) {
    throw new UnsupportedOperationException("Not implemented — Red Phase stub");
}
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `RPT-TC-001` | `throw` in `build` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | ☐ Tautology ☐ Shared state ☐ Hallucinated import |
| `RPT-TC-003` | `scopeOf` returning all workers | 🔴 FAIL (worker/HSE no longer 403) | ☐ FAIL ☐ PASS — not run | |

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3)
- Log file: `target/surefire-reports/orochi.steelsight.TeamsTicketsAndReportTest.txt`

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-RPT-IMP-001` approved
- [ ] Accounting confirms the column set
- [ ] DPO sign-off on exporting pay data

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` green (Docker)
- [ ] Coverage ≥ 80% for new files — **no JaCoCo**; add or waive
- [ ] Every sheet label has keys in both message files (`TranslationsTest`)
- [ ] The file is read back in the test, not only its headers

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)**
- [ ] **Contract Existence:** `sh ./mvnw -q -o test-compile` clean
- [ ] **Props Isolation:** `@Transactional`; seeded month computed relative to now (`seedLastMonth()`), not hard-coded
- [ ] **Oracle Source** cited

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable
- Accounting changes the columns
- Gap G1 fix in progress (figures will change)

---

## 7. Rollback Plan

```bash
git checkout -- src/main/java/orochi/steelsight/reporting src/main/java/orochi/steelsight/web/MonthlyReportWorkbook.java src/main/java/orochi/steelsight/web/ReportController.java
# Read-only module: no data to restore. Tell accounting to discard files from a bad build.
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ not verified | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ RPT-TC-030 depends on the G1 decision — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ none | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☑ all exist at `f005861` | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-003` | `RPT-TC-030` | Depends on how visits get linked to shifts (no ADR) | Write the linking ADR first (see ATT-TC-040) | ☐ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
