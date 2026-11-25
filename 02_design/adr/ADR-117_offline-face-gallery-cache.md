# ADR-117 — Keep an offline copy of the face gallery: vectors only, replaced on every sync

**Status:** `Accepted`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #offline #biometric #sync
**US References:** `US-WRK-001` ⭐ *CASE 2.0*
**BR References:** `BR-SYN-012` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided on 2026-11-17, after the app started with an empty gallery on a network that blocks Postgres port 5432. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
The gallery was loaded only from Supabase into memory. The school network blocks port 5432, so the app started with no faces and recognised nobody.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Copy the enrolment photos and re-embed them at startup | Survives a model change | Biometric photos on disk; slow start |
| B | Copy the READY vectors to the local SQLite after each successful sync, and load them at startup | Fast; no photos on disk; same result as re-embedding with the same model | Stale until the next successful sync |
| C | No copy; require a connection | Nothing stored | No recognition offline |

## Decision — Quyết định
*We chose option B.* `GalleryCache` (table `face_cache`, 512 float32 per row, with `model_name`) is overwritten in one transaction after every successful `refresh_reference`; an empty ERP result empties it. `AppState` loads it at startup when it matches `FACE_MODEL_PACK`. The face status shows `gallery: erp | cache`.

## Rationale — Lý do
A vector is exactly what re-embedding the photo would give with the same model, so photos add only risk.

## Consequences — Hệ quả
**Positive:**
- Recognition works on any network once the app has synced once.

**Negative (trade-offs accepted):**
- New enrolments and deletions reach this machine only on the next successful sync.

**Risks:**
- A worker deleted in the ERP stays recognisable on an offline machine until it reconnects.

**Compliance Impact:** ⭐ *CASE 2.0*
- Biometric vectors are stored on the gate PC (Luật 91/2025/QH15); every sync replaces the copy, so ERP erasure (ADR-007) reaches it on reconnect. **DPO to confirm the local copy is acceptable.**

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-117:
1. Save the cache only from a successful ERP refresh, replacing every row in one transaction.
2. Load only rows whose model_name equals FACE_MODEL_PACK.
3. Store vectors only; never store enrolment photos locally.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
