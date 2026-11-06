# ADR-005 — Detection writes are idempotent by outbox event UUID

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #propagation #idempotency #ha
**US References:** `US-SYS-001`, `US-SYS-002` ⭐ *CASE 2.0*
**BR References:** `BR-NTF-003` ⭐ *CASE 2.0*

> Recorded retrospectively from `V2__outbox_event_ids.sql`, `V8` (`safety_alert.event_id`) and `SafetyAlertService.record()`.

---

## Context — Bối cảnh
The detection laptop can lose its internet connection. Its local outbox holds events and retries until a write is confirmed. If the connection drops after the commit but before the confirmation, the same event is sent again. A duplicate violation would cost a worker 10 safety points; a duplicate check-in would break the one-open-visit rule.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Deduplicate on business fields (worker, camera, time) | No new column | Fragile: two real violations can share them; time precision varies |
| B | Each event carries a UUID made by the producer; a UNIQUE `event_id` column; inserts use `ON CONFLICT (event_id) DO NOTHING` | Exact; retry-safe; cheap | One more column per table |
| C | Exactly-once delivery with a broker | Strong guarantees | A broker to run on a free tier; far more complexity |

## Decision — Quyết định
*We chose option B.* `violation`, `check_in`, `ppe_linking` (V2) and `safety_alert` (V8) have `event_id uuid UNIQUE`. It is NULL for rows the ERP creates by hand (a unique constraint allows many NULLs). Detection inserts with `ON CONFLICT (event_id) DO NOTHING`; the ERP's alert API does the same and answers `200 {"status":"duplicate"}` when nothing was inserted.

## Rationale — Lý do
A producer-made UUID identifies the event, not the business facts, so retries are exact and two genuine events never collide. It needs nothing beyond PostgreSQL.

## Consequences — Hệ quả
**Positive:**
- At-least-once delivery from the outbox becomes effectively exactly-once in the database.
- The alert API can tell the caller `created` (201) from `duplicate` (200), so the outbox can drop the event either way.

**Negative (trade-offs accepted):**
- The producer must keep the same UUID across retries; a bug that regenerates it would create duplicates.

**Risks:**
- `safety_alert.event_id` is NOT NULL, but the three detection tables allow NULL; a detection bug that omits it would lose idempotency silently.

**Compliance Impact:** ⭐ *CASE 2.0*
- Prevents duplicate records that would misstate a worker's safety score and pay (accuracy of personal data, Luật 91/2025/QH15).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-019: The alert API returns 201/200 by insert result.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-005:
1. Any new table that receives events from the detection service MUST have event_id uuid with a UNIQUE
   constraint, and the insert MUST use ON CONFLICT (event_id) DO NOTHING.
2. An API that accepts detection events MUST require eventId and answer 201 when inserted and 200 with
   {"status":"duplicate"} when the event id already exists; never 409 or 500 for a retry.
3. Do NOT deduplicate detection events on business fields (worker, time, camera).
4. Rows the ERP creates itself (manual check-ins, etc.) leave event_id NULL.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
