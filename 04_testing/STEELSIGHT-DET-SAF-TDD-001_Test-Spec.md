# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-DET-SAF-TDD-001`
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
- `03_implement/STEELSIGHT-DET-SAF-IMP-001_TDS.md` — Technical Specification
- ADRs: ADR-107
- `docs/REQUIREMENTS_SPECIFICATION.md`

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `pytest` chưa xanh. Test data is synthetic: no real faces or names.
>
> **Retrospective note & status legend:** these tests were written with or after the code, so RED was never evidenced. 🟢V = re-run on 2026-11-09 after the package refactor (60 passed) · 🔴 = proposed, not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-09` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Safety Rules |

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
| **Feature / Gap ID** | `SAF` (existing) |
| **Module** | `SAF — Safety Rules` |
| **Spec gốc** | `STEELSIGHT-DET-SAF-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` |
| **Compliance Scope** | Luật 91/2025/QH15 — violations can name a worker; sentences are spoken aloud |
| **Upstream Dependencies** | VIS, IDG, edge-tts |
| **Downstream Consumers** | REC, SYN, WEB |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` |
| **Constraint Source** | `STEELSIGHT-DET-SAF-IMP-001 §17` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Docs cite flat modules such as `steelsight/gate.py` | Code moved to packages ([ADR-101](../02_design/adr/ADR-101_packages-by-purpose-and-routed-web-layer.md)) | Tests import the new paths |
| L2 | Requirements doc said 38 tests | The suite has 60 passing cases | Status is taken from the 60-case run |
| L3 | Header comment in `engine.py` says "3s" | Default timers are 10 s, reminder 1.5 s | Tests set values with `cfg` |
| L4 | "Both missing is one violation" | An unidentified person with no vest is `INTRUDER` even if the hard hat is also missing | SAF-TC-010 (new) |
| L5 | Stored confidence looks measured | With no negative box, `_confidence` stores the YOLO threshold | Not asserted as a measurement |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
SAF covers: `steelsight/safety/*`
Existing test sources: test_gate.py, test_safety.py
Runner: pytest, no GPU or database needed
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `DETECTION-MODULE-MAP.md` | BR-SAF-001, BR-SAF-002, BR-SAF-003, BR-SAF-004, BR-SAF-005, BR-SAF-006 |
| ADRs | ADR-107 |
| `REQUIREMENTS_SPECIFICATION.md` | FR-4.1, FR-4.1a, FR-4.4, FR-4.5, FR-4.6, FR-7.1 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|---|---|---|---|
| TC-COND-001 | Reminder once, then a named violation at 10 s | `tests/test_safety.py` — `test_reminder_comes_fast_and_only_once_then_named_violation_at_10s` | `SAF-TC-001` |
| TC-COND-002 | Fixing PPE prevents a violation | `tests/test_safety.py` — `test_putting_the_hat_on_after_the_reminder_means_no_violation` | `SAF-TC-002` |
| TC-COND-003 | A new absence gets a new reminder | `tests/test_safety.py` — `test_taking_it_off_again_gets_a_new_reminder` | `SAF-TC-003` |
| TC-COND-004 | Combined reminder | `tests/test_safety.py` — `test_missing_both_gets_the_combined_reminder` | `SAF-TC-004` |
| TC-COND-005 | Setting turns reminders off | `tests/test_safety.py` — `test_reminder_can_be_switched_off` | `SAF-TC-005` |
| TC-COND-006 | Sentences name the person | `tests/test_safety.py` — `test_violation_sentences_name_the_person` | `SAF-TC-006` |
| TC-COND-007 | A carried hard hat is not worn | `tests/test_gate.py` — `test_helmet_in_hand_does_not_count_as_worn` | `SAF-TC-007` |
| TC-COND-008 | The same worker on two cameras within the cooldown produces one violation | *(new)* covers `CooldownBook` | `SAF-TC-008` |
| TC-COND-009 | A person below `SAFETY_MIN_PERSON_PX` holds both timers | *(new)* covers `SafetyMonitor.process` | `SAF-TC-009` |
| TC-COND-010 | No identity and no vest becomes `intruder` after the OCR wait | *(new)* covers `SafetyMonitor.process` | `SAF-TC-010` |

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
| `FX-101` | Fake | `FakeSpeaker` recording `said` | Sentences without audio |
| `FX-102` | Fixture | `mon` with 1.5 s, 10 s, 60 s, 40 px | Default timings |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```python
def test_example(engine, cfg):        # fresh database and restored config per test
    cfg(GATE_IDENTIFY_SECONDS=2)
    subject = make_subject(engine)    # a factory inside the test; no module-level mutable state
```

---

### SAF-TC-001 — Reminder once, then a named violation at 10 s

**Severity:** `HIGH`
**Feature Under Test:** `SafetyMonitor.process`, `_remind`
**Test File:** `tests/test_safety.py` — `test_reminder_comes_fast_and_only_once_then_named_violation_at_10s`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-SAF-001`, `FR-4.1a`

**Test Steps:** step a worker with a vest and no hat at 0.5 s intervals from 0 to 10 s.

**Expected Result (PASS — hành vi đúng):** nothing is said before 1.5 s, one reminder is said, no violation before 10 s; at 10 s one `MISSING_HELMET` with `worker_name == "W007"`, `spoken` true, and the last sentence is `Cảnh báo! Công nhân W007 không đội mũ bảo hộ.`

**Expected Result (FAIL — dấu hiệu lỗi):** an early record costing a worker points, or repeated reminders.

**Current Status:** 🟢V

---

### SAF-TC-007 — A carried hard hat is not worn

**Severity:** `HIGH`
**Feature Under Test:** `associate_ppe`
**Test File:** `tests/test_gate.py` — `test_helmet_in_hand_does_not_count_as_worn`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-007`
**Oracle Source:** `BR-SAF-004`, `FR-6.9`

