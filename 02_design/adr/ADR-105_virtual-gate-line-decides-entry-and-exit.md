# ADR-105 — A virtual line decides entering from leaving

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #attendance
**US References:** `US-WRK-001`, `US-WRK-002`, `US-ADM-003` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-002`, `BR-IDG-003`, `BR-IDG-004`, `BR-IDG-005` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided by the developer on 2026-11-06. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
One camera watches the gate. Being seen by the camera does not say whether a person is arriving or leaving, and a face scan alone could be done without entering.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Check in on any recognised face | Simplest | Scan and walk away records attendance; no direction |
| B | Two cameras, one per direction | Direction by camera | More hardware |
| C | A line with an inside point; track the foot point across it | One camera; direction from geometry; recognition alone records nothing | Depends on tracking and on line placement |

## Decision — Quyết định
*We chose option C.* Entering needs a face. Leaving needs a face or the linked vest number, because leavers usually show their back. There is no default line: a gate camera without one records nothing and shows a warning.

## Rationale — Lý do
Direction becomes a property of movement, so standing at the camera has no effect.

## Consequences — Hệ quả
**Positive:**
- Stepping back before commit cancels; jitter is absorbed by a dead band; repeats within 30 s are ignored.

**Negative (trade-offs accepted):**
- The line is effectively infinite: A and B define an angle and position, not a segment.

**Risks:**
- A lost track mid-crossing misses that crossing.
- A worker recognised entering but not leaving stays open until auto-closed; the ERP flags this for review when no vest is linked (ADR-111).

**Compliance Impact:** ⭐ *CASE 2.0*
- Gate snapshots and face similarity are personal data; check-ins go to the ERP under its retention rules.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-111.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-105:
1. Direction MUST come from `GateLine` side changes of the foot point; do NOT infer it from recognition.
2. A check-in MUST have a face; a check-out accepts a face or the linked vest worker.
3. The check-in time is the moment of crossing; waiting for identity or PPE MUST NOT change it.
4. PPE checks MUST NOT block a check-in.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
