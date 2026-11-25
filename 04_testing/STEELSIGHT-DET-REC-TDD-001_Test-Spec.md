# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-DET-REC-TDD-001`
**Version:** `1.0`
**Date:** `2026-11-09`
**Status:** `Draft`
**Standard:** ISO/IEC/IEEE 29119-3:2021 — Software Testing Part 3: Test Documentation
**Author:** `Claude Code (Opus 5.5) — documentation, from the existing test suite`
**Reviewed by:** `[ ] Tech Lead — Pending`
**DPO Sign-off:** `[ ] Pending`
**Approved by:** `[ ] Pending`
**Classification:** `Internal — Confidential`

**References:**
- `02_design/DETECTION-MODULE-MAP.md` — BR / US catalog
- `03_implement/STEELSIGHT-DET-REC-IMP-001_TDS.md` — Technical Specification
- ADRs: ADR-108, ADR-112
- `docs/REQUIREMENTS_SPECIFICATION.md`

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `pytest` chưa xanh. Test data is synthetic: no real faces or names.
>
> **Retrospective note & status legend:** these tests were written with or after the code, so RED was never evidenced. 🟢V = re-run on 2026-11-09 after the package refactor (60 passed) · 🔴 = proposed, not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-09` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Records & Notifications |

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
| **Feature / Gap ID** | `REC` (existing) |
| **Module** | `REC — Records & Notifications` |
| **Spec gốc** | `STEELSIGHT-DET-REC-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` |
| **Compliance Scope** | Luật 91/2025/QH15 — worker photos are kept locally; Telegram and SMTP are outside processors |
| **Upstream Dependencies** | SAF, IDG, `EventBus`, SMTP, Telegram, central server |
| **Downstream Consumers** | WEB |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` |
| **Constraint Source** | `STEELSIGHT-DET-REC-IMP-001 §17` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Docs cite flat modules such as `steelsight/gate.py` | Code moved to packages ([ADR-101](../02_design/adr/ADR-101_packages-by-purpose-and-routed-web-layer.md)) | Tests import the new paths |
| L2 | Requirements doc said 38 tests | The suite has 60 passing cases | Status is taken from the 60-case run |
| L3 | `day` was assumed valid | It was used in `.thumbs_{day}`, and `shutil.rmtree` removed that path; `day="../../../evidence"` deleted the evidence folder in a temp-folder test on 2026-11-09 | Fixed with `parse_day`; REC-TC-007 passes |
| L4 | "Clear" removes everything | `clear_all` deletes local rows only | REC proposal |
| L5 | Retention covers all photos | Only `evidence/` | REC-TC-006 |
| L6 | Daily report includes workers | It has `person_id` (the track ID), not worker or vest | FR-8.7 partial |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
REC covers: `steelsight/records/*`
Existing test sources: none yet
Runner: pytest, no GPU or database needed
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `DETECTION-MODULE-MAP.md` | BR-REC-001, BR-REC-002, BR-REC-003, BR-REC-004, BR-REC-005 |
| ADRs | ADR-108, ADR-112 |
| `REQUIREMENTS_SPECIFICATION.md` | FR-8.1, FR-8.2, FR-8.5, FR-8.6, FR-8.7, FR-8.8, FR-7.6 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|---|---|---|---|
| TC-COND-001 | Evidence older than the retention period is deleted, and newer files stay | *(new)* covers `Maintenance.purge_old_evidence` | `REC-TC-001` |
| TC-COND-002 | A traversal name cannot download a file outside `REPORTS_DIR` | *(new)* covers `/api/reports/download` | `REC-TC-002` |
| TC-COND-003 | Only one disk alert is raised per crossing of the threshold | *(new)* covers `Maintenance.check_disk` | `REC-TC-003` |
| TC-COND-004 | `insert` then `for_day` round-trips the identity fields | *(new)* covers `Storage` | `REC-TC-004` |
| TC-COND-005 | The daily report runs once per day after the configured hour | *(new)* covers `DailyReport` | `REC-TC-005` |
| TC-COND-006 | Gate snapshots and `gate_events` follow a retention period (currently never purged) | *(new)* covers `Maintenance` | `REC-TC-006` |
| TC-COND-007 | A crafted `day` cannot delete folders | `tests/test_report_day.py` — `test_crafted_day_cannot_delete_folders` | `REC-TC-007` |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Equivalence Partitioning | Input classes of the rules in §2 | Each class behaves alike |
| Boundary Value Analysis | Timers, thresholds and limits | Most faults sit on limits |
| State Transition Testing | Module states in the TDS §6.3 | Order of events matters |
| Error Guessing | Lost connections, bad input, missing identity | Known field failures |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | pytest fixture | `engine` — a new SQLite file per test (`tests/conftest.py`) | Isolated local database |
| `FX-002` | pytest fixture | `cfg(...)` — temporary `config` values, undone after the test | Boundary settings |
| `FX-003` | Synthetic | Fake worker IDs, numeric vest codes, generated boxes | No real personal data |
| `FX-101` | Temp dir | `tmp_path` with `evidence/<year>/<day>/` | Retention |
| `FX-102` | Fake | `Storage`-like class returning `[]` and `{}` | Report without a database |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```python
def test_example(engine, cfg):        # fresh database and restored config per test
    cfg(GATE_IDENTIFY_SECONDS=2)
    subject = make_subject(engine)    # a factory inside the test; no module-level mutable state
```

---

### REC-TC-007 — A crafted `day` cannot delete folders

