# ADR-119 — Reference data loads before the queue; violations are sent only from known cameras

**Status:** `Accepted`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #sync #reliability
**US References:** `US-SYS-001`, `US-ADM-003` ⭐ *CASE 2.0*
**BR References:** `BR-SYN-009`, `BR-SYN-010`, `BR-SYN-011` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided on 2026-11-17, after the app showed no faces from the ERP while 679 violations waited and 764 had failed. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Each sync pass sent up to 100 queued events before loading cameras, vests and faces, and a network error while sending aborted the pass, so the gallery never loaded. The queued violations came from cameras with no ERP code: the app fell back to local IDs such as `CAM-537`, which the ERP's empty `camera` table refused permanently.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Keep the order; fix only the cameras | No code change | A long or failing queue can still starve the gallery |
| B | Load reference data first; queue violations only for a camera whose ERP code is set (and known, when the list is loaded); add clear actions | Gallery always loads; no doomed rows; the operator can clean up | Violations from unconfigured cameras stay local |

## Decision — Quyết định
*We chose option B.* `_db_loop` runs `_timers` (reference refresh, stale close, enrolment) before `_drain_db`. `AppState._erp_camera_code` returns the code only when a camera's `code` is set and, if the ERP camera list is loaded, present in it; otherwise the violation is stored locally only, with one dashboard message per camera. `Outbox.clear` and `POST /api/erp/clear` remove rejected events or pending violations; pending attendance is never cleared.

## Rationale — Lý do
Identity data matters more than delivery order, and a row the ERP will refuse should never be queued.

## Consequences — Hệ quả
**Positive:**
- Faces load within seconds of connecting, whatever the queue holds.

**Negative (trade-offs accepted):**
- Violations from a camera without an ERP code never reach the ERP.

**Risks:**
- Offline, the app can't check codes and queues by the typed code.

**Compliance Impact:** ⭐ *CASE 2.0*
- Clearing removes queue entries only; local violation history and evidence stay.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-119:
1. _timers before _drain_db in _db_loop.
2. Queue a violation only when _erp_camera_code returns a code.
3. Never clear pending gate_in, gate_out or vest_fix events.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
