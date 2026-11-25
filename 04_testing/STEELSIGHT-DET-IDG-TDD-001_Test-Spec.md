# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-DET-IDG-TDD-001`
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
- `03_implement/STEELSIGHT-DET-IDG-IMP-001_TDS.md` — Technical Specification
- ADRs: ADR-104, ADR-105, ADR-106, ADR-111
- `docs/REQUIREMENTS_SPECIFICATION.md`

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `pytest` chưa xanh. Test data is synthetic: no real faces or names.
>
> **Retrospective note & status legend:** these tests were written with or after the code, so RED was never evidenced. 🟢V = re-run on 2026-11-09 after the package refactor (60 passed) · 🔴 = proposed, not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-09` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Identity & Gate |
| `2026-11-17` | `Claude Code (Opus 5.5)` | Added IDG-TC-018 to IDG-TC-039 for ADR-116, 117, 118 and 120, the scanner re-scan window and the gate screen vest column |
| `2026-11-17` | `Claude Code (Opus 5.5)` | Added IDG-TC-040 to IDG-TC-050 for ADR-122 (vest markers) and BR-IDG-016 (reads in a row). IDG-TC-033 now points at `test_number_needs_n_confident_reads_in_a_row` (was `test_two_early_misreads_cannot_beat_the_number_read_as_often`, majority rule removed); IDG-TC-036 at `test_vest_the_erp_does_not_have_is_linked_here_and_cleared_in_the_erp` (was `…_is_refused_before_queueing`, refusal removed) |

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
| **Feature / Gap ID** | `IDG` (existing) |
| **Module** | `IDG — Identity & Gate` |
| **Spec gốc** | `STEELSIGHT-DET-IDG-IMP-001` |
| **Priority** | 🔴 P0 |
| **Sprint** | `Not defined` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `Sensitive-PII` |
| **Compliance Scope** | Luật 91/2025/QH15, NĐ 356/2025 — face vectors are biometric data; DPO review required |
| **Upstream Dependencies** | VIS, InsightFace, EasyOCR, SYN (gallery and links) |
| **Downstream Consumers** | SAF, SYN, WEB |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` |
| **Constraint Source** | `STEELSIGHT-DET-IDG-IMP-001 §17` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Docs cite flat modules such as `steelsight/gate.py` | Code moved to packages ([ADR-101](../02_design/adr/ADR-101_packages-by-purpose-and-routed-web-layer.md)) | Tests import the new paths |
| L2 | Requirements doc said 38 tests | The suite has 60 passing cases | Status is taken from the 60-case run |
| L3 | "Stepping back cancels" reads as always | Only before commit; with face and vest, check-in commits at once and stepping back then records a check-out | IDG-TC-014 (new) |
| L4 | The drawn line looks like a segment | `signed_distance` treats it as infinite | Documented in §5.2 |
| L5 | Retention covers gate photos | `Maintenance` purges only `evidence/`; `attendance/` and `gate_events` grow | Raised for the DPO; REC-TC-006 (new) |
| L6 | Face seen before crossing might not count | `FaceService` keeps identity per track, so a face confirmed before the line counts | Encoded by the fixtures, where identity is present at every step |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
IDG covers: `steelsight/people/*`, `steelsight/gate/*`
Existing test sources: test_gate.py
Runner: pytest, no GPU or database needed
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `DETECTION-MODULE-MAP.md` | BR-IDG-001, BR-IDG-002, BR-IDG-003, BR-IDG-004, BR-IDG-005, BR-IDG-006, BR-IDG-007, BR-IDG-008 |
| ADRs | ADR-104, ADR-105, ADR-106, ADR-111 |
| `REQUIREMENTS_SPECIFICATION.md` | FR-5.3, FR-5.4, FR-5.6, FR-5.7, FR-6.1, FR-6.5, FR-6.7 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|---|---|---|---|
| TC-COND-001 | Sides and dead band | `tests/test_gate.py` — `test_line_sides_and_dead_band` | `IDG-TC-001` |
| TC-COND-002 | Face and vest check in and link | `tests/test_gate.py` — `test_entering_with_face_and_vest_checks_in_and_links` | `IDG-TC-002` |
| TC-COND-003 | Waits for the vest, then checks in without it | `tests/test_gate.py` — `test_entering_waits_for_the_vest_number_then_checks_in_without_it` | `IDG-TC-003` |
| TC-COND-004 | No face, no check-in | `tests/test_gate.py` — `test_entering_without_a_face_records_nothing` | `IDG-TC-004` |
| TC-COND-005 | Check-out by vest number | `tests/test_gate.py` — `test_leaving_by_vest_number_on_the_back_checks_out_and_unlinks` | `IDG-TC-005` |
| TC-COND-006 | Stepping back cancels | `tests/test_gate.py` — `test_stepping_back_over_the_line_cancels` | `IDG-TC-006` |
| TC-COND-007 | Jitter is ignored | `tests/test_gate.py` — `test_jitter_on_the_line_is_ignored` | `IDG-TC-007` |
| TC-COND-008 | Repeats suppressed | `tests/test_gate.py` — `test_repeat_crossing_in_the_same_direction_is_suppressed` | `IDG-TC-008` |
| TC-COND-009 | A handover moves the link | `tests/test_gate.py` — `test_vest_handover_moves_the_link` | `IDG-TC-009` |
| TC-COND-010 | PPE wait off | `tests/test_gate.py` — `test_require_ppe_off_checks_in_at_once_without_a_ppe_note` | `IDG-TC-010` |
| TC-COND-011 | Keeps the crossing time | `tests/test_gate.py` — `test_require_ppe_waits_until_the_hat_goes_on_and_keeps_the_crossing_time` | `IDG-TC-011` |
| TC-COND-012 | Still checks in, with a note | `tests/test_gate.py` — `test_require_ppe_times_out_and_still_checks_in_with_a_note` | `IDG-TC-012` |
| TC-COND-013 | A recognised person who never crosses records nothing | *(new)* covers `GateMonitor.process` | `IDG-TC-013` |
| TC-COND-014 | An `in` crossing after a commit followed by a back-crossing records check-in and check-out | *(new)* covers `GateMonitor` | `IDG-TC-014` |
| TC-COND-015 | `embed_photo` refuses a face matching a different worker | *(new)* covers `FaceService.embed_photo` | `IDG-TC-015` |
| TC-COND-016 | A gate camera with no valid line creates no `GateMonitor` and warns | *(new)* covers `AppState._make_gate` | `IDG-TC-016` |
| TC-COND-017 | Two matching vest reads out of six confirm the number | *(new)* covers `VestService.update` | `IDG-TC-017` |

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
| `FX-101` | Helper | `walk(g, xs, t0, pid, face, vest, vest_worker, dt)` | Drives a track across x positions |
| `FX-102` | Fixture | `gate`: line x=0.5, inside right, real `LinkBook`, `FakeOutbox` | Isolated gate |
| `FX-103` | Helper | `walk_ppe(g, xs, t0, pid, helmet_from, dt)` | PPE timing |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```python
def test_example(engine, cfg):        # fresh database and restored config per test
    cfg(GATE_IDENTIFY_SECONDS=2)
    subject = make_subject(engine)    # a factory inside the test; no module-level mutable state
```

