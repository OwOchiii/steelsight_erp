# ADR-017 — One leader per team, and one team per leader

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #scheduling #scope
**US References:** `US-ADM-001`, `US-MGR-001`, `US-MGR-005` ⭐ *CASE 2.0*
**BR References:** `BR-SCH-006`, `BR-SCH-007`, `BR-IAM-007`, `BR-RPT-001` ⭐ *CASE 2.0*

> Recorded retrospectively from `V11` (`uq_team_manager`), `TeamService`, `AppUserRepository.findManagersWithoutATeam()`.

---

## Context — Bối cảnh
Shift-planning scope, attendance scope and the monthly report are all "the team I lead". With several teams per manager, the report would need a team picker and the scope rules multiply.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | A manager may lead many teams | Flexible | Report and UI need a team choice; more scope cases |
| B | One leader per team (required, MANAGER role), one team per leader: service check + partial unique index `uq_team_manager` | Simple scope; report scope is unambiguous | A manager over two crews needs two accounts or one merged team |

## Decision — Quyết định
*We chose option B.* `TeamService.create/changeManager` require a leader with the MANAGER role who doesn't lead another team; the leader picker lists only free managers. V11 refuses to apply if any manager already leads two teams. A team can be deleted only when empty; deleted accounts are detached first.

## Rationale — Lý do
Unambiguous scope for planning, attendance and reporting.

## Consequences — Hệ quả
**Positive:**
- `TeamsTicketsAndReportTest.adminSetsUpTeamsWithOneLeaderEach`, `theDatabaseAlsoRefusesALeaderForTwoTeams`.

**Negative (trade-offs accepted):**
- `manager_id` is nullable in the database for legacy rows, though the service always sets it.

**Risks:**
- Deleting or demoting a leader is blocked until the team is reassigned (BR-IAM-007), which an admin must know to do.

**Compliance Impact:** ⭐ *CASE 2.0*
- Limits each manager's view of attendance to their own team.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-021: report scope = the leader's team.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-017:
1. Assign or change a team leader ONLY through TeamService.create/changeManager; the leader MUST have role
   MANAGER and lead no other team.
2. "My team" for a manager is teams.findAllByManagerId(actorId) (at most one); do NOT add multi-team pickers.
3. Team creation, leader change and deletion require USER_MANAGE; the late rule may also be changed by the
   team's own leader.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
