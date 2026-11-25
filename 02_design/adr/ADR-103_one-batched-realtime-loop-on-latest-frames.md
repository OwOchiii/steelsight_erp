# ADR-103 — One detector, one batched realtime loop, latest frame only

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #performance #realtime
**US References:** `US-HSE-001` ⭐ *CASE 2.0*
**BR References:** `BR-VIS-001`, `BR-VIS-002` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`engine.py`, `vision/capture.py`, `vision/detector.py`). IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Several cameras share one consumer GPU. Delay is worse than dropped frames for safety alerts.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | One model and thread per camera | Simple isolation | VRAM per model; GPU contention |
| B | One model; a single engine thread batches the newest frame from each camera | One model in VRAM; bounded latency | Slow inference lowers every camera's rate together |

## Decision — Quyết định
*We chose option B.* Capture threads overwrite a one-slot buffer. The engine processes a camera only when its frame version has changed, in chunks of up to `BATCH_MAX` (4).

## Rationale — Lý do
Latest-frame processing cannot build a backlog, and batching uses the GPU efficiently.

## Consequences — Hệ quả
**Positive:**
- No growing delay on live streams.

**Negative (trade-offs accepted):**
- Frames are dropped under load, so short events can be missed.

**Risks:**
- No measured benchmark is recorded for current hardware (NFR-13).

**Compliance Impact:** ⭐ *CASE 2.0*
- None directly.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-104.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-103:
1. The engine loop MUST NOT block on I/O, identity models or the ERP.
2. Capture workers MUST keep only the newest frame; do NOT add frame queues.
3. A failure in one per-camera step is caught and logged so video keeps streaming.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
