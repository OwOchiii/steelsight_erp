# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-DET-SYN-TDD-001`
**Version:** `1.0`
**Date:** `2026-11-09`
**Status:** `Draft`
**Standard:** ISO/IEC/IEEE 29119-3:2021 — Software Testing Part 3: Test Documentation
**Author:** `Claude Code (Opus 5.5) — documentation, from the existing test suite`
**Reviewed by:** `[ ] Tech Lead — Pending`
**DPO Sign-off:** `[ ] Pending` *(mandatory — biometric data)*
**Approved by:** `[ ] Pending`
**Classification:** `Internal — Confidential`

**References:**
- `02_design/DETECTION-MODULE-MAP.md` — BR / US catalog
- `03_implement/STEELSIGHT-DET-SYN-IMP-001_TDS.md` — Technical Specification
- ADRs: ADR-108, ADR-109, ADR-110
- `docs/REQUIREMENTS_SPECIFICATION.md`

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `pytest` chưa xanh. Test data is synthetic: no real faces or names.
>
> **Retrospective note & status legend:** these tests were written with or after the code, so RED was never evidenced. 🟢V = re-run on 2026-11-09 after the package refactor (60 passed) · 🔴 = proposed, not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-09` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho ERP Sync |
| `2026-11-17` | `Claude Code (Opus 5.5)` | Added SYN-TC-027 to SYN-TC-037 for ADR-119 and ADR-120, and the V14 integration checks |

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
| **Feature / Gap ID** | `SYN` (existing) |
| **Module** | `SYN — ERP Sync` |
| **Spec gốc** | `STEELSIGHT-DET-SYN-IMP-001` |
| **Priority** | 🔴 P0 |
| **Sprint** | `Not defined` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `Sensitive-PII` |
| **Compliance Scope** | Luật 91/2025/QH15, NĐ 356/2025 — biometric vectors and worker records cross to a processor; DPO review required |
| **Upstream Dependencies** | IDG, SAF, Supabase PostgreSQL and Storage, ERP REST API |
| **Downstream Consumers** | IDG (gallery, links), WEB (status) |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` |
| **Constraint Source** | `STEELSIGHT-DET-SYN-IMP-001 §17` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Docs cite flat modules such as `steelsight/gate.py` | Code moved to packages ([ADR-101](../02_design/adr/ADR-101_packages-by-purpose-and-routed-web-layer.md)) | Tests import the new paths |
| L2 | Requirements doc said 38 tests | The suite has 60 passing cases | Status is taken from the 60-case run |
| L3 | Violation snapshot path always points at a real photo | If Storage isn't configured, or no evidence was saved, `_upload` is skipped but `snapshot_path` is still written; `gate_in` clears it, `violation` doesn't | SYN-TC-024 scope; flagged as a defect |
| L4 | Outbox is cleaned up | `purge_done` is never called | SYN-TC-026 (new) |
| L5 | Enrolment doesn't affect the db lane | `embed_photo` runs on `erp-sync` and can wait up to 180 s for the model, delaying the lane | Known limitation |
| L6 | Detection is fully least-privilege | `SUPABASE_SERVICE_KEY` on the site machine bypasses Storage policies | Raised for review |
| L7 | `gate_out` replay guarded by `event_id` | It uses no `event_id`; `check_out_at IS NULL AND check_in_at <= at` makes a replay a no-op | SYN-TC-019 asserts statements only |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
SYN covers: `steelsight/erp/*`
Existing test sources: test_erp_mapping.py, test_outbox.py, test_sync.py
Runner: pytest, no GPU or database needed
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `DETECTION-MODULE-MAP.md` | BR-SYN-001, BR-SYN-002, BR-SYN-003, BR-SYN-004, BR-SYN-005, BR-SYN-006, BR-SYN-007, BR-SYN-008 |
| ADRs | ADR-108, ADR-109, ADR-110 |
| `REQUIREMENTS_SPECIFICATION.md` | FR-8.9, FR-7.7, INT-2, INT-3, INT-7, INT-8, INT-11 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|---|---|---|---|
| TC-COND-001 | Types map to the ERP enum | `tests/test_erp_mapping.py` — `test_types_map_to_erp_enum` | `SYN-TC-001` |
| TC-COND-002 | Confidence 0–100 | `tests/test_erp_mapping.py` — `test_confidence_is_percent_and_clamped` | `SYN-TC-002` |
| TC-COND-003 | Box as percent | `tests/test_erp_mapping.py` — `test_box_is_percent_of_frame_and_clamped` | `SYN-TC-003` |
| TC-COND-004 | Vest key by digits | `tests/test_erp_mapping.py` — `test_vest_key_matches_by_digits` | `SYN-TC-004` |
| TC-COND-005 | Time zone handling | `tests/test_erp_mapping.py` — `test_aware_adds_zone_only_to_naive_times` | `SYN-TC-005` |
| TC-COND-006 | Two rows with stable IDs | `tests/test_erp_mapping.py` — `test_missing_both_becomes_two_rows_with_stable_ids_and_one_photo` | `SYN-TC-006` |
| TC-COND-007 | Row contents | `tests/test_erp_mapping.py` — `test_violation_row_contents` | `SYN-TC-007` |
| TC-COND-008 | One alert per window | `tests/test_erp_mapping.py` — `test_burst_counter_alerts_once_per_window` | `SYN-TC-008` |
| TC-COND-009 | Both rows count | `tests/test_erp_mapping.py` — `test_burst_counter_counts_both_rows` | `SYN-TC-009` |
| TC-COND-010 | Order per lane | `tests/test_outbox.py` — `test_events_come_out_in_order_per_lane` | `SYN-TC-010` |
| TC-COND-011 | Retry holds the head | `tests/test_outbox.py` — `test_retry_keeps_the_head_until_its_time` | `SYN-TC-011` |
| TC-COND-012 | Failed events step aside | `tests/test_outbox.py` — `test_failed_events_step_aside_and_can_be_requeued` | `SYN-TC-012` |
| TC-COND-013 | Event ID preserved | `tests/test_outbox.py` — `test_event_id_is_kept` | `SYN-TC-013` |
| TC-COND-014 | Gate off drops events | `tests/test_outbox.py` — `test_sync_gate_drops_events_while_sync_is_off` | `SYN-TC-014` |
| TC-COND-015 | Replay is a no-op | `tests/test_sync.py` — `test_violation_insert_and_retry_is_a_no_op` | `SYN-TC-015` |
| TC-COND-016 | Unknown camera is permanent | `tests/test_sync.py` — `test_unknown_camera_is_a_permanent_error` | `SYN-TC-016` |
| TC-COND-017 | Closes old visit, moves link, checks in | `tests/test_sync.py` — `test_gate_in_closes_old_visit_moves_the_link_then_checks_in` | `SYN-TC-017` |
| TC-COND-018 | Unknown vest still checks in | `tests/test_sync.py` — `test_gate_in_with_unknown_vest_checks_in_without_a_link` | `SYN-TC-018` |
| TC-COND-019 | Checks out and unlinks | `tests/test_sync.py` — `test_gate_out_checks_out_and_unlinks` | `SYN-TC-019` |
| TC-COND-020 | Rejected rows do not block | `tests/test_sync.py` — `test_drain_marks_rejected_rows_failed_and_keeps_going` | `SYN-TC-020` |
| TC-COND-021 | Network drop keeps the event | `tests/test_sync.py` — `test_drain_keeps_the_event_when_the_network_drops` | `SYN-TC-021` |
| TC-COND-022 | Links seeded only when empty | `tests/test_sync.py` — `test_reference_refresh_loads_gallery_and_links_only_when_outbox_is_empty` | `SYN-TC-022` |
| TC-COND-023 | A process restart resumes pending events in order | *(new)* covers `Outbox` | `SYN-TC-023` |
| TC-COND-024 | Backoff doubles and stops at 60 s | *(new)* covers `ErpSync` | `SYN-TC-024` |
| TC-COND-025 | An enrolment replaced meanwhile is not overwritten | *(new)* covers `ErpSync.process_enrolments` | `SYN-TC-025` |
| TC-COND-026 | Done rows are purged on a schedule (not implemented) | *(new)* covers `Outbox.purge_done` | `SYN-TC-026` |

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
| `FX-101` | Fake | `FakeConn(script)` logging SQL, commits and rollbacks | Handlers without a database |
| `FX-102` | Fixture | `erp`: `ErpSync(Outbox(engine), ErpReference())` | Isolated sync |
| `FX-103` | Script | `tests/erp_integration_check.py` | Real schema and grants |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```python
def test_example(engine, cfg):        # fresh database and restored config per test
    cfg(GATE_IDENTIFY_SECONDS=2)
    subject = make_subject(engine)    # a factory inside the test; no module-level mutable state
```

