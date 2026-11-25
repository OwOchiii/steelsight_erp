# ADR-112 — The web layer polls thread-safe state

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #concurrency
**US References:** `US-HSE-001` ⭐ *CASE 2.0*
**BR References:** `BR-WEB-001` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`engine.py` `EventBus`, `web/routes/live.py`). IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Capture, inference and services are threads; FastAPI streaming and WebSockets are asyncio.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | asyncio queues bridged with `call_soon_threadsafe` | Push latency | Lifetime and deadlock hazards across engine restarts |
| B | Threads write to locked structures and async code polls: MJPEG about 20 times a second, `/ws` every 0.5 s | Simple; engine restarts are safe | Up to 0.5 s event delay |

## Decision — Quyết định
*We chose option B.* `EventBus` is a `deque(maxlen=400)` of `(seq, event)`, and each reader keeps its own `seq`.

## Rationale — Lý do
A short delay is acceptable, and restart safety matters more.

## Consequences — Hệ quả
**Positive:**
- Dashboard, Notifier and Reporter read independently.

**Negative (trade-offs accepted):**
- A slow reader can miss events once 400 newer ones have arrived.

**Risks:**
- `/ws` sends a full snapshot on each tick, which grows with cameras.

**Compliance Impact:** ⭐ *CASE 2.0*
- None directly.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-112:
1. Do NOT pass asyncio queues into engine threads.
2. Publish UI events with `EventBus.push`; readers track their own `seq`.
3. Run blocking `state` operations with `asyncio.to_thread` from async routes.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
