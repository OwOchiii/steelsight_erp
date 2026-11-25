# ADR-120 — A vest number can be corrected by hand, as an ordered outbox event

**Status:** `Superseded by ADR-123`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #identity #sync #operator
**US References:** `US-WRK-001`, `US-HSE-002` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-013`, `BR-SYN-005` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided on 2026-11-17, after OCR linked the wrong number at check-in. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
When OCR misreads a vest, the wrong vest is linked for the whole visit and the gate screen shows the wrong number.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Correct it in the ERP only | One place | The detection app keeps the wrong link and names violations wrongly |
| B | Correct it in the detection app: relink locally at once and queue a `vest_fix` event | Both sides agree; ordered after the check-in | The gate screen is worker-facing, so anyone there can correct |

## Decision — Quyết định
*We chose option B.* `AppState.fix_vest` (dashboard worker card and gate screen, for visits still on site) relinks in `LinkBook`, updates today's last entry in `gate_events`, and queues `vest_fix {user_id, vest_code, at, link_event_id}`. `ErpSync._vest_fix` ends the old link, inserts the new one (idempotent by `link_event_id`) and sets `check_in.ppe_linking_id` on the open visit; an empty number unlinks. A number the ERP doesn't have is still linked here (the gate screen and zone alerts use it) and queued with `vest_code = NULL`, so the ERP ends the old link instead of keeping a wrong vest; the response carries a warning to register the vest in `ppe_vest` (changed later on 2026-11-17: refusing blocked every correction while `ppe_vest` was empty).

## Rationale — Lý do
It fixes both sides with grants detection already has, and the outbox keeps it after the check-in it corrects.

## Consequences — Hệ quả
**Positive:**
- The right vest is linked for the rest of the visit, locally and in the ERP.

**Negative (trade-offs accepted):**
- Anyone at the gate screen can change a vest number.

**Risks:**
- Until the vest is registered in the ERP, the ERP shows the visit with no vest.

**Compliance Impact:** ⭐ *CASE 2.0*
- No new personal data.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Limit corrections on the gate screen to staff.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-120:
1. Corrections go through AppState.fix_vest only.
2. _vest_fix must be idempotent by link_event_id and update only check_in.ppe_linking_id.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
