# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-DET-WEB-TDD-001`
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
- `03_implement/STEELSIGHT-DET-WEB-IMP-001_TDS.md` — Technical Specification
- ADRs: ADR-101, ADR-112, ADR-114, ADR-115
- `docs/REQUIREMENTS_SPECIFICATION.md`

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `pytest` chưa xanh. Test data is synthetic: no real faces or names.
>
> **Retrospective note & status legend:** these tests were written with or after the code, so RED was never evidenced. 🟢V = re-run on 2026-11-09 after the package refactor (60 passed) · 🔴 = proposed, not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-09` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Web & Access |
| `2026-11-17` | `Claude Code (Opus 5.5)` | Added WEB-TC-010 and WEB-TC-011 for ADR-121 |

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
| **Feature / Gap ID** | `WEB` (existing) |
| **Module** | `WEB — Web & Access` |
| **Spec gốc** | `STEELSIGHT-DET-WEB-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `Confidential` |
| **Compliance Scope** | Luật 91/2025/QH15 — **demo build has no access control; not for customer delivery** |
| **Upstream Dependencies** | All modules |
| **Downstream Consumers** | Browser dashboard, pywebview |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` |
| **Constraint Source** | `STEELSIGHT-DET-WEB-IMP-001 §17` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Docs cite flat modules such as `steelsight/gate.py` | Code moved to packages ([ADR-101](../02_design/adr/ADR-101_packages-by-purpose-and-routed-web-layer.md)) | Tests import the new paths |
| L2 | Requirements doc said 38 tests | The suite has 60 passing cases | Status is taken from the 60-case run |
| L3 | Docs imply roles apply | `ROLES` exists, but no route checks a role | WEB tests cover sessions only |
| L4 | `list_users`, `delete_user`, `change_password`, `set_role` look reachable | No route calls them | Not tested through the API |
| L5 | "Setup never replaces an admin" | `/api/setup` checks `needs_setup()` before `create_user`, and `create_user` refuses an existing username | WEB-TC-004 covers it |
| L6 | `/api/cameras` looks safe | Returns `source` including RTSP passwords | WEB-TC-007 fails today |
| L7 | `/api/violations?limit=` is capped | `min(limit, 1000)`, but a negative value is passed through | Noted |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
WEB covers: `steelsight/web/*`
Existing test sources: none yet
Runner: pytest, no GPU or database needed
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `DETECTION-MODULE-MAP.md` | BR-WEB-001, BR-WEB-002, BR-WEB-003, BR-WEB-004, BR-WEB-005, BR-WEB-006, BR-WEB-007 |
| ADRs | ADR-101, ADR-112, ADR-114, ADR-115 |
| `REQUIREMENTS_SPECIFICATION.md` | FR-1.4, FR-2.6, FR-6.6, FR-7.4, FR-9.1, FR-9.2, FR-9.3, FR-9.4 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|---|---|---|---|
| TC-COND-001 | Each listed endpoint answers with its documented status | *(new)* covers `web/routes/*` | `WEB-TC-001` |
| TC-COND-002 | With auth restored, an API call with no session returns 401 | *(new)* covers `auth_guard` | `WEB-TC-002` |
| TC-COND-003 | With auth restored, `/ws` refuses a connection with no session | *(new)* covers `routes/live.py` | `WEB-TC-003` |
| TC-COND-004 | `/api/setup` refuses once an account exists | *(new)* covers `api_setup` | `WEB-TC-004` |
| TC-COND-005 | A blank `smtp_password` does not erase the saved one | *(new)* covers `routes/settings.py` | `WEB-TC-005` |
| TC-COND-006 | Lockout delays double after 5 failed sign-ins | *(new)* covers `AuthStore.locked_for` | `WEB-TC-006` |
| TC-COND-007 | `/api/cameras` does not return RTSP passwords (currently fails) | *(new)* covers `routes/cameras.py` | `WEB-TC-007` |
| TC-COND-008 | A launch token validates only when exact | `tests/test_launch_token.py` — `test_missing_or_wrong_tokens_are_refused` | `WEB-TC-008` |
| TC-COND-009 | `/ws` refuses a connection with no launch cookie | *(new)* covers `routes/live.py` | `WEB-TC-009` |

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
| `FX-101` | ASGI | Direct `app(scope, receive, send)` calls | No test-client dependency |
| `FX-102` | SQLite | `AuthStore(db_path=tmp)` | Isolated accounts |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```python
def test_example(engine, cfg):        # fresh database and restored config per test
    cfg(GATE_IDENTIFY_SECONDS=2)
    subject = make_subject(engine)    # a factory inside the test; no module-level mutable state
```

---

### WEB-TC-002 — With auth restored, an API call with no session returns 401

**Severity:** `CRITICAL`
**OWASP:** `A01:2021 — Broken Access Control`
**CWE:** `CWE-306 — Missing Authentication for Critical Function`
**Legal:** `Luật 91/2025/QH15`
**Feature Under Test:** `auth_guard`
**Test File:** *(new)*
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-002`
**Oracle Source:** `FR-9.1`, `ADR-114 §AI Prompt Constraint 2`

**Test Steps (Attack Simulation):** with the early return removed, call `GET /api/state`, `GET /stream/0` and `GET /evidence/x.jpg` with no cookie.

**Expected Result (PASS = hệ thống an toàn):** `401 {"error":"unauthorized"}`.

**Expected Result (FAIL = lỗ hổng tồn tại):** video, photos and worker records are open to anyone reaching the port. This is the current demo behaviour.

**Current Status:** 🔴 Not written

---

### WEB-TC-004 — `/api/setup` refuses once an account exists

**Severity:** `CRITICAL`
**CWE:** `CWE-620 — Unverified Password Change`
**Feature Under Test:** `web/routes/account.py` `api_setup`
**Test File:** *(new)*
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `BR-WEB-005`

**Expected Result (PASS = hệ thống an toàn):** the call returns 403 and the original password still verifies.

**Expected Result (FAIL = lỗ hổng tồn tại):** an unauthenticated caller creates a second admin or resets the existing one.

**Current Status:** 🔴 Not written

---

### Full case list

| TC ID | Test | Description / Oracle | Severity | Status |
|---|---|---|---|---|
| WEB-TC-001 | *(new)* covers `web/routes/*` | Each listed endpoint answers with its documented status — `§9` | HIGH | 🔴 |
| WEB-TC-002 | *(new)* covers `auth_guard` | With auth restored, an API call with no session returns 401 — `FR-9.1` | CRITICAL | 🔴 |
| WEB-TC-003 | *(new)* covers `routes/live.py` | With auth restored, `/ws` refuses a connection with no session — `FR-9.1` | CRITICAL | 🔴 |
| WEB-TC-004 | *(new)* covers `api_setup` | `/api/setup` refuses once an account exists — `BR-WEB-005` | CRITICAL | 🔴 |
| WEB-TC-005 | *(new)* covers `routes/settings.py` | A blank `smtp_password` does not erase the saved one — `BR-WEB-006` | HIGH | 🔴 |
| WEB-TC-006 | *(new)* covers `AuthStore.locked_for` | Lockout delays double after 5 failed sign-ins — `BR-WEB-004` | HIGH | 🔴 |
| WEB-TC-007 | *(new)* covers `routes/cameras.py` | `/api/cameras` does not return RTSP passwords (currently fails) — `NFR-7` | HIGH | 🔴 |
| WEB-TC-008 | `tests/test_launch_token.py` — `test_missing_or_wrong_tokens_are_refused` | A launch token validates only when exact — `BR-WEB-007` | CRITICAL | 🟢V |
| WEB-TC-009 | *(new)* covers `routes/live.py` | `/ws` refuses a connection with no launch cookie — `BR-WEB-007` | CRITICAL | 🔴 |
| WEB-TC-010 | `tests/test_live_still.py` — `test_still_returns_latest_annotated_jpeg_and_is_not_cached` | Still frame, not cached — `BR-WEB-008` | MEDIUM | 🟢V |
| WEB-TC-011 | `tests/test_live_still.py` — `test_still_without_a_frame_yet_is_204` | 204 before the first frame — `BR-WEB-008` | LOW | 🟢V |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|---|---|---|---|---|
| `WEB-TC-001` | *(new)* covers `web/routes/*` | `[ ]` not evidenced | — | — |
| `WEB-TC-002` | *(new)* covers `auth_guard` | `[ ]` not evidenced | — | — |
| `WEB-TC-003` | *(new)* covers `routes/live.py` | `[ ]` not evidenced | — | — |
| `WEB-TC-004` | *(new)* covers `api_setup` | `[ ]` not evidenced | — | — |
| `WEB-TC-005` | *(new)* covers `routes/settings.py` | `[ ]` not evidenced | — | — |
| `WEB-TC-006` | *(new)* covers `AuthStore.locked_for` | `[ ]` not evidenced | — | — |
| `WEB-TC-007` | *(new)* covers `routes/cameras.py` | `[ ]` not evidenced | — | — |
| `WEB-TC-008` | `tests/test_launch_token.py` — `test_missing_or_wrong_tokens_are_refused` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `WEB-TC-009` | *(new)* covers `routes/live.py` | `[ ]` not evidenced | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```python
async def auth_guard(request, call_next): return await call_next(request)   # today's demo bypass makes the auth-restored TCs FAIL
```

**Red Gate Verification:** *not run. Retrospective tests must be checked with the stub above before the module can move to T3.*

| TC ID | Stub Result | Expected | Actual | Root Cause |
|---|---|---|---|---|
| `WEB-TC-001` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-002` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-003` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-004` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-005` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-006` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-007` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-008` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `WEB-TC-009` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |

**Red Gate Evidence:** stub commit `___` · all FAIL? ☐ Yes

---

## 6. Entry / Exit Criteria

### Entry Criteria

- [ ] `STEELSIGHT-DET-WEB-IMP-001` reviewed
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
git checkout -- steelsight/web/app.py steelsight/web/state.py steelsight/web/deps.py steelsight/web/auth.py steelsight/web/routes/account.py steelsight/web/routes/live.py steelsight/web/routes/settings.py steelsight/web/routes/violations.py steelsight/web/routes/erp.py steelsight/web/routes/cameras.py steelsight/web/routes/reports.py steelsight/web/routes/workers.py
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
