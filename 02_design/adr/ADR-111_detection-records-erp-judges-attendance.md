# ADR-111 — Detection records every crossing; the ERP judges attendance

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #attendance
**US References:** `US-WRK-001`, `US-WRK-002` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-008` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Lunch decision by the developer on 2026-11-06; minimum-visit and needs-review rules decided on 2026-11-09 and implemented in the ERP. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
A worker leaving for lunch creates extra visits. Attendance can also be gamed by checking in and leaving at once, or by leaving without being recognised.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Filter lunch and short visits in detection | Cleaner ERP data | Business rules on the edge; facts lost |
| B | Detection records every crossing; the ERP applies attendance rules when reading | One owner for the rules; facts are kept | ERP reports must interpret the visits |

## Decision — Quyết định
*We chose option B.* In the ERP, a recorded visit under `MINIMUM_VISIT_MINUTES` (default 10) does not count as attending a shift, and a face visit that is auto-closed with no vest link shows as *needs review*. Hours are unchanged.

## Rationale — Lý do
Rules change more often than crossings do, and only the ERP has shifts.

## Consequences — Hệ quả
**Positive:**
- Rule changes need no change to site software.

**Negative (trade-offs accepted):**
- Vest swapping and deliberate abuse are only partly detectable.

**Risks:**
- An honest double miss (OCR and exit) shows as needs review until someone corrects it.

**Compliance Impact:** ⭐ *CASE 2.0*
- A review flag rather than an automatic penalty avoids an unfair automated decision.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-111:
1. Do NOT suppress crossings in detection for lunch, short stays or shift times.
2. Attendance tags, minimum visit and needs-review logic belong in the ERP.
3. `auto_closed` is set only by the ERP-side close logic and the next-entry close in `gate_in`.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
