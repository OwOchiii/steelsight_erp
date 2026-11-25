# ADR-108 — ERP writes go through a local SQLite outbox with ordered lanes

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #propagation #ha
**US References:** `US-SYS-001` ⭐ *CASE 2.0*
**BR References:** `BR-SYN-001`, `BR-SYN-002`, `BR-SYN-003` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`erp/outbox.py`, `erp/sync.py`). Pairs with ERP ADR-005. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
The site laptop can lose its connection, and check-ins depend on earlier events being applied in order.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Write directly from the engine | Simple | Blocks detection; loses data offline |
| B | Message broker | Strong delivery | A broker to run on a laptop |
| C | SQLite outbox; a worker drains each lane in order | Survives restarts and offline periods; no new infrastructure | Retries can repeat, so writes must be idempotent |

## Decision — Quyết định
*We chose option C.* Lanes are `db` (Supabase) and `api` (ERP REST). The head of a lane blocks later events until it is done or failed. Transient errors back off, doubling to 60 s. Permanent errors mark the event failed so later events continue, and an operator can re-queue it.

## Rationale — Lý do
It keeps order within a lane and needs nothing beyond SQLite.

## Consequences — Hệ quả
**Positive:**
- No ERP event is lost while offline (NFR-10).

**Negative (trade-offs accepted):**
- Done rows are not purged automatically: `Outbox.purge_done()` exists but is not scheduled.

**Risks:**
- A permanent failure needs someone to look at Settings.

**Compliance Impact:** ⭐ *CASE 2.0*
- Queued payloads are personal data held locally until sent.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-109, ADR-110.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-108:
1. Queue ERP work with `SyncGate.put`; do NOT write to the ERP from the engine, gate or safety code.
2. Each event is one transaction committed by `ErpSync.apply`.
3. Connection errors are transient; constraint, trigger and privilege errors are permanent.
4. A later event MUST NOT overtake an earlier one in its lane.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
