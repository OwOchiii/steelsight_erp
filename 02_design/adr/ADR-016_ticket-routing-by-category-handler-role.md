# ADR-016 — Tickets route by category to a handler role: HSE or Admin

**Status:** `Accepted` *(supersedes the V9 team-ticket rule for managers)*
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #workflow #enforcement #scope
**US References:** `US-WRK-004`, `US-HSE-003`, `US-ADM-004` ⭐ *CASE 2.0*
**BR References:** `BR-TKT-001`, `BR-TKT-003`, `BR-TKT-004` ⭐ *CASE 2.0*

> Recorded retrospectively from `V1`, `V9`, `V11__team_leaders_and_ticket_routing.sql` and `TicketService.listQueue()`.

---

## Context — Bối cảnh
Tickets cover very different things: PPE disputes and safety concerns (HSE's job), leave and equipment requests, shift issues, account problems. V1 sent shift and equipment tickets to managers; V9 limited managers to tickets from their own team; on 2026-11-06 the developer decided team leaders shouldn't handle tickets at all.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Every ticket to Admin | One queue | HSE's safety work lost in admin's queue |
| B | Category → handler role (`ticket_category.handler_role_id`); queue = categories my role handles; Admin also has `TICKET_READ_ALL`/`TICKET_UPDATE_ALL` | Data-driven; one place to change; HSE sees only safety | Categories must be curated |
| C | V9: plus team scope for managers | Managers handle their people | More rules; managers didn't want it |

## Decision — Quyết định
*We chose option B.* HSE: *PPE dispute*, *Safety concern*. Admin: *Shift or timetable issue*, *Equipment request*, *Leave request* (new in V11), *Account or access*. V11 removed `TICKET_*_TEAM` permissions and `ticket_from_my_team()`, unassigned managers from open tickets, and restored the V3 shapes of `can_read_ticket`, `can_update_ticket`, `can_delete_ticket`. Admin's queue shows its own categories by default, and every ticket with `?all=true`.

## Rationale — Lý do
Routing lives in data, the permission functions read it, and HSE's queue stays focused on safety.

## Consequences — Hệ quả
**Positive:**
- `TeamsTicketsAndReportTest.safetyTicketsGoToHseAndEverythingElseToAdmin`, `teamLeadersNoLongerHandleTickets`.

**Negative (trade-offs accepted):**
- Managers only open and read their own tickets.

**Risks:**
- A new category needs a migration; there's no admin UI for categories.
- Category names are matched as strings in code (`"PPE dispute"`); renaming one breaks dispute creation.

**Compliance Impact:** ⭐ *CASE 2.0*
- Tickets can hold personal information (leave reasons, photos); only the handling role and the creator read them.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-016:
1. Who handles a ticket is ticket_category.handler_role_id; do NOT hard-code role checks per category in Java.
2. Ticket reads/updates/deletes MUST go through can_read_ticket / can_update_ticket / can_delete_ticket via
   AccessPolicy; queues MUST filter by the actor's role id (findAllByCategoryHandlerRoleId...).
3. New categories are added by a migration INSERT INTO ticket_category; the PPE dispute category name is
   TicketService.PPE_DISPUTE_CATEGORY and must not be renamed.
4. Do NOT reintroduce team-scoped ticket permissions for managers.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