**Expected Result (PASS — hành vi đúng):** waist-height Helmet gives `{1: {"vest": 0.9}}`; on-head Helmet gives both.

**Expected Result (FAIL — dấu hiệu lỗi):** a worker carrying a hard hat counts as wearing it.

**Current Status:** 🟢V

---

### SAF-TC-008 — One violation across two cameras within the cooldown

**Severity:** `HIGH`
**Feature Under Test:** `CooldownBook`
**Test File:** *(new)*
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-008`
**Oracle Source:** `BR-SAF-003`, `ADR-107`

**Expected Result (PASS — hành vi đúng):** the second camera returns no violation.

**Expected Result (FAIL — dấu hiệu lỗi):** duplicate records for one episode.

**Current Status:** 🔴 Not written

---

### Full case list

| TC ID | Test | Description / Oracle | Severity | Status |
|---|---|---|---|---|
| SAF-TC-001 | `tests/test_safety.py` — `test_reminder_comes_fast_and_only_once_then_named_violation_at_10s` | Reminder once, then a named violation at 10 s — `BR-SAF-001` | HIGH | 🟢V |
| SAF-TC-002 | `tests/test_safety.py` — `test_putting_the_hat_on_after_the_reminder_means_no_violation` | Fixing PPE prevents a violation — `BR-SAF-002` | HIGH | 🟢V |
| SAF-TC-003 | `tests/test_safety.py` — `test_taking_it_off_again_gets_a_new_reminder` | A new absence gets a new reminder — `BR-SAF-002` | MEDIUM | 🟢V |
| SAF-TC-004 | `tests/test_safety.py` — `test_missing_both_gets_the_combined_reminder` | Combined reminder — `BR-SAF-005` | MEDIUM | 🟢V |
| SAF-TC-005 | `tests/test_safety.py` — `test_reminder_can_be_switched_off` | Setting turns reminders off — `ADR-107` | LOW | 🟢V |
| SAF-TC-006 | `tests/test_safety.py` — `test_violation_sentences_name_the_person` | Sentences name the person — `FR-7.1` | MEDIUM | 🟢V |
| SAF-TC-007 | `tests/test_gate.py` — `test_helmet_in_hand_does_not_count_as_worn` | A carried hard hat is not worn — `BR-SAF-004` | HIGH | 🟢V |
| SAF-TC-008 | *(new)* covers `CooldownBook` | The same worker on two cameras within the cooldown produces one violation — `BR-SAF-003` | HIGH | 🔴 |
| SAF-TC-009 | *(new)* covers `SafetyMonitor.process` | A person below `SAFETY_MIN_PERSON_PX` holds both timers — `ADR-107` | MEDIUM | 🔴 |
| SAF-TC-010 | *(new)* covers `SafetyMonitor.process` | No identity and no vest becomes `intruder` after the OCR wait — `BR-SAF-006` | HIGH | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|---|---|---|---|---|
| `SAF-TC-001` | `tests/test_safety.py` — `test_reminder_comes_fast_and_only_once_then_named_violation_at_10s` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SAF-TC-002` | `tests/test_safety.py` — `test_putting_the_hat_on_after_the_reminder_means_no_violation` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SAF-TC-003` | `tests/test_safety.py` — `test_taking_it_off_again_gets_a_new_reminder` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SAF-TC-004` | `tests/test_safety.py` — `test_missing_both_gets_the_combined_reminder` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SAF-TC-005` | `tests/test_safety.py` — `test_reminder_can_be_switched_off` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SAF-TC-006` | `tests/test_safety.py` — `test_violation_sentences_name_the_person` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SAF-TC-007` | `tests/test_gate.py` — `test_helmet_in_hand_does_not_count_as_worn` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SAF-TC-008` | *(new)* covers `CooldownBook` | `[ ]` not evidenced | — | — |
| `SAF-TC-009` | *(new)* covers `SafetyMonitor.process` | `[ ]` not evidenced | — | — |
| `SAF-TC-010` | *(new)* covers `SafetyMonitor.process` | `[ ]` not evidenced | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```python
def cooling(self, key, flag, now): return False   # never cooling; the cooldown TC must FAIL
```

**Red Gate Verification:** *not run. Retrospective tests must be checked with the stub above before the module can move to T3.*

| TC ID | Stub Result | Expected | Actual | Root Cause |
|---|---|---|---|---|
| `SAF-TC-001` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-002` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-003` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-004` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-005` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-006` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-007` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-008` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-009` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SAF-TC-010` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |

**Red Gate Evidence:** stub commit `___` · all FAIL? ☐ Yes

---

## 6. Entry / Exit Criteria

### Entry Criteria

- [ ] `STEELSIGHT-DET-SAF-IMP-001` reviewed
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
git checkout -- steelsight/safety/rules.py steelsight/safety/monitor.py steelsight/safety/alarm.py steelsight/safety/tts.py
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