**Severity:** `CRITICAL`
**OWASP:** `A01:2021 — Broken Access Control`
**CWE:** `CWE-22 — Path Traversal`
**Legal:** `Luật 91/2025/QH15 — evidence integrity`
**Feature Under Test:** `POST /api/report/run` → `build_report`
**Test File:** `tests/test_report_day.py` — `test_crafted_day_cannot_delete_folders`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-007`
**Oracle Source:** `CWE-22`; Test-Spec L3

**Preconditions:** a temporary data folder with `evidence/keep.jpg`, and `REPORT_EMBED_EVIDENCE=True`.

**Test Steps (Attack Simulation):**
1. Send `{"day": "../../../evidence"}`
2. Check the response and the folder

**Expected Result (PASS = hệ thống an toàn):** `400`, and `evidence/keep.jpg` still exists.

**Expected Result (FAIL = lỗ hổng tồn tại):** the folder is deleted, as reproduced against `build_report` before the fix on 2026-11-09.

**Current Status:** 🟢V. RED was observed before the fix.

---

### REC-TC-001 — Old evidence is deleted and recent evidence stays

**Severity:** `HIGH`
**Feature Under Test:** `Maintenance.purge_old_evidence`
**Test File:** *(new)*
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-REC-001`

**Expected Result (PASS — hành vi đúng):** only folders older than the cutoff are removed; `delete_before` runs; a `system` event reports it.

**Expected Result (FAIL — dấu hiệu lỗi):** dispute evidence lost early, or the disk fills up.

**Current Status:** 🔴 Not written

---

### Full case list

| TC ID | Test | Description / Oracle | Severity | Status |
|---|---|---|---|---|
| REC-TC-001 | *(new)* covers `Maintenance.purge_old_evidence` | Evidence older than the retention period is deleted, and newer files stay — `BR-REC-001` | HIGH | 🔴 |
| REC-TC-002 | *(new)* covers `/api/reports/download` | A traversal name cannot download a file outside `REPORTS_DIR` — `BR-REC-003` | CRITICAL | 🔴 |
| REC-TC-003 | *(new)* covers `Maintenance.check_disk` | Only one disk alert is raised per crossing of the threshold — `BR-REC-002` | MEDIUM | 🔴 |
| REC-TC-004 | *(new)* covers `Storage` | `insert` then `for_day` round-trips the identity fields — `BR-DPL-003` | HIGH | 🔴 |
| REC-TC-005 | *(new)* covers `DailyReport` | The daily report runs once per day after the configured hour — `BR-REC-005` | MEDIUM | 🔴 |
| REC-TC-006 | *(new)* covers `Maintenance` | Gate snapshots and `gate_events` follow a retention period (currently never purged) — `Luật 91/2025/QH15` | HIGH | 🔴 |
| REC-TC-007 | `tests/test_report_day.py` — `test_crafted_day_cannot_delete_folders` | A crafted `day` cannot delete folders — `CWE-22` | CRITICAL | 🟢V |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|---|---|---|---|---|
| `REC-TC-001` | *(new)* covers `Maintenance.purge_old_evidence` | `[ ]` not evidenced | — | — |
| `REC-TC-002` | *(new)* covers `/api/reports/download` | `[ ]` not evidenced | — | — |
| `REC-TC-003` | *(new)* covers `Maintenance.check_disk` | `[ ]` not evidenced | — | — |
| `REC-TC-004` | *(new)* covers `Storage` | `[ ]` not evidenced | — | — |
| `REC-TC-005` | *(new)* covers `DailyReport` | `[ ]` not evidenced | — | — |
| `REC-TC-006` | *(new)* covers `Maintenance` | `[ ]` not evidenced | — | — |
| `REC-TC-007` | `tests/test_report_day.py` — `test_crafted_day_cannot_delete_folders` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```python
def purge_old_evidence(self): return 0   # the retention TC must FAIL
```

**Red Gate Verification:** *not run. Retrospective tests must be checked with the stub above before the module can move to T3.*

| TC ID | Stub Result | Expected | Actual | Root Cause |
|---|---|---|---|---|
| `REC-TC-001` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `REC-TC-002` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `REC-TC-003` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `REC-TC-004` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `REC-TC-005` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `REC-TC-006` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `REC-TC-007` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |

**Red Gate Evidence:** stub commit `___` · all FAIL? ☐ Yes

---

## 6. Entry / Exit Criteria

### Entry Criteria

- [ ] `STEELSIGHT-DET-REC-IMP-001` reviewed
- [ ] Section 2 confirmed
- [ ] Fixtures in TDS-05 available

### Exit Criteria (DoD)

- [ ] `pytest -q tests` green, with no skips
- [ ] Every 🔴 case above written or explicitly dropped
- [ ] No real names, faces or passwords in test data

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)** complete
- [ ] **Contract Existence** — `python -m pytest --collect-only -q tests` has no import errors
- [ ] **Props Isolation** — no mutable module-level state in test files
- [ ] **Oracle Source** recorded for each expected value

### Suspension Criteria

- ERP schema change blocks a mapped write
- A new architecture question needs an ADR
- The test run is broken by an unrelated change

---

## 7. Rollback Plan

```bash
git checkout -- steelsight/records/storage.py steelsight/records/report.py steelsight/records/reporter.py steelsight/records/notify.py steelsight/records/maintenance.py
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☐ | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☐ | G-1 |
| AP-AI-004 | Layer Violation | Test verify route có business logic | ☐ | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☐ | G-3 |

**Kết quả review:** ☐ pending human review

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
