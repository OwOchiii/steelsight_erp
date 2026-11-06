# ADR-014 — The safety score is computed by SQL, never stored; level notices come from a trigger

**Status:** `Accepted`
**Date:** 2026-11-05
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer (`chatlog/ai-log.md` 2026-11-05 15:47)
**Context tags:** #safety #derived-data #propagation
**US References:** `US-WRK-002`, `US-WRK-005`, `US-MGR-004`, `US-HSE-005` ⭐ *CASE 2.0*
**BR References:** `BR-PPE-005`, `BR-PPE-006`, `BR-PPE-008`, `BR-NTF-001` ⭐ *CASE 2.0*

> Recorded retrospectively from `V8__safety_score_and_notifications.sql`, `SafetyScoreService.java`, `SafetyScoreTest`.

---

## Context — Bối cảnh
Each worker starts the month on 100 and loses 10 points per counted violation; warning at ≤ 70 and danger at ≤ 40, with a notification. A camera reports someone standing in view again and again, so repeats must not stack. The month resets on the 1st in site time. Overturned violations must give points back. The detection service writes violations straight to the database while the ERP may be asleep, yet notifications must still go out.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Stored score column, decremented by the ERP, reset by a monthly job | Fast reads | ERP asleep → missed decrements and resets; overturn must add back exactly |
| B | `safety_scores(p_at)` SQL function computes from `violation` rows; an `AFTER INSERT OR UPDATE OF user_id, dispute_status` trigger (`SECURITY DEFINER`) inserts the level notification | Always correct; resets itself; works while the ERP sleeps; one copy used by Java and the trigger | Computed on every read |
| C | Computed in Java | Testable in Java | Trigger can't use it; ERP asleep → no notices |

## Decision — Quyết định
*We chose option B.* `safety_policy` (single row) holds `points_per_violation=10`, `warning_at=70`, `danger_at=40`, `repeat_limit=30 min`, `time_zone`. A hit is a distinct `(violation_type, date_bin(repeat_limit, detected_at))`; `dispute_status = 'OVERTURNED'` is excluded. Only WORKER-role, non-deleted users get a row. The trigger inserts `SAFETY_WARNING`/`SAFETY_DANGER` with `ON CONFLICT (user_id, kind, period) DO NOTHING`, so each level reaches a worker once a month. `safety_time_slots(from, to)` ranks shift slots by violations per 100 worker-hours. Java reads both through `JdbcTemplate`.

## Rationale — Lý do
The score is a pure function of the violation rows, so storing it would only create ways to be wrong.

## Consequences — Hệ quả
**Positive:**
- Resets at site midnight on the 1st with no job; overturns refund automatically (`SafetyScoreTest`).
- The detection role can insert a violation and the notice still appears, because the trigger runs as the owner.

**Negative (trade-offs accepted):**
- `listWorkers` computes every worker's score per page; fine for one site.
- A disputed violation keeps costing points until decided.

**Risks:**
- `SECURITY DEFINER` functions must keep `SET search_path = public, pg_temp` to avoid search-path hijack.
- `safety_policy.time_zone` must match `APP_TIME_ZONE`.

**Compliance Impact:** ⭐ *CASE 2.0*
- The score affects pay and bonuses; it's derived transparently from records the worker can see and dispute (right to accuracy and to object, Luật 91/2025/QH15).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-022: notifications store no text.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-014:
1. Read scores ONLY through SafetyScoreService (SELECT ... FROM safety_scores(?)); do NOT add a stored score
   column or compute the score in Java.
2. Score rules (points, thresholds, repeat window, time zone) live in the safety_policy row; do NOT hard-code
   them in Java or templates.
3. Level notifications are written ONLY by the notify_safety_level() trigger; Java must not insert
   SAFETY_WARNING/SAFETY_DANGER notifications.
4. Any SECURITY DEFINER function MUST declare SET search_path = public, pg_temp.
5. Only WORKER-role users have a score; callers must handle a null Score for other roles.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
