# ADR-018 — Shift overlap blocked by an exclusion constraint; site-local days

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #scheduling #integrity #time
**US References:** `US-MGR-001`, `US-WRK-001` ⭐ *CASE 2.0*
**BR References:** `BR-SCH-001`, `BR-SCH-002`, `BR-SCH-005`, `BR-PLT-005` ⭐ *CASE 2.0*

> Recorded retrospectively from `V1` (`EXCLUDE USING gist`), `SiteTime.java`, `ShiftGrid.java`, `ShiftRepository` `[from, to)` queries.

---

## Context — Bối cảnh
Two planners (a leader and an admin) can give one worker overlapping shifts at the same moment; an application check-then-insert can't stop that race. Night shifts cross midnight; a week view must put each shift in exactly one cell. Users enter local times (Vietnam), the server and database run in UTC.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Java overlap query before insert | Clear message | Race between two planners |
| B | `EXCLUDE USING gist (user_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (status <> 'CANCELLED')` with `btree_gist`; Java translates the violation | Race-proof; cancelled shifts free their time | Needs an extension |

## Decision — Quyết định
*We chose option B.* Times are stored as `timestamptz` (UTC by `hibernate.jdbc.time_zone`), converted by `SiteTime` (`steelsight.time-zone`, default `Asia/Ho_Chi_Minh`) on input and display. A shift belongs to the local day it **starts** on; range queries are half-open `[from, to)` so a midnight shift falls in one week only. Weeks start on Monday.

## Rationale — Lý do
The constraint holds for every writer and every race; half-open ranges remove double counting.

## Consequences — Hệ quả
**Positive:**
- `ServiceIntegrationTest.overlappingShiftIsARuleErrorAndCancellingFreesTheTime`, `ShiftOverlapPageTest`, `ManagerPagesIntegrationTest.anOvernightShiftBelongsToTheDayItStartsAndAMidnightOneToTheNextWeek`.

**Negative (trade-offs accepted):**
- Two time-zone settings must agree: `APP_TIME_ZONE` and `safety_policy.time_zone`.

**Risks:**
- Older repository methods use inclusive `BETWEEN` (`findAllByStartAtBetween`, `findAllByTeamManagerIdAndDateRange`); they're used by `ShiftService.listAll/listTeam`, which pages don't call today.

**Compliance Impact:** ⭐ *CASE 2.0*
- N/A beyond accuracy of working-time records.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-018:
1. Do NOT add a Java overlap check as the guard; rely on the exclusion constraint and translate
   DataIntegrityViolationException to BusinessRuleException("This worker already has a shift at that time").
2. Convert every user-entered LocalDateTime with SiteTime.toInstant and every displayed time with SiteTime /
   Display; never use the JVM default zone.
3. Week and day queries MUST be half-open [from, to) using SiteTime.startOf(date); use StartAtGreaterThanEqual
   ...StartAtLessThan repository methods, not Between.
4. A shift belongs to the day it starts on.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
