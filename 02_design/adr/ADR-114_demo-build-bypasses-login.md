# ADR-114 — The demo build bypasses sign-in checks

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #security #demo
**US References:** `US-ADM-001` ⭐ *CASE 2.0*
**BR References:** `BR-WEB-002` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`web/app.py` `auth_guard`, `web/routes/live.py` `ws`). Status is `Accepted` for the demo only. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
The demo is built for quick model testing on a trusted machine, and the full sign-in code already exists.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Keep sign-in on | Safe by default | Friction during demos |
| B | `auth_guard` returns before checking, with the original logic left below it | Fast to demo; easy to restore | Anyone who reaches the port has full access |

## Decision — Quyết định
*We chose option B, for the demo build only.*

## Rationale — Lý do
It suits a controlled demo. It is not acceptable for a customer deployment.

## Consequences — Hệ quả
**Positive:**
- The original logic is preserved for restoration.

**Negative (trade-offs accepted):**
- Unreachable code stays in `auth_guard`.

**Risks:**
- `/api/cameras` returns RTSP URLs with passwords to the app window, with no user sign-in (NFR-7 partial).
- The default bind is loopback, but `host` can be changed in `settings.json`, which would expose the unauthenticated API to the network.

**Compliance Impact:** ⭐ *CASE 2.0*
- A customer deployment with this bypass would not meet security-of-processing duties (Luật 91/2025/QH15).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- A later ADR restoring authentication before any customer delivery.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-114:
1. Do NOT ship this bypass to a customer build.
2. Restoring auth means removing the early return in `auth_guard` and restoring the `ws` session check together; the launch-token check (ADR-115) stays.
3. Do NOT remove `AuthStore` while the demo bypass exists.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
