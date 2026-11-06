# ADR-001 — Permission and scope checks live in SQL functions

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer (commit author); Claude Code (Opus 5.5) — AI pair programmer (`chatlog/ai-log.md`)
**Context tags:** #enforcement #authorization #scope
**US References:** `US-MGR-001`, `US-HSE-003`, `US-ADM-002`, `US-ADM-004` ⭐ *CASE 2.0*
**BR References:** `BR-IAM-009`, `BR-SCH-003`, `BR-TKT-003`, `BR-IAM-005` ⭐ *CASE 2.0*

> Recorded retrospectively on 2026-11-06 from `V1__init_schema.sql`, `V3__service_scope_functions.sql`, `V9`–`V11` and `identity/service/AccessPolicy.java`.

---

## Context — Bối cảnh
Each action needs two answers: **may this role do it** (permission), and **on which records** (scope). A manager may edit shifts, but only for the team they lead; HSE may resolve tickets, but only in the categories HSE handles. The scope rules join several tables (`team.manager_id`, `ticket_category.handler_role_id`, `app_user.role_id`). The rules changed three times in two days (V9 gave managers team tickets, V10 took account edits away from managers, V11 took team tickets away again), so a second copy of them anywhere would drift. The Python detection service shares the database, and the schema design wanted the rules readable by anyone looking at the database.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Spring Security annotations (`@PreAuthorize`) with permission authorities only | Idiomatic; no DB call | Can't express scope ("team I lead") without custom code; checks scattered over controllers |
| B | Scope logic in Java service methods, querying repositories | Type-safe, unit-testable without a DB | Logic duplicated across services; easy to forget a branch; a second copy if SQL ever needs it |
| C | `has_permission()` and `can_*()` SQL functions, called from one Java facade (`AccessPolicy`) at the top of every service method | One copy of the rules; changed by migration with history; testable in SQL; same rule for every caller | One DB round-trip per check; logic in SQL, less familiar to Java reviewers |

## Decision — Quyết định
*We chose option C.* Every service method starts with an `AccessPolicy` call (`require`, `requireAny`, `requireCan*`), which runs `SELECT has_permission(?, ?)` or `SELECT can_<rule>(?, ?)`. A `false` answer throws `AccessDeniedException` (403).

## Rationale — Lý do
Option C keeps one copy of rules that changed often, and each change is a migration with its own history (`V9` → `V11` shows the team-ticket rule added and removed). `has_permission()` also checks `is_active` and `deleted_at`, so a deactivated user loses every right on their next request, not just at the next sign-in. The cost, one small indexed query per check, is acceptable at this scale (one site, tens of users).

## Consequences — Hệ quả
**Positive:**
- Rules are changed in one place, by migration, and their history is in `db/migration/`.
- Deactivation takes effect at once, because `has_permission()` reads `is_active` on every call.
- `RepositoryIntegrationTest` and `ServiceIntegrationTest` test the rules against a real PostgreSQL.

**Negative (trade-offs accepted):**
- Several DB round-trips per page (e.g. `TicketService.listPossibleAssignees` calls `can_update_ticket` once per candidate).
- Services can't be unit-tested without a database; every service test needs Testcontainers.
- Spring authorities on `AuthenticatedUser` are a snapshot from sign-in, used only to show or hide UI; they are **not** the enforcement point. Templates and `HomeController` must never be the only check.

**Risks:**
- A new service method that forgets its `AccessPolicy` call has no other guard (except `PasswordChangeRequiredFilter` and `authenticated()`). Mitigation: §AI Prompt Constraint below and review.
- Some methods load the record before checking (`ViolationService.get`, `TeamService.setLateAfterMinutes`), so 404 vs 403 reveals which IDs exist (gap G8).

**Compliance Impact:** ⭐ *CASE 2.0*
- Supports least privilege and access control over personal data under Luật 91/2025/QH15 (Bảo vệ dữ liệu cá nhân) and NĐ 356/2025: access to worker records, check-ins and violations is scoped by role and team. DPO review pending.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-010: Only admins manage accounts (a one-line change to `can_update_user()`).
- ADR-016: Ticket routing by category handler role.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-001:
1. Every public service method MUST start with an AccessPolicy call (require, requireAny, requireCanUpdateUser,
   requireCanEditShift, requireCanAssignShift, requireCanReadTicket, requireCanUpdateTicket, requireCanDeleteTicket)
   BEFORE loading the target record.
2. Do NOT re-implement a scope rule in Java or in a JPQL WHERE clause as the access check. If a new scope is
   needed, add a SQL function can_<verb>_<noun>(p_user bigint, p_target bigint) RETURNS boolean in a new Flyway
   migration, built on has_permission(), and add a method to AccessPolicy.
3. Do NOT use @PreAuthorize, AuthenticatedUser.can() or template checks as the enforcement point; they only
   decide what the UI shows.
4. The acting user is always AuthenticatedUser.getUserId() from @AuthenticationPrincipal, passed as the first
   argument (actorId) to the service. Never take the actor from a request parameter.
5. Permission codes come from identity.service.Permissions constants and must exist in the permission table.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
