# ADR-013 — Unclosed visits are auto-closed by a database function the detection service calls

**Status:** `Accepted` *(replaces the V1 grace-period version of the same function)*
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #attendance #scheduling #ha
**US References:** `US-SYS-002`, `US-MGR-003` ⭐ *CASE 2.0*
**BR References:** `BR-ATT-001`, `BR-ATT-006` ⭐ *CASE 2.0*

> Recorded retrospectively from `V1` and `V7__auto_close_after_the_day_ends.sql`, `AutoCloseCheckInTest`.

---

## Context — Bối cảnh
A worker who leaves without passing the gate camera keeps an open visit, and `uq_check_in_open` then blocks their next check-in. The ERP sleeps; the detection service is the process that's awake when workers arrive. V1 closed visits 2 hours after shift end, which cut short people working late.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | A Spring `@Scheduled` job in the ERP | Java | ERP is asleep exactly when it's needed |
| B | V1: close at shift end + 2 h, or check-in + 16 h without a shift | Fast | Cuts off overtime |
| C | V7: `close_stale_check_ins(p_time_zone)`, called by detection on a timer and before each check-in, closes visits whose site-local day (the shift's end day, or the check-in day) has passed, at shift end or site midnight; sets `auto_closed = true` | Works while the ERP sleeps; overtime on the same day is kept; night shifts stay open past midnight | Check-out is an estimate |

## Decision — Quyết định
*We chose option C.* The function is `SECURITY INVOKER`, executable only by `steelsight_detection` (revoked from PUBLIC). Cancelled shifts count as no shift. The close time is at least check-in + 1 minute, so the `check_out_at > check_in_at` check holds for a late arrival.

## Rationale — Lý do
The awake process runs the rule; the rule lives once, in SQL; estimates are flagged so attendance rules don't call them "early" (ADR-012).

## Consequences — Hệ quả
**Positive:**
- `AutoCloseCheckInTest` covers today/yesterday, no shift, late check-in, cancelled shift.

**Negative (trade-offs accepted):**
- HSE must correct estimated check-outs by hand; a correction clears `auto_closed`.

**Risks:**
- The ERP never calls the function; if detection is off for days, open visits pile up (gap G7).
- `p_time_zone` must match `safety_policy.time_zone` and `APP_TIME_ZONE`.

**Compliance Impact:** ⭐ *CASE 2.0*
- Estimated values are marked as such, so pay isn't computed from a guess unknowingly.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Future: ERP-side fallback call on page load or startup.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-013:
1. Auto-closing visits MUST go through close_stale_check_ins(p_time_zone text); do NOT add a Java scheduler that
   updates check_in.check_out_at.
2. Any automatic check-out MUST set auto_closed = true; any human correction MUST set auto_closed = false.
3. A change to the auto-close rule is a new migration that DROPs and recreates the function and re-applies
   REVOKE EXECUTE ... FROM PUBLIC and GRANT EXECUTE ... TO steelsight_detection.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
