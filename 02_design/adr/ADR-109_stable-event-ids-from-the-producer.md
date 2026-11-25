# ADR-109 — Detection creates stable event IDs for every ERP write

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #idempotency
**US References:** `US-SYS-001` ⭐ *CASE 2.0*
**BR References:** `BR-SYN-004`, `BR-SYN-005` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`erp/mapping.py`, `gate/monitor.py`). This is the producer side of ERP ADR-005. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
The outbox can resend after a lost acknowledgement. One local `missing_both` violation becomes two ERP rows, and both must stay stable across retries.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Generate an ID at send time | Simple | New ID on each retry creates duplicates |
| B | Generate at the event: UUIDs for gate events, and `row_event_id(local_id, erp_type)` for violation rows | Exact retries | IDs must be stored with the payload |

## Decision — Quyết định
*We chose option B.* `gate_in` also carries a separate `link_event_id` for its `ppe_linking` row.

## Rationale — Lý do
ERP uniqueness only works if the producer never changes the ID.

## Consequences — Hệ quả
**Positive:**
- Handlers can check for an existing `event_id` and skip.

**Negative (trade-offs accepted):**
- Mapping must remain deterministic.

**Risks:**
- A change to `row_event_id` would make queued violations duplicate after an upgrade.

**Compliance Impact:** ⭐ *CASE 2.0*
- Prevents duplicate records that misstate a worker's safety score.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-109:
1. Never regenerate an `event_id` on retry.
2. Do NOT change the `row_event_id` derivation without draining the outbox first.
3. Do NOT deduplicate on worker, time or camera.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