---

### SYN-TC-006 — `missing_both` becomes two rows with stable IDs and one photo

**Severity:** `CRITICAL`
**Feature Under Test:** `violation_rows`, `row_event_id`
**Test File:** `tests/test_erp_mapping.py` — `test_missing_both_becomes_two_rows_with_stable_ids_and_one_photo`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-006`
**Oracle Source:** `BR-SYN-004`, `ADR-109`

**Expected Result (PASS — hành vi đúng):** types `NO_HARD_HAT`, `NO_VEST`; two different IDs; the same IDs on a second call; one `snapshot_path`.

**Expected Result (FAIL — dấu hiệu lỗi):** a retry inserts another violation, costing a worker 10 safety points.

**Current Status:** 🟢V

---

### SYN-TC-010 — Events leave in order per lane

**Severity:** `CRITICAL`
**Feature Under Test:** `Outbox.head`
**Test File:** `tests/test_outbox.py` — `test_events_come_out_in_order_per_lane`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-010`
**Oracle Source:** `BR-SYN-001`

**Expected Result (PASS — hành vi đúng):** `gate_in` before `gate_out`; `alert` independent on lane `api`.

**Expected Result (FAIL — dấu hiệu lỗi):** a check-out applied before its check-in.

**Current Status:** 🟢V

