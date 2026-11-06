# ADR-012 — Late and early tags are computed on read, with make-up time

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer (`chatlog/ai-log.md` 21:13, 21:37)
**Context tags:** #attendance #derived-data
**US References:** `US-MGR-002`, `US-MGR-003` ⭐ *CASE 2.0*
**BR References:** `BR-ATT-004`, `BR-ATT-005`, `BR-SCH-008` ⭐ *CASE 2.0*

> Recorded retrospectively from `attendance/service/AttendanceRules.java`, `VisitStatus.java`, `V6__team_late_after_minutes.sql`.

---

## Context — Bối cảnh
Managers need late and early tags. Each team sets its own grace period. Lateness within the grace period may be made up at the end of the shift (arrive 08:10 on an 08:00–17:00 shift → leave at 17:10), but make-up is capped at the grace period (arrive 08:40 with 15 minutes grace → late, leave from 17:15). Visits are corrected by HSE and shifts are edited after the fact.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Store `is_late`/`minutes_late` columns, set at check-in | Fast queries | Wrong after a correction, a shift edit or a grace-period change; detection would need the rule too |
| B | Compute in Java when read (`AttendanceRules.assess(visit) → VisitStatus`), never store | Always reflects current data; one copy of the rule | Can't filter or sort by "late" in SQL |
| C | Compute in a SQL view | Queryable | Rule in SQL, harder to unit test; minute rounding awkward |

## Decision — Quyết định
*We chose option B.* `requiredCheckOut = shiftEnd + (late ? grace : lateBy)`, `late = lateBy > grace`; all times truncated to the minute; no tags without a non-cancelled shift; an auto-closed check-out is never "early". Grace comes from `team.late_after_minutes` (0–240) or `steelsight.attendance.late-after-minutes` for workers with no team.

## Rationale — Lý do
Derived values that change with three inputs (visit, shift, team rule) are safest when never stored. Unit tests run without a database (`AttendanceRulesTest`, 13 cases).

## Consequences — Hệ quả
**Positive:**
- A correction or a new team rule changes every page and the monthly report at once.

**Negative (trade-offs accepted):**
- Lists filter "late" in memory after paging.

**Risks:**
- Tags need `check_in.shift_id`; manual check-ins are saved without a shift and detection doesn't link shifts yet, so most visits currently get no tags (gap G1). `TodayRoster` and `MonthlyReportService` partly work around it by matching on time overlap.

**Compliance Impact:** ⭐ *CASE 2.0*
- Attendance tags feed pay; computing from source records supports accuracy (Luật 91/2025/QH15).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Future: link visits to shifts at check-in (detection or a DB trigger).

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-012:
1. Late/early/required-check-out MUST be computed by AttendanceRules.assess(CheckIn) returning VisitStatus;
   do NOT add stored late/early columns or re-implement the rule elsewhere.
2. Compare times truncated to whole minutes; a visit with no shift or a CANCELLED shift has no tags.
3. An auto_closed check-out MUST NOT be judged early; a visit still on site can be late but not early.
4. The grace period comes from Team.lateAfterMinutes, or the steelsight.attendance.late-after-minutes
   property when the worker has no team.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
