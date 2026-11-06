# ADR-010 — Only admins manage accounts

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #enforcement #accounts #biometrics
**US References:** `US-ADM-002`, `US-ADM-003` ⭐ *CASE 2.0*
**BR References:** `BR-IAM-005`, `BR-IAM-006`, `BR-IAM-007` ⭐ *CASE 2.0*

> Supersedes the V1 rule where managers held `USER_UPDATE_TEAM`. Recorded from `V10__admin_only_accounts.sql` and commit `94a2c3c`.

---

## Context — Bối cảnh
V1 let a manager edit workers on the team they ran. Face photos are biometric data, and account changes (role, active status, password) are security-sensitive. The developer decided managers should organise shifts but not change anyone's account.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Keep `USER_UPDATE_TEAM` for managers, except face photos | Managers fix typos themselves | Two levels of account rights to reason about |
| B | Remove `USER_UPDATE_TEAM`; `can_update_user()` = `USER_MANAGE` and target not deleted | One owner of accounts | Admin does every edit |

## Decision — Quyết định
*We chose option B.* V10 deletes `USER_UPDATE_TEAM` and redefines `can_update_user()`. `UserService` requires `USER_MANAGE` for create, role, team, active, password reset, face photo and delete; an admin can't change their own role, deactivate or delete themselves; a team's leader can't be deleted or moved off MANAGER until the team has a new leader. `TeamController.requireAdmin()` also checks before a photo upload is stored, so nobody else can put files in storage.

## Rationale — Lý do
One owner for accounts and biometric data is simpler to audit and test.

## Consequences — Hệ quả
**Positive:**
- `ManagerPagesIntegrationTest.aManagerCannotEditEvenTheirOwnTeam` and `AccountPagesTest.managersAndWorkersCannotManageAccounts`.

**Negative (trade-offs accepted):**
- Admin workload; a single admin is a bottleneck.

**Risks:**
- An admin could deactivate every other admin; there's no "last admin" guard except that one can't deactivate oneself.

**Compliance Impact:** ⭐ *CASE 2.0*
- Narrows who can process biometric data to Admin, supporting purpose limitation and access control (Luật 91/2025/QH15).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-011: Face enrolment.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-010:
1. Every method that changes app_user (other than changeOwnPassword) MUST call access.require(actorId,
   Permissions.USER_MANAGE) or access.requireCanUpdateUser(actorId, userId) first.
2. Do NOT reintroduce USER_UPDATE_TEAM or any manager path to edit accounts.
3. A controller that stores an uploaded face photo MUST check user.can(Permissions.USER_MANAGE) BEFORE calling
   PhotoStorage.store, and the service checks again.
4. Self-protection rules (own role, own deactivation, own deletion) MUST throw BusinessRuleException.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