---

### IDG-TC-002 — Face and vest check a worker in and link the vest

**Severity:** `CRITICAL`
**Legal:** `Luật 91/2025/QH15`
**Feature Under Test:** `GateMonitor.process`, `_commit_in`
**Test File:** `tests/test_gate.py` — `test_entering_with_face_and_vest_checks_in_and_links`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-002`
**Oracle Source:** `BR-IDG-003`, `FR-5.6`, `ADR-105`

**Preconditions:** `FX-101`, `FX-102`.

**Test Steps:**
1. `walk(g, [200,300,450,600,700], face="7", vest="012")`
2. Inspect the returned event, outbox item and link

**Expected Result (PASS — hành vi đúng):** one `in` event with `user_id == 7`, `vest_code == "12"`, `employee_code == "W007"`; outbox `gate_in` on lane `db` with the same `event_id`, `face_similarity == 62.0`, `camera_code == "CAM-GATE"` and a `link_event_id`; `links.worker_for("12")["user_id"] == 7`.

**Expected Result (FAIL — dấu hiệu lỗi):** a missed check-in, wrong direction, or a vest left unlinked so zone violations can't be named.

**Current Status:** 🟢V

---

### IDG-TC-004 — No face records nothing

**Severity:** `CRITICAL`
**Feature Under Test:** `GateMonitor._try_commit`
**Test File:** `tests/test_gate.py` — `test_entering_without_a_face_records_nothing`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `BR-IDG-003`

**Expected Result (PASS — hành vi đúng):** after 9 s no event, `out.items == []`, `links.count == 0`.

**Expected Result (FAIL — dấu hiệu lỗi):** a check-in attributed from a vest number alone, which someone else could be wearing.

**Current Status:** 🟢V

---

### IDG-TC-013 — A recognised person who never crosses records nothing

**Severity:** `CRITICAL`
**Feature Under Test:** `GateMonitor.process`
**Test File:** *(new)* `tests/test_gate.py`
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-013`
**Oracle Source:** `ADR-105 §Decision`

