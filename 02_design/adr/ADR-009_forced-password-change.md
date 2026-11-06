# ADR-009 — Admin-chosen passwords must be changed at the next sign-in

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #auth #credentials
**US References:** `US-ADM-002`, `US-ALL-001` ⭐ *CASE 2.0*
**BR References:** `BR-IAM-003` ⭐ *CASE 2.0*

> Recorded retrospectively from `V10__admin_only_accounts.sql` (`must_change_password`), `FirstSignInHandler`, `PasswordChangeRequiredFilter`, `UserService`.

---

## Context — Bối cảnh
There is no email-based password reset (email is planned; Render blocks SMTP). An admin creates each account and resets forgotten passwords by typing one, so the admin knows it. The account must not stay usable with a password someone else knows.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Trust users to change it | Nothing to build | Most won't |
| B | Email a one-time reset link | Admin never knows a password | Needs an email API; many workers have no email |
| C | `must_change_password` flag set on create and reset; at sign-in, a session flag sends the user to `/account/password` and a filter keeps them there until they change it | Works without email; cheap (no DB query per request) | Admin still sees the password once |

## Decision — Quyết định
*We chose option C.* `UserService.create()` and `resetPassword()` set `mustChangePassword = true`; `changeOwnPassword()` clears it, requires the current password, and refuses a new one equal to the current. `FirstSignInHandler` sets session attribute `steelsight.mustChangePassword` and redirects; `PasswordChangeRequiredFilter` redirects every other path except `/account/password`, `/logout`, `/login`, `/language`, `/error`, `/actuator/health` and static folders.

## Rationale — Lý do
It works with no email, and costs nothing per request because the flag lives in the session.

## Consequences — Hệ quả
**Positive:**
- `AccountPagesTest.someoneGivenAPasswordByAnAdminMustChooseTheirOwnFirst` proves the lock-in.

**Negative (trade-offs accepted):**
- If an admin resets someone's password while they're signed in, their current session isn't flagged until they sign in again.

**Risks:**
- The bootstrap admin (`AdminBootstrap`) is created without the flag, so the env-var password stays valid until changed by hand (gap G9).

**Compliance Impact:** ⭐ *CASE 2.0*
- Limits shared knowledge of credentials that protect personal data.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Future: email reset through an HTTPS email API.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-009:
1. Any code path where someone other than the user sets a password MUST set mustChangePassword = true.
2. A new page that must stay reachable during a forced change MUST be added to PasswordChangeRequiredFilter.OPEN;
   nothing else may be added there.
3. changeOwnPassword MUST verify the current password and refuse an identical new one; clear the session flag
   PasswordChangeRequiredFilter.SESSION_FLAG only after it succeeds.
4. Passwords are never sent back to the browser: on a failed form, render a fresh PasswordForm/NewAccountForm
   without the password.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
