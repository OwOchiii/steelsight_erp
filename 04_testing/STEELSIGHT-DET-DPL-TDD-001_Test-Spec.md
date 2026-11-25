# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-DET-DPL-TDD-001`
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
- `03_implement/STEELSIGHT-DET-DPL-IMP-001_TDS.md` — Technical Specification
- ADRs: ADR-101, ADR-102, ADR-113
- `docs/REQUIREMENTS_SPECIFICATION.md`

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `pytest` chưa xanh. Test data is synthetic: no real faces or names.
>
> **Retrospective note & status legend:** these tests were written with or after the code, so RED was never evidenced. 🟢V = re-run on 2026-11-09 after the package refactor (60 passed) · 🔴 = proposed, not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-09` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Detection Platform |

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
| **Feature / Gap ID** | `DPL` (existing) |
| **Module** | `DPL — Detection Platform` |
| **Spec gốc** | `STEELSIGHT-DET-DPL-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `Confidential` |
| **Compliance Scope** | Luật 91/2025/QH15 — settings and local files hold credentials; camera passwords are plain text (NFR-8) |
| **Upstream Dependencies** | Filesystem, `.env`, SQLite |
| **Downstream Consumers** | All modules |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` |
| **Constraint Source** | `STEELSIGHT-DET-DPL-IMP-001 §17` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Docs cite flat modules such as `steelsight/gate.py` | Code moved to packages ([ADR-101](../02_design/adr/ADR-101_packages-by-purpose-and-routed-web-layer.md)) | Tests import the new paths |
| L2 | Requirements doc said 38 tests | The suite has 60 passing cases | Status is taken from the 60-case run |
| L3 | "Survive a crash" (NFR-9) reads as all config | `save_cameras` writes `cameras.json` directly; only `settings.json` is atomic | No test claims it; DPL-TC-006 (new) records the gap |
| L4 | `CENTRAL_ENABLED`, `CENTRAL_DB_PATH`, `CHECKIN_WINDOW_SECONDS` look active | Nothing outside `core/config.py` reads them | Not tested; candidates for removal |
| L5 | Comment in `config.py` mentions `data/faces.db` and per-day attendance | Faces come from the ERP; attendance is `gate_events` | Tests encode the code's behaviour |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
DPL covers: `steelsight/core/*`, `run.py`, `desktop.py`, `scripts/build_engine.py`, `steelsight.spec`, `setup.*`, `build_exe.ps1`
Existing test sources: none yet
Runner: pytest, no GPU or database needed
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `DETECTION-MODULE-MAP.md` | BR-DPL-001, BR-DPL-002, BR-DPL-003, BR-DPL-004, BR-DPL-005 |
| ADRs | ADR-101, ADR-102, ADR-113 |
| `REQUIREMENTS_SPECIFICATION.md` | NFR-4, NFR-5, NFR-6, NFR-9 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|---|---|---|---|
| TC-COND-001 | A settings key overrides its default with the default's type | *(new)* covers `config.load_user_settings` | `DPL-TC-001` |
| TC-COND-002 | An unknown settings key is ignored | *(new)* covers `config.load_user_settings` | `DPL-TC-002` |
| TC-COND-003 | `public_settings()` never contains ERP secrets, SMTP password or Telegram token | *(new)* covers `config.public_settings` | `DPL-TC-003` |
| TC-COND-004 | `migrate` adds a missing column to an old database file | *(new)* covers `core.db.migrate` | `DPL-TC-004` |
| TC-COND-005 | `BASE_DIR` resolves to the project root after the package move | *(new)* covers `core.config` | `DPL-TC-005` |
| TC-COND-006 | `cameras.json` survives a crash during save (currently not atomic) | *(new)* covers `config.save_cameras` | `DPL-TC-006` |

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
| `FX-101` | Temp file | `settings.json` with valid, invalid and unknown keys | Overlay rules |
| `FX-102` | SQLite | A file created without `camera_code` | Migration |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```python
def test_example(engine, cfg):        # fresh database and restored config per test
    cfg(GATE_IDENTIFY_SECONDS=2)
    subject = make_subject(engine)    # a factory inside the test; no module-level mutable state
```

---

### DPL-TC-003 — `public_settings()` never contains secrets

**Severity:** `CRITICAL`
**CWE:** `CWE-200 — Exposure of Sensitive Information`
**Legal:** `Luật 91/2025/QH15`
**Feature Under Test:** `config.public_settings()`
**Test File:** *(new)* `tests/test_config.py`
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-003`
**Oracle Source:** `BR-DPL-002`, `ADR-110 §AI Prompt Constraint 3`

**Preconditions:** `cfg(TELEGRAM_BOT_TOKEN="fake-token", SMTP_PASSWORD="fake-pass", ERP_DB_URL="postgresql://x")`.

**Test Steps:**
1. Set the fake secrets with `cfg`
2. Call `public_settings()`
3. Assert no value equals a secret, and the `*_set` flags are true

**Expected Result (PASS — hành vi đúng):** only boolean "is set" flags describe secrets.

**Expected Result (FAIL — dấu hiệu lỗi):** a token, password or connection string reaches the browser through `/api/state`.

**Current Status:** 🔴 Not written

---

### DPL-TC-005 — `BASE_DIR` is the project root after the package move

**Severity:** `HIGH`
**Feature Under Test:** `core.config.BASE_DIR`
**Test File:** *(new)* `tests/test_config.py`
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-005`
**Oracle Source:** `ADR-101 §Risks`

**Test Steps:** assert that `os.path.isdir(os.path.join(config.BASE_DIR, "steelsight"))` and that `STATIC_DIR` contains `index.html`.

**Expected Result (PASS — hành vi đúng):** both hold.

**Expected Result (FAIL — dấu hiệu lỗi):** data and models resolve under `steelsight/`, and the app starts with empty settings.

**Current Status:** 🔴 Not written. Checked by hand on 2026-11-09.

---

### Full case list

| TC ID | Test | Description / Oracle | Severity | Status |
|---|---|---|---|---|
| DPL-TC-001 | *(new)* covers `config.load_user_settings` | A settings key overrides its default with the default's type — `BR-DPL-001` | HIGH | 🔴 |
| DPL-TC-002 | *(new)* covers `config.load_user_settings` | An unknown settings key is ignored — `BR-DPL-001` | MEDIUM | 🔴 |
| DPL-TC-003 | *(new)* covers `config.public_settings` | `public_settings()` never contains ERP secrets, SMTP password or Telegram token — `BR-DPL-002` | CRITICAL | 🔴 |
| DPL-TC-004 | *(new)* covers `core.db.migrate` | `migrate` adds a missing column to an old database file — `BR-DPL-003` | HIGH | 🔴 |
| DPL-TC-005 | *(new)* covers `core.config` | `BASE_DIR` resolves to the project root after the package move — `ADR-101` | HIGH | 🔴 |
| DPL-TC-006 | *(new)* covers `config.save_cameras` | `cameras.json` survives a crash during save (currently not atomic) — `NFR-9` | MEDIUM | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|---|---|---|---|---|
| `DPL-TC-001` | *(new)* covers `config.load_user_settings` | `[ ]` not evidenced | — | — |
| `DPL-TC-002` | *(new)* covers `config.load_user_settings` | `[ ]` not evidenced | — | — |
| `DPL-TC-003` | *(new)* covers `config.public_settings` | `[ ]` not evidenced | — | — |
| `DPL-TC-004` | *(new)* covers `core.db.migrate` | `[ ]` not evidenced | — | — |
| `DPL-TC-005` | *(new)* covers `core.config` | `[ ]` not evidenced | — | — |
| `DPL-TC-006` | *(new)* covers `config.save_cameras` | `[ ]` not evidenced | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```python
def public_settings(): return dict(globals())   # leaks everything; the secrets TC must FAIL
```

**Red Gate Verification:** *not run. Retrospective tests must be checked with the stub above before the module can move to T3.*

| TC ID | Stub Result | Expected | Actual | Root Cause |
|---|---|---|---|---|
| `DPL-TC-001` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `DPL-TC-002` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `DPL-TC-003` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `DPL-TC-004` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `DPL-TC-005` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `DPL-TC-006` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |

**Red Gate Evidence:** stub commit `___` · all FAIL? ☐ Yes

---

## 6. Entry / Exit Criteria

### Entry Criteria

- [ ] `STEELSIGHT-DET-DPL-IMP-001` reviewed
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
git checkout -- steelsight/core/config.py steelsight/core/domain.py steelsight/core/db.py run.py desktop.py scripts/build_engine.py steelsight.spec
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
