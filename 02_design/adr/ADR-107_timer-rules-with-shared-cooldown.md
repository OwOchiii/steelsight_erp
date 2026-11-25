# ADR-107 — Violations are timers on continued absence, with a cooldown shared by all cameras

**Status:** `Accepted — amended by ADR-123 (cooldown per tracked person, item and camera; SRS BR-44)`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #safety
**US References:** `US-HSE-001`, `US-HSE-002` ⭐ *CASE 2.0*
**BR References:** `BR-SAF-001`, `BR-SAF-002`, `BR-SAF-003`, `BR-SAF-004` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`safety/monitor.py`). IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
One missed detection must not raise a violation, since it costs a worker safety points. The same person must not be reported repeatedly as they walk between cameras.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Report any NoHelmet or NoVest box | Immediate | Very noisy |
| B | Per-track timers on how long a positive item has been unseen, plus a `CooldownBook` shared across cameras | Stable; one report per episode | Delay before reporting |

## Decision — Quyết định
*We chose option B.* Defaults: reminder 1.5 s, violation 10 s, cooldown 60 s. Cooldown keys are the track and, when known, the worker or vest.

## Rationale — Lý do
Absence over time is a better signal than a single frame, and a spoken reminder gives the worker a chance to fix it.

## Consequences — Hệ quả
**Positive:**
- A worker who fixes PPE after the reminder is not reported.

**Negative (trade-offs accepted):**
- Short violations under 10 s are not recorded.

**Risks:**
- Stored confidence can be weak for timer-only violations (FR-4.8 partial).

**Compliance Impact:** ⭐ *CASE 2.0*
- Accuracy of personal data: avoids unfair records against named workers.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-109.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-107:
1. A positive box clears the timer; do NOT raise a violation from a single frame.
2. People under `SAFETY_MIN_PERSON_PX` are not judged.
3. Cooldown MUST be applied through the shared `CooldownBook` across every camera.
4. A hard hat counts as worn only when its centre is in the top `HELMET_HEAD_ZONE` of the person box.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
