# ADR-008 — Session sign-in by employee code, BCrypt, in-memory sign-in limiter

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #auth #brute-force
**US References:** `US-ALL-001` ⭐ *CASE 2.0*
**BR References:** `BR-IAM-001`, `BR-IAM-002`, `BR-IAM-004` ⭐ *CASE 2.0*

> Recorded retrospectively from `security/SecurityConfig.java`, `SignInLimiter.java`, `SignInLimitFilter.java`, commit `d4f978f` ("Add production deployment and sign-in safeguards").

---

## Context — Bối cảnh
Workers sign in on shared or personal phones, by employee code (many have no email). Employee codes are short and guessable, and the site is public on the internet (Render). There's one instance on a free tier, with no Redis.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Spring Security form login with session cookie, no limit | Built in | Online password guessing is unlimited |
| B | Form login + per-address limit | Simple | `X-Forwarded-For` can be faked behind a proxy, dodging an address-only limit |
| C | Form login + a Caffeine counter keyed both by (code, address) — 5 failures — and by code across all addresses — 20 failures — for 15 minutes, checked before the password | Stops guessing even with faked addresses; a stranger needs 4× more failures to lock someone out than to lock themselves | Counts lost on restart; not shared across instances |
| D | JWT + external rate limiter | Scales out | Overkill; token revocation; more infrastructure |

## Decision — Quyết định
*We chose option C.* `formLogin` with `usernameParameter("employeeCode")`, `BCryptPasswordEncoder`, `SameSite=Lax` session cookie (`Secure` in prod). `SignInLimitFilter` runs before `UsernamePasswordAuthenticationFilter` on `POST /login` and redirects to `/login?locked` when locked. `SignInLimiter` listens to authentication success and failure events; codes are normalised (trimmed, upper-cased, truncated to 64) and the cache is capped at 100 000 entries.

## Rationale — Lý do
It defeats guessing behind a proxy without new infrastructure, and bounds memory against junk codes.

## Consequences — Hệ quả
**Positive:**
- `DeploymentEndpointsTest.repeatedWrongPasswordsLockTheSignInEvenWithTheRightPassword` proves the lock; `SignInLimiterTest` covers spoofed addresses and spelling variants.

**Negative (trade-offs accepted):**
- A restart (including Render's spin-down) clears all counts and signs everyone out (gap G6).
- A determined attacker can lock a known code for 15 minutes with 20 failures (accepted denial-of-service for that one account).

**Risks:**
- Scaling to two instances would split the counts; an external store would be needed.

**Compliance Impact:** ⭐ *CASE 2.0*
- Access control to personal data (Luật 91/2025/QH15 security measures). Passwords are only stored as BCrypt hashes and are never logged (`SignInLog`).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-009: Forced password change after an admin sets it.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-008:
1. Users authenticate ONLY through Spring Security formLogin at POST /login with parameters employeeCode and
   password; the principal is AuthenticatedUser loaded by AppUserDetailsService.
2. Passwords MUST be hashed with the PasswordEncoder bean (BCrypt); NEVER log, return or store a raw password.
3. Do NOT bypass SignInLimitFilter; any new sign-in path MUST call SignInLimiter.isLocked() before checking the
   password and record success/failure events.
4. Log typed employee codes only after replacing \r \n \t (log forging); never log the password.
5. Do NOT add JWT, remember-me or a second authentication mechanism for pages without a new ADR.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