**Test Steps:** `walk(g, [200, 220, 240, 220], face="7", vest="12")`, then process past the identify window.

**Expected Result (PASS — hành vi đúng):** no event, no link, no outbox item.

**Expected Result (FAIL — dấu hiệu lỗi):** attendance from a face scan alone.

**Current Status:** 🔴 Not written

---

### IDG-TC-015 — Enrolment refuses a face matching another worker

**Severity:** `CRITICAL`
**CWE:** `CWE-287 — Improper Authentication`
**Legal:** `Luật 91/2025/QH15`
**Feature Under Test:** `FaceService.embed_photo`
**Test File:** *(new)*
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-015`
**Oracle Source:** `FR-5.2`

**Expected Result (PASS = hệ thống an toàn):** `(None, "Khuôn mặt trùng với ...")`.

**Expected Result (FAIL = lỗ hổng tồn tại):** one person enrolled as two workers and can check in for both.

**Current Status:** 🔴 Not written

---

### Full case list

| TC ID | Test | Description / Oracle | Severity | Status |
|---|---|---|---|---|
| IDG-TC-001 | `tests/test_gate.py` — `test_line_sides_and_dead_band` | Sides and dead band — `BR-IDG-002` | MEDIUM | 🟢V |
| IDG-TC-002 | `tests/test_gate.py` — `test_entering_with_face_and_vest_checks_in_and_links` | Face and vest check in and link — `BR-IDG-003` | CRITICAL | 🟢V |
| IDG-TC-003 | `tests/test_gate.py` — `test_entering_waits_for_the_vest_number_then_checks_in_without_it` | Waits for the vest, then checks in without it — `FR-5.6` | HIGH | 🟢V |
| IDG-TC-004 | `tests/test_gate.py` — `test_entering_without_a_face_records_nothing` | No face, no check-in — `BR-IDG-003` | CRITICAL | 🟢V |
| IDG-TC-005 | `tests/test_gate.py` — `test_leaving_by_vest_number_on_the_back_checks_out_and_unlinks` | Check-out by vest number — `BR-IDG-004` | HIGH | 🟢V |
| IDG-TC-006 | `tests/test_gate.py` — `test_stepping_back_over_the_line_cancels` | Stepping back cancels — `BR-IDG-005` | HIGH | 🟢V |
| IDG-TC-007 | `tests/test_gate.py` — `test_jitter_on_the_line_is_ignored` | Jitter is ignored — `BR-IDG-002` | MEDIUM | 🟢V |
| IDG-TC-008 | `tests/test_gate.py` — `test_repeat_crossing_in_the_same_direction_is_suppressed` | Repeats suppressed — `BR-IDG-005` | MEDIUM | 🟢V |
| IDG-TC-009 | `tests/test_gate.py` — `test_vest_handover_moves_the_link` | A handover moves the link — `BR-IDG-006` | HIGH | 🟢V |
| IDG-TC-010 | `tests/test_gate.py` — `test_require_ppe_off_checks_in_at_once_without_a_ppe_note` | PPE wait off — `BR-IDG-008` | MEDIUM | 🟢V |
| IDG-TC-011 | `tests/test_gate.py` — `test_require_ppe_waits_until_the_hat_goes_on_and_keeps_the_crossing_time` | Keeps the crossing time — `ADR-105` | MEDIUM | 🟢V |
| IDG-TC-012 | `tests/test_gate.py` — `test_require_ppe_times_out_and_still_checks_in_with_a_note` | Still checks in, with a note — `BR-IDG-008` | HIGH | 🟢V |
| IDG-TC-013 | *(new)* covers `GateMonitor.process` | A recognised person who never crosses records nothing — `ADR-105` | CRITICAL | 🔴 |
| IDG-TC-014 | *(new)* covers `GateMonitor` | An `in` crossing after a commit followed by a back-crossing records check-in and check-out — `ADR-105` | HIGH | 🔴 |
| IDG-TC-015 | *(new)* covers `FaceService.embed_photo` | `embed_photo` refuses a face matching a different worker — `FR-5.2` | CRITICAL | 🔴 |
| IDG-TC-016 | *(new)* covers `AppState._make_gate` | A gate camera with no valid line creates no `GateMonitor` and warns — `ADR-105` | HIGH | 🔴 |
| IDG-TC-017 | *(new)* covers `VestService.update` | Two matching vest reads out of six confirm the number — `BR-IDG-001` | MEDIUM | 🔴 |
| IDG-TC-018 | `tests/test_faceid_debug.py` — `test_low_score_shows_closest_worker_and_score` | A low score shows the closest worker and is not yet Unknown — `BR-IDG-009` | MEDIUM | 🟢V |
| IDG-TC-019 | `tests/test_faceid_debug.py` — `test_turned_face_is_still_matched_and_can_confirm` | A turned face is matched — `BR-IDG-009` | HIGH | 🟢V |
| IDG-TC-020 | `tests/test_faceid_debug.py` — `test_turned_face_that_fails_does_not_count_as_unknown` | A turned miss is not a stranger — `BR-IDG-009` | HIGH | 🟢V |
| IDG-TC-021 | `tests/test_faceid_debug.py` — `test_frontal_face_that_fails_does_count_as_unknown` | A straight-on miss counts — `BR-IDG-009` | HIGH | 🟢V |
| IDG-TC-022 | `tests/test_gallery_names.py` — `test_several_angles_of_one_worker_match_by_the_closest_one` | Best match over all angles; workers counted once — `BR-IDG-010` | HIGH | 🟢V |
| IDG-TC-023 | `tests/test_gallery_cache.py` — `test_offline_start_matches_like_online` | Offline start matches like online — `BR-SYN-012` | HIGH | 🟢V |
| IDG-TC-024 | `tests/test_gallery_cache.py` — `test_each_sync_replaces_the_copy_so_erased_workers_disappear` | Erased workers leave the copy — `BR-SYN-012` | CRITICAL | 🟢V |
| IDG-TC-025 | `tests/test_gallery_cache.py` — `test_vectors_from_another_model_are_not_loaded` | Other-model vectors ignored — `ADR-117` | HIGH | 🟢V |
| IDG-TC-026 | `tests/test_vest_ocr.py` — `test_no_vest_box_still_queues_upper_body_when_loose` | No vest box still reads, to the bottom of the person — `BR-IDG-011` | HIGH | 🟢V |
| IDG-TC-027 | `tests/test_vest_ocr.py` — `test_strict_mode_needs_a_vest_box` | Strict mode needs a vest box — `ADR-118` | MEDIUM | 🟢V |
| IDG-TC-028 | `tests/test_vest_ocr.py` — `test_vest_box_crop_is_still_preferred` | Vest box crop preferred — `ADR-118` | MEDIUM | 🟢V |
| IDG-TC-029 | `tests/test_vest_ocr.py` — `test_known_face_is_cut_off_so_it_cannot_be_read_as_zero` | Crop starts below the chin — `BR-IDG-011` | HIGH | 🟢V |
| IDG-TC-030 | `tests/test_vest_ocr.py` — `test_zero_reads_are_ignored_and_the_real_number_wins` | Zero-only reads ignored — `BR-IDG-011` | HIGH | 🟢V |
| IDG-TC-031 | `tests/test_vest_ocr.py` — `test_debug_mode_shows_every_candidate_and_keeps_only_recent_crops` | OCR diagnostics — `ADR-118` | LOW | 🟢V |
| IDG-TC-032 | `tests/test_vest_ocr.py` — `test_camera_clock_band_is_greyed_out_before_ocr` | Camera text bands blanked — `BR-IDG-011` | HIGH | 🟢V |
| IDG-TC-033 | `tests/test_vest_ocr.py` — `test_number_needs_n_confident_reads_in_a_row` | 3 confident reads in a row confirm — `BR-IDG-016` | HIGH | 🟢V |
| IDG-TC-034 | `tests/test_scanner.py` — `test_default_rescan_window_is_15_seconds` | 15 s re-scan window — `BR-IDG-012` | MEDIUM | 🟢V |
| IDG-TC-035 | `tests/test_vest_fix.py` — `test_wrong_ocr_number_is_replaced_locally_and_sent_to_erp` | Correction relinks and queues `vest_fix` — `BR-IDG-013` | CRITICAL | 🟢V |
| IDG-TC-036 | `tests/test_vest_fix.py` — `test_vest_the_erp_does_not_have_is_linked_here_and_cleared_in_the_erp` | Unknown vest linked locally, cleared in the ERP, with a warning — `BR-IDG-013` | HIGH | 🟢V |
| IDG-TC-037 | `tests/test_vest_fix.py` — `test_empty_number_removes_the_vest` | Empty number unlinks — `BR-IDG-013` | MEDIUM | 🟢V |
| IDG-TC-038 | `tests/test_vest_fix.py` — `test_offline_still_queues_with_the_typed_number` | Offline correction still queued — `ADR-120` | MEDIUM | 🟢V |
| IDG-TC-039 | `tests/test_gate_board.py` — `test_rows_numbered_with_names` | Vest column shows the printed number — `BR-IDG-014` | MEDIUM | 🟢V |
| IDG-TC-040 | `tests/test_vest_ocr.py` — `test_fluctuating_reads_never_confirm` | Fluctuating reads never confirm — `BR-IDG-016` | HIGH | 🟢V |
| IDG-TC-041 | `tests/test_vest_ocr.py` — `test_low_confidence_reads_never_confirm` | Reads below 0.90 never confirm — `BR-IDG-016` | HIGH | 🟢V |
| IDG-TC-042 | `tests/test_vest_ocr.py` — `test_confirmed_number_changes_only_after_a_full_run_of_another` | Confirmed number changes only after a full run — `BR-IDG-016` | MEDIUM | 🟢V |
| IDG-TC-043 | `tests/test_vest_marker.py` — `test_reads_straight_turned_and_blurred` | Marker read straight, turned ~50° and blurred — `BR-IDG-015` | CRITICAL | 🟢V |
| IDG-TC-044 | `tests/test_vest_marker.py` — `test_no_marker_reads_nothing_and_never_guesses` | No marker, no number — `ADR-122` | HIGH | 🟢V |
| IDG-TC-045 | `tests/test_vest_marker.py` — `test_id_zero_and_ids_above_the_max_are_ignored` | ID 0 and IDs above the max ignored — `BR-IDG-015` | HIGH | 🟢V |
| IDG-TC-046 | `tests/test_vest_marker.py` — `test_marker_crop_is_the_whole_person_with_a_margin` | Crop is the person box +10% — `BR-IDG-015` | MEDIUM | 🟢V |
| IDG-TC-047 | `tests/test_vest_marker.py` — `test_marker_mode_confirms_after_two_sightings_without_a_vest_box` | 2 sightings confirm, no vest box needed — `BR-IDG-016` | HIGH | 🟢V |
| IDG-TC-048 | `tests/test_vest_marker.py` — `test_setting_switches_the_reader_at_runtime` | Setting switches reader at runtime — `ADR-122` | MEDIUM | 🟢V |
| IDG-TC-049 | `tests/test_vest_marker.py` — `test_printed_page_reads_back_as_its_vest_number` | Generated page reads back as its number — `ADR-122` | HIGH | 🟢V |
| IDG-TC-050 | `tests/test_vest_marker.py` — `test_twenty_cm_does_not_fit_a4_but_fits_a3` | Generator refuses a marker without enough margin — `ADR-122` | LOW | 🟢V |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|---|---|---|---|---|
| `IDG-TC-001` | `tests/test_gate.py` — `test_line_sides_and_dead_band` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-002` | `tests/test_gate.py` — `test_entering_with_face_and_vest_checks_in_and_links` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-003` | `tests/test_gate.py` — `test_entering_waits_for_the_vest_number_then_checks_in_without_it` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-004` | `tests/test_gate.py` — `test_entering_without_a_face_records_nothing` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-005` | `tests/test_gate.py` — `test_leaving_by_vest_number_on_the_back_checks_out_and_unlinks` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-006` | `tests/test_gate.py` — `test_stepping_back_over_the_line_cancels` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-007` | `tests/test_gate.py` — `test_jitter_on_the_line_is_ignored` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-008` | `tests/test_gate.py` — `test_repeat_crossing_in_the_same_direction_is_suppressed` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-009` | `tests/test_gate.py` — `test_vest_handover_moves_the_link` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-010` | `tests/test_gate.py` — `test_require_ppe_off_checks_in_at_once_without_a_ppe_note` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-011` | `tests/test_gate.py` — `test_require_ppe_waits_until_the_hat_goes_on_and_keeps_the_crossing_time` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-012` | `tests/test_gate.py` — `test_require_ppe_times_out_and_still_checks_in_with_a_note` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `IDG-TC-013` | *(new)* covers `GateMonitor.process` | `[ ]` not evidenced | — | — |
| `IDG-TC-014` | *(new)* covers `GateMonitor` | `[ ]` not evidenced | — | — |
| `IDG-TC-015` | *(new)* covers `FaceService.embed_photo` | `[ ]` not evidenced | — | — |
| `IDG-TC-016` | *(new)* covers `AppState._make_gate` | `[ ]` not evidenced | — | — |
| `IDG-TC-017` | *(new)* covers `VestService.update` | `[ ]` not evidenced | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```python
def _crossed(self, *a, **k): raise NotImplementedError   # every crossing TC must FAIL
```

**Red Gate Verification:** *not run. Retrospective tests must be checked with the stub above before the module can move to T3.*

| TC ID | Stub Result | Expected | Actual | Root Cause |
|---|---|---|---|---|
| `IDG-TC-001` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-002` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-003` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-004` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-005` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-006` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-007` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-008` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-009` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-010` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-011` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-012` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-013` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-014` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-015` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-016` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `IDG-TC-017` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |

**Red Gate Evidence:** stub commit `___` · all FAIL? ☐ Yes

---

## 6. Entry / Exit Criteria

### Entry Criteria

- [ ] `STEELSIGHT-DET-IDG-IMP-001` reviewed
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
git checkout -- steelsight/people/faceid.py steelsight/people/vest.py steelsight/people/identity.py steelsight/gate/monitor.py
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
