# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-DET-VIS-TDD-001`
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
- `03_implement/STEELSIGHT-DET-VIS-IMP-001_TDS.md` — Technical Specification
- ADRs: ADR-103, ADR-104
- `docs/REQUIREMENTS_SPECIFICATION.md`

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `pytest` chưa xanh. Test data is synthetic: no real faces or names.
>
> **Retrospective note & status legend:** these tests were written with or after the code, so RED was never evidenced. 🟢V = re-run on 2026-11-09 after the package refactor (60 passed) · 🔴 = proposed, not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-09` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Vision & Engine |

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
| **Feature / Gap ID** | `VIS` (existing) |
| **Module** | `VIS — Vision & Engine` |
| **Spec gốc** | `STEELSIGHT-DET-VIS-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` |
| **Compliance Scope** | Luật 91/2025/QH15 — video of identifiable workers; frames are held in memory |
| **Upstream Dependencies** | Cameras, ultralytics, ONNX Runtime, TensorRT |
| **Downstream Consumers** | SAF, IDG, WEB |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` |
| **Constraint Source** | `STEELSIGHT-DET-VIS-IMP-001 §17` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Docs cite flat modules such as `steelsight/gate.py` | Code moved to packages ([ADR-101](../02_design/adr/ADR-101_packages-by-purpose-and-routed-web-layer.md)) | Tests import the new paths |
| L2 | Requirements doc said 38 tests | The suite has 60 passing cases | Status is taken from the 60-case run |
| L3 | Reconnect "every 2 s" | `_reconnect` waits 20 × 0.1 s and reopens, with no retry limit | VIS-TC proposals use 2 s |
| L4 | Comment in `engine.py` says 3 s timers | Timers are configured, 10 s by default | Tests use config values |
| L5 | `Engine.open_video` is described as a feature | No route calls it | Not tested through the API |
| L6 | Model failure could look like a crash | If all candidates fail, `ready` is false, `on_ready` still fires and the engine runs on empty detections | VIS-TC-005 scope |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
VIS covers: `steelsight/engine.py`, `steelsight/vision/*`
Existing test sources: test_engine_flow.py
Runner: pytest, no GPU or database needed
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `DETECTION-MODULE-MAP.md` | BR-VIS-001, BR-VIS-002, BR-VIS-003, BR-VIS-004, BR-VIS-005 |
| ADRs | ADR-103, ADR-104 |
| `REQUIREMENTS_SPECIFICATION.md` | FR-1.1, FR-1.2, FR-1.3, FR-2.3, FR-2.5, FR-3.1 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|---|---|---|---|
| TC-COND-001 | Gate and zone cameras run through one engine flow | `tests/test_engine_flow.py` — `test_gate_camera_checks_in_and_zone_camera_reports_a_named_violation` | `VIS-TC-001` |
| TC-COND-002 | A slower reader sees the newest frame, not a backlog | *(new)* covers `CaptureWorker.read_latest` | `VIS-TC-002` |
| TC-COND-003 | An unmatched track is dropped after `TRACK_MAX_AGE` frames | *(new)* covers `SimpleTracker.update` | `VIS-TC-003` |
| TC-COND-004 | Overlap below 0.3 starts a new track | *(new)* covers `SimpleTracker.update` | `VIS-TC-004` |
| TC-COND-005 | A detector exception yields empty detections and the loop continues | *(new)* covers `Engine` | `VIS-TC-005` |
| TC-COND-006 | `.engine` without CUDA loads the same-name `.pt` | *(new)* covers `Detector` | `VIS-TC-006` |

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
| `FX-101` | Fake | `FakeDetector` with classes Helmet, NoHelmet, Vest, NoVest, Person | Engine wiring without a GPU |
| `FX-102` | Generated | `numpy.zeros((1000,1000,3))` frames | No real images |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```python
def test_example(engine, cfg):        # fresh database and restored config per test
    cfg(GATE_IDENTIFY_SECONDS=2)
    subject = make_subject(engine)    # a factory inside the test; no module-level mutable state
```

---

### VIS-TC-001 — Gate and zone cameras run through one engine flow