---

### SYN-TC-015 — Replay is a no-op

**Severity:** `CRITICAL`
**Feature Under Test:** `ErpSync.apply`, `_violation`
**Test File:** `tests/test_sync.py` — `test_violation_insert_and_retry_is_a_no_op`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-015`
**Oracle Source:** `BR-SYN-005`, ERP `ADR-005`

**Expected Result (PASS — hành vi đúng):** first apply runs exists + insert; the second only exists.

**Expected Result (FAIL — dấu hiệu lỗi):** duplicate violation rows.

**Current Status:** 🟢V

---

### SYN-TC-021 — A network drop keeps the event

**Severity:** `CRITICAL`
**Feature Under Test:** `ErpSync._drain_db`
**Test File:** `tests/test_sync.py` — `test_drain_keeps_the_event_when_the_network_drops`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-021`
**Oracle Source:** `NFR-10`, `BR-SYN-002`

**Expected Result (PASS — hành vi đúng):** `OperationalError` propagates; `pending == 1`, `failed == 0`.

**Expected Result (FAIL — dấu hiệu lỗi):** the event is dropped or marked failed during an outage.

**Current Status:** 🟢V

---

### Full case list

| TC ID | Test | Description / Oracle | Severity | Status |
|---|---|---|---|---|
| SYN-TC-001 | `tests/test_erp_mapping.py` — `test_types_map_to_erp_enum` | Types map to the ERP enum — `INT-4` | HIGH | 🟢V |
| SYN-TC-002 | `tests/test_erp_mapping.py` — `test_confidence_is_percent_and_clamped` | Confidence 0–100 — `INT-4` | MEDIUM | 🟢V |
| SYN-TC-003 | `tests/test_erp_mapping.py` — `test_box_is_percent_of_frame_and_clamped` | Box as percent — `INT-4` | MEDIUM | 🟢V |
| SYN-TC-004 | `tests/test_erp_mapping.py` — `test_vest_key_matches_by_digits` | Vest key by digits — `ADR-106` | HIGH | 🟢V |
| SYN-TC-005 | `tests/test_erp_mapping.py` — `test_aware_adds_zone_only_to_naive_times` | Time zone handling — `INT-4` | MEDIUM | 🟢V |
| SYN-TC-006 | `tests/test_erp_mapping.py` — `test_missing_both_becomes_two_rows_with_stable_ids_and_one_photo` | Two rows with stable IDs — `BR-SYN-004` | CRITICAL | 🟢V |
| SYN-TC-007 | `tests/test_erp_mapping.py` — `test_violation_row_contents` | Row contents — `INT-4` | HIGH | 🟢V |
| SYN-TC-008 | `tests/test_erp_mapping.py` — `test_burst_counter_alerts_once_per_window` | One alert per window — `BR-SYN-008` | MEDIUM | 🟢V |
| SYN-TC-009 | `tests/test_erp_mapping.py` — `test_burst_counter_counts_both_rows` | Both rows count — `BR-SYN-008` | MEDIUM | 🟢V |
| SYN-TC-010 | `tests/test_outbox.py` — `test_events_come_out_in_order_per_lane` | Order per lane — `BR-SYN-001` | CRITICAL | 🟢V |
| SYN-TC-011 | `tests/test_outbox.py` — `test_retry_keeps_the_head_until_its_time` | Retry holds the head — `BR-SYN-001` | HIGH | 🟢V |
| SYN-TC-012 | `tests/test_outbox.py` — `test_failed_events_step_aside_and_can_be_requeued` | Failed events step aside — `BR-SYN-003` | HIGH | 🟢V |
| SYN-TC-013 | `tests/test_outbox.py` — `test_event_id_is_kept` | Event ID preserved — `BR-SYN-004` | CRITICAL | 🟢V |
| SYN-TC-014 | `tests/test_outbox.py` — `test_sync_gate_drops_events_while_sync_is_off` | Gate off drops events — `ADR-108` | MEDIUM | 🟢V |
| SYN-TC-015 | `tests/test_sync.py` — `test_violation_insert_and_retry_is_a_no_op` | Replay is a no-op — `BR-SYN-005` | CRITICAL | 🟢V |
| SYN-TC-016 | `tests/test_sync.py` — `test_unknown_camera_is_a_permanent_error` | Unknown camera is permanent — `BR-SYN-003` | HIGH | 🟢V |
| SYN-TC-017 | `tests/test_sync.py` — `test_gate_in_closes_old_visit_moves_the_link_then_checks_in` | Closes old visit, moves link, checks in — `INT-5` | CRITICAL | 🟢V |
| SYN-TC-018 | `tests/test_sync.py` — `test_gate_in_with_unknown_vest_checks_in_without_a_link` | Unknown vest still checks in — `INT-5` | HIGH | 🟢V |
| SYN-TC-019 | `tests/test_sync.py` — `test_gate_out_checks_out_and_unlinks` | Checks out and unlinks — `INT-6` | CRITICAL | 🟢V |
| SYN-TC-020 | `tests/test_sync.py` — `test_drain_marks_rejected_rows_failed_and_keeps_going` | Rejected rows do not block — `BR-SYN-003` | HIGH | 🟢V |
| SYN-TC-021 | `tests/test_sync.py` — `test_drain_keeps_the_event_when_the_network_drops` | Network drop keeps the event — `BR-SYN-002` | CRITICAL | 🟢V |
| SYN-TC-022 | `tests/test_sync.py` — `test_reference_refresh_loads_gallery_and_links_only_when_outbox_is_empty` | Links seeded only when empty — `BR-SYN-007` | HIGH | 🟢V |
| SYN-TC-023 | *(new)* covers `Outbox` | A process restart resumes pending events in order — `NFR-10` | HIGH | 🔴 |
| SYN-TC-024 | *(new)* covers `ErpSync` | Backoff doubles and stops at 60 s — `BR-SYN-002` | MEDIUM | 🔴 |
| SYN-TC-025 | *(new)* covers `ErpSync.process_enrolments` | An enrolment replaced meanwhile is not overwritten — `FR-5.1` | HIGH | 🔴 |
| SYN-TC-026 | *(new)* covers `Outbox.purge_done` | Done rows are purged on a schedule (not implemented) — `ADR-108` | LOW | 🔴 |
| SYN-TC-027 | `tests/test_sync.py` — `test_gallery_refresh_is_not_starved_by_a_failing_queue` | Reference refresh runs before a failing queue — `BR-SYN-009` | CRITICAL | 🟢V |
| SYN-TC-028 | `tests/test_sync.py` — `test_vest_fix_moves_the_link_and_points_the_open_visit_at_it` | `vest_fix` relinks and updates the open visit — `ADR-120` | CRITICAL | 🟢V |
| SYN-TC-029 | `tests/test_sync.py` — `test_vest_fix_sent_twice_creates_nothing_new` | `vest_fix` replay changes nothing — `BR-SYN-005` | HIGH | 🟢V |
| SYN-TC-030 | `tests/test_sync.py` — `test_vest_fix_to_no_vest_unlinks_and_clears_the_visit` | `vest_fix` to no vest — `ADR-120` | MEDIUM | 🟢V |
| SYN-TC-031 | `tests/test_sync.py` — `test_vest_fix_to_a_vest_the_erp_does_not_have_is_rejected` | Unknown vest is a permanent error — `ADR-120` | HIGH | 🟢V |
| SYN-TC-032 | `tests/test_erp_queue_cleanup.py` — `test_clear_failed_removes_only_rejected_events` | Clear rejected events only — `BR-SYN-011` | HIGH | 🟢V |
| SYN-TC-033 | `tests/test_erp_queue_cleanup.py` — `test_clear_pending_violations_keeps_attendance` | Clearing violations keeps attendance — `BR-SYN-011` | CRITICAL | 🟢V |
| SYN-TC-034 | `tests/test_erp_queue_cleanup.py` — `test_camera_without_erp_code_is_not_sent` | No ERP code, not queued — `BR-SYN-010` | HIGH | 🟢V |
| SYN-TC-035 | `tests/test_erp_queue_cleanup.py` — `test_code_unknown_to_erp_is_not_sent_once_the_list_is_loaded` | Unknown code not queued — `BR-SYN-010` | HIGH | 🟢V |
| SYN-TC-036 | `tests/test_erp_queue_cleanup.py` — `test_known_code_is_sent_and_offline_still_queues` | Known code queued, offline too — `BR-SYN-010` | HIGH | 🟢V |
| SYN-TC-037 | `tests/erp_integration_check.py` — `sections 10b and 8b` | V14 angle rows load, pose is read-only for detection, one row per angle; `vest_fix` on the real schema (32/32 checks) — `BR-SYN-006`, `ADR-120` | CRITICAL | 🟢V |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|---|---|---|---|---|
| `SYN-TC-001` | `tests/test_erp_mapping.py` — `test_types_map_to_erp_enum` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-002` | `tests/test_erp_mapping.py` — `test_confidence_is_percent_and_clamped` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-003` | `tests/test_erp_mapping.py` — `test_box_is_percent_of_frame_and_clamped` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-004` | `tests/test_erp_mapping.py` — `test_vest_key_matches_by_digits` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-005` | `tests/test_erp_mapping.py` — `test_aware_adds_zone_only_to_naive_times` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-006` | `tests/test_erp_mapping.py` — `test_missing_both_becomes_two_rows_with_stable_ids_and_one_photo` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-007` | `tests/test_erp_mapping.py` — `test_violation_row_contents` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-008` | `tests/test_erp_mapping.py` — `test_burst_counter_alerts_once_per_window` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-009` | `tests/test_erp_mapping.py` — `test_burst_counter_counts_both_rows` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-010` | `tests/test_outbox.py` — `test_events_come_out_in_order_per_lane` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-011` | `tests/test_outbox.py` — `test_retry_keeps_the_head_until_its_time` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-012` | `tests/test_outbox.py` — `test_failed_events_step_aside_and_can_be_requeued` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-013` | `tests/test_outbox.py` — `test_event_id_is_kept` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-014` | `tests/test_outbox.py` — `test_sync_gate_drops_events_while_sync_is_off` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-015` | `tests/test_sync.py` — `test_violation_insert_and_retry_is_a_no_op` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-016` | `tests/test_sync.py` — `test_unknown_camera_is_a_permanent_error` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-017` | `tests/test_sync.py` — `test_gate_in_closes_old_visit_moves_the_link_then_checks_in` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-018` | `tests/test_sync.py` — `test_gate_in_with_unknown_vest_checks_in_without_a_link` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-019` | `tests/test_sync.py` — `test_gate_out_checks_out_and_unlinks` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-020` | `tests/test_sync.py` — `test_drain_marks_rejected_rows_failed_and_keeps_going` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-021` | `tests/test_sync.py` — `test_drain_keeps_the_event_when_the_network_drops` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-022` | `tests/test_sync.py` — `test_reference_refresh_loads_gallery_and_links_only_when_outbox_is_empty` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `SYN-TC-023` | *(new)* covers `Outbox` | `[ ]` not evidenced | — | — |
| `SYN-TC-024` | *(new)* covers `ErpSync` | `[ ]` not evidenced | — | — |
| `SYN-TC-025` | *(new)* covers `ErpSync.process_enrolments` | `[ ]` not evidenced | — | — |
| `SYN-TC-026` | *(new)* covers `Outbox.purge_done` | `[ ]` not evidenced | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```python
def row_event_id(local_id, erp_type): return str(uuid.uuid4())   # the stable-ID TC must FAIL
```

**Red Gate Verification:** *not run. Retrospective tests must be checked with the stub above before the module can move to T3.*

| TC ID | Stub Result | Expected | Actual | Root Cause |
|---|---|---|---|---|
| `SYN-TC-001` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-002` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-003` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-004` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-005` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-006` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-007` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-008` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-009` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-010` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-011` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-012` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-013` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-014` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-015` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-016` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-017` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-018` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-019` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-020` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-021` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-022` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-023` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-024` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-025` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `SYN-TC-026` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |

**Red Gate Evidence:** stub commit `___` · all FAIL? ☐ Yes

---

## 6. Entry / Exit Criteria

### Entry Criteria

- [ ] `STEELSIGHT-DET-SYN-IMP-001` reviewed
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
git checkout -- steelsight/erp/mapping.py steelsight/erp/outbox.py steelsight/erp/sync.py
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
