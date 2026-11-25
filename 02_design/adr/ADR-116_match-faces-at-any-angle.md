# ADR-116 — Every face is matched at any angle; only straight-on misses count as a stranger

**Status:** `Accepted`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #biometric #recognition
**US References:** `US-WRK-001`, `US-WRK-002` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-001`, `BR-IDG-009`, `BR-IDG-010` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided on 2026-11-17, after the face scanner kept failing to recognise workers. Works with the ERP's [ADR-026](ADR-026_face-photos-from-several-angles.md) (several photos per worker). IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
The scanner camera is about 2.5 m high and looks down at about 30°, so a 1.8 m worker 1.5 m away is seen from about 29° above. Correct matches scored 0.45 to 0.64 against the 0.45 cutoff. A first change skipped faces estimated as turned more than 45°, to stop side views creating false "Unknown" flags; the developer asked to stop skipping, and the evidence agreed: a turned face scores lower, not higher, and confirmation already needs 2 matching frames.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Skip faces turned more than `FACE_MAX_YAW` (the first change) | No embedding cost for side views | Throws away the frames that LEFT/RIGHT photos are meant to match |
| B | Match every face; count a miss toward "Unknown" only when the face is within `FACE_MAX_YAW` | Turned faces can confirm a worker; glancing sideways doesn't make a stranger | Embedding cost for every face found |
| C | Lower the match threshold | Instant | More wrong names as the gallery grows |

## Decision — Quyết định
*We chose option B.* `FaceService._worker` matches every face found and keeps the best score over all of a worker's vectors (any angle). Yaw is estimated from the 5 SCRFD landmarks (`estimate_yaw`). A miss counts toward "Unknown" only when `yaw <= FACE_MAX_YAW` (45°), and "Unknown" needs `FACE_UNKNOWN_HITS` (3) such misses; a match resets them. Unrecognised tracks are re-checked every `FACE_RECHECK_SECONDS` (0.4 s), recognised ones every `FACE_CONFIRMED_RECHECK_SECONDS` (5 s). With `FACE_DEBUG_OVERLAY`, the closest worker, score and yaw are drawn under the face box.

## Rationale — Lý do
It uses every usable frame while keeping the two protections that matter: 2 matching votes before a name, and no stranger flag from a side view.

## Consequences — Hệ quả
**Positive:**
- A worker can be confirmed from the side when a matching angle photo exists.
- The overlay shows why a face isn't recognised (low score, wrong person, or no gallery).

**Negative (trade-offs accepted):**
- More embedding work per person (about 2.5× while unrecognised).

**Risks:**
- The yaw estimate assumes an average nose depth and can be about ±10° off.

**Compliance Impact:** ⭐ *CASE 2.0*
- No change to what biometric data is stored.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-117: an offline copy of the gallery.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-116:
1. Do NOT skip embedding for turned faces; pass no max_yaw to largest_face in the worker.
2. Increment st["misses"] only when face.yaw <= FACE_MAX_YAW; reset it on a match.
3. Set unknown only after FACE_UNKNOWN_HITS misses and only while no worker is confirmed.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
