# ADR-021 — Monthly report as a streamed XLSX in the reader's language

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #reporting #pay-data
**US References:** `US-MGR-005`, `US-ADM-005` ⭐ *CASE 2.0*
**BR References:** `BR-RPT-001`…`BR-RPT-006` ⭐ *CASE 2.0*

> Recorded retrospectively from `reporting/service/MonthlyReportService.java`, `web/MonthlyReportWorkbook.java`, `web/ReportController.java`.

---

## Context — Bối cảnh
Team leaders send each month's safety and attendance figures to accounting, which works out pay and bonuses in spreadsheets. The server has 512 MB of RAM.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | CSV | Trivial | One sheet; encoding trouble with Vietnamese in Excel |
| B | Apache POI XSSF | Full featured | Heavy in memory |
| C | fastexcel (streaming writer), four sheets (summary, violations, attendance, no-shows), labels in the downloader's language | Small memory; native Excel types | Fewer formatting features |

## Decision — Quyết định
*We chose option C.* `GET /team/report?month=YYYY-MM` (default last month). Scope: `REPORT_EXPORT_ALL` → every WORKER; else `REPORT_EXPORT_TEAM` → the team the actor leads. Response `Cache-Control: no-store`, ASCII file name `steelsight-<team-slug>-<YYYY-MM>.xlsx`, download logged. Dates are written in site time (Excel has no time zone).

## Rationale — Lý do
Accounting already uses Excel; streaming keeps memory flat on the free tier.

## Consequences — Hệ quả
**Positive:**
- `TeamsTicketsAndReportTest.aLeaderExportsTheirTeamsMonthWithTheRightFigures` reads the file back with fastexcel-reader.

**Negative (trade-offs accepted):**
- The report recomputes everything on each download; no snapshot is kept of what was sent.

**Risks:**
- Attendance tags depend on `shift_id` links (gap G1); "worked" and "no-show" use overlap instead.

**Compliance Impact:** ⭐ *CASE 2.0*
- The file contains personal data used for pay: no caching, logged downloads, scope limited to the leader's team. Retention of downloaded files is outside the system: **DPO guidance needed.**

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-021:
1. Report data MUST be built by MonthlyReportService.build(actorId, month), which decides scope; the controller
   only parses the month and writes the file.
2. Scores come from safety_scores(end of period); attendance tags from CheckInService.listStartedInScope /
   AttendanceRules; do NOT duplicate those rules in report code.
3. Responses carrying the report MUST set Cache-Control: no-store and Content-Disposition attachment with an
   ASCII file name.
4. Sheet labels MUST come from message keys so the file is in the reader's language.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
