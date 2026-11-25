# ADR-104 — Face and OCR models run off the detection loop

**Status:** `Accepted — amended by ADR-123 (only the face model remains)`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #performance #biometrics
**US References:** `US-WRK-001` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-001` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`people/faceid.py`, `people/vest.py`). IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
InsightFace and EasyOCR are much slower than YOLO. Calling them inline would stall every camera.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Inline in the engine loop | Simple | Stalls detection |
| B | Own worker threads with bounded queues; at most one pending job per track; the update call returns the latest known result | Detection never waits | Identity arrives some frames late |

## Decision — Quyết định
*We chose option B.* Face queue size 16; OCR queue size 8. A full queue skips the crop for that frame. Two matching votes confirm an identity.

## Rationale — Lý do
Voting tolerates a late or bad frame, and detection speed is the priority.

## Consequences — Hệ quả
**Positive:**
- Detection and safety rules continue if either model is missing or failing.

**Negative (trade-offs accepted):**
- The gate must wait up to `GATE_IDENTIFY_SECONDS` for identity after a crossing.

**Risks:**
- A very fast walker may not give enough crops for two votes.

**Compliance Impact:** ⭐ *CASE 2.0*
- Face vectors are biometric data (Luật 91/2025/QH15); they are processed in memory and stored by the ERP, not in local SQLite.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-105, ADR-106.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-104:
1. `update()` on an identity service MUST only queue work (`put_nowait`) and return current state.
2. Do NOT call InsightFace or EasyOCR from the engine thread.
3. Keep at most one pending job per track.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
