# ADR-115 — Every local request must carry a per-launch token

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #security #desktop
**US References:** `US-ADM-001` ⭐ *CASE 2.0*
**BR References:** `BR-WEB-007` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided by the developer on 2026-11-09, after a local request was shown to be able to delete evidence. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
The desktop window talks to FastAPI on a loopback port. Loopback stops other machines, but any program on the same computer could call it, and the demo build has no sign-in.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Restore user sign-in now | Full access control | Slows demos; a separate decision (ADR-114) |
| B | Replace FastAPI with the pywebview JS bridge | No port at all | Video, events and image serving would all need rebuilding; loses browser mode |
| C | A random token per launch, exchanged for an HttpOnly, SameSite=Strict cookie | Small change; no frontend change; stops other local programs | Not per-user: whoever holds the window has full access |

## Decision — Quyết định
*We chose option C.* `desktop.py` and `run.py` open `/?launch=<token>`. `auth_guard` swaps the token for a cookie and redirects to `/`. Every HTTP request and the `/ws` handshake must then present it.

## Rationale — Lý do
It closes the open local port with very little code, and it stays on when user sign-in is restored.

## Consequences — Hệ quả
**Positive:**
- Other programs on the machine, and web pages in other browsers, cannot use the API.

**Negative (trade-offs accepted):**
- A browser tab left open across an app restart shows a 401 page and must be reopened from the app.
- Viewing the dashboard from another computer is not possible.

**Risks:**
- The token is in the first URL, so it reaches browser history in `run.py` browser mode until the app restarts.

**Compliance Impact:** ⭐ *CASE 2.0*
- Reduces unauthorised local access to worker photos and records (Luật 91/2025/QH15). Does not replace user sign-in.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-115:
1. The launch-token check runs before the demo bypass in `auth_guard`; do NOT move it after that return.
2. `/ws` MUST check the cookie itself, because HTTP middleware does not run for WebSockets.
3. Entry points MUST open `access.launch_url(...)`; do NOT print or log the token.
4. Compare tokens with `access.is_valid` only.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