**Severity:** `HIGH`
**Feature Under Test:** `Engine._process`
**Test File:** `tests/test_engine_flow.py` — `test_gate_camera_checks_in_and_zone_camera_reports_a_named_violation`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-IDG-007`, `ADR-106`

**Preconditions:** two cameras (`CAM-GATE` with a line, `CAM-B2`), `HELMET_MISSING_SECONDS=0`, fake services; `FX-101`, `FX-102`.

**Test Steps:**
1. Walk a person from x=200 to x=720 in 20 px steps on camera 0
2. Assert `face.calls` equals `[0] * steps`, `gate_in` was queued, and vest 12 links to worker 7
3. Process a NoHelmet box on camera 1

**Expected Result (PASS — hành vi đúng):** face calls unchanged; violation is `MISSING_HELMET`, with `camera_code == "CAM-B2"`, `frame_size == (1000, 1000)`, `identity_source == "vest"`, and stored.

**Expected Result (FAIL — dấu hiệu lỗi):** biometric processing runs on a zone camera, or a violation has no camera code.

**Current Status:** 🟢V

---

### VIS-TC-005 — An inference exception keeps the loop running

**Severity:** `HIGH`
**Feature Under Test:** `Engine._loop`, `Detector.predict_batch`
**Test File:** *(new)*
**TDD Phase:** 🔴 RED — chưa implement
**Condition Ref:** `TC-COND-005`
**Oracle Source:** `NFR-1`

**Test Steps:** make the fake detector raise; run one loop step; assert a raw frame is still encoded and no exception escapes.

**Expected Result (PASS — hành vi đúng):** empty detections, video continues.

**Expected Result (FAIL — dấu hiệu lỗi):** the engine thread dies and every camera freezes.

**Current Status:** 🔴 Not written

---

### Full case list

| TC ID | Test | Description / Oracle | Severity | Status |
|---|---|---|---|---|
| VIS-TC-001 | `tests/test_engine_flow.py` — `test_gate_camera_checks_in_and_zone_camera_reports_a_named_violation` | Gate and zone cameras run through one engine flow — `BR-IDG-007` | HIGH | 🟢V |
| VIS-TC-002 | *(new)* covers `CaptureWorker.read_latest` | A slower reader sees the newest frame, not a backlog — `BR-VIS-001` | HIGH | 🔴 |
| VIS-TC-003 | *(new)* covers `SimpleTracker.update` | An unmatched track is dropped after `TRACK_MAX_AGE` frames — `BR-VIS-004` | MEDIUM | 🔴 |
| VIS-TC-004 | *(new)* covers `SimpleTracker.update` | Overlap below 0.3 starts a new track — `BR-VIS-004` | MEDIUM | 🔴 |
| VIS-TC-005 | *(new)* covers `Engine` | A detector exception yields empty detections and the loop continues — `NFR-1` | HIGH | 🔴 |
| VIS-TC-006 | *(new)* covers `Detector` | `.engine` without CUDA loads the same-name `.pt` — `BR-VIS-003` | MEDIUM | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|---|---|---|---|---|
| `VIS-TC-001` | `tests/test_engine_flow.py` — `test_gate_camera_checks_in_and_zone_camera_reports_a_named_violation` | `[ ]` not evidenced | `57283ce` + refactor (re-run 2026-11-09) | Package refactor: import paths only |
| `VIS-TC-002` | *(new)* covers `CaptureWorker.read_latest` | `[ ]` not evidenced | — | — |
| `VIS-TC-003` | *(new)* covers `SimpleTracker.update` | `[ ]` not evidenced | — | — |
| `VIS-TC-004` | *(new)* covers `SimpleTracker.update` | `[ ]` not evidenced | — | — |
| `VIS-TC-005` | *(new)* covers `Engine` | `[ ]` not evidenced | — | — |
| `VIS-TC-006` | *(new)* covers `Detector` | `[ ]` not evidenced | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```python
def iou(a, b): return 1.0   # every box matches; the tracker TCs must FAIL
```

**Red Gate Verification:** *not run. Retrospective tests must be checked with the stub above before the module can move to T3.*

| TC ID | Stub Result | Expected | Actual | Root Cause |
|---|---|---|---|---|
| `VIS-TC-001` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `VIS-TC-002` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `VIS-TC-003` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `VIS-TC-004` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `VIS-TC-005` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |
| `VIS-TC-006` | Stub below | 🔴 FAIL | ☐ FAIL ☐ PASS |  |

**Red Gate Evidence:** stub commit `___` · all FAIL? ☐ Yes

---

## 6. Entry / Exit Criteria

### Entry Criteria

- [ ] `STEELSIGHT-DET-VIS-IMP-001` reviewed
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
git checkout -- steelsight/engine.py steelsight/vision/capture.py steelsight/vision/detector.py steelsight/vision/tracker.py steelsight/vision/render.py steelsight/vision/optimizer.py steelsight/vision/discovery.py
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
