# ADR-020 — Photo storage behind one interface, with private, separate buckets

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #storage #biometrics #security
**US References:** `US-WRK-004`, `US-ADM-003` ⭐ *CASE 2.0*
**BR References:** `BR-STO-001`…`BR-STO-006`, `BR-TKT-008` ⭐ *CASE 2.0*

> Recorded retrospectively from `storage/**`, `application*.yaml`, `docs/deployment.md`.

---

## Context — Bối cảnh
Safety concerns carry a photo; face enrolment needs a photo. Render's disk is wiped on every redeploy, restart or spin-down. Face photos are biometric; ticket photos are not. Browsers send any file name and content type they like.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Store bytes in PostgreSQL | One store | Bloats the free database; pooler limits |
| B | Public bucket URLs | Fast | Anyone with a link sees a face photo |
| C | `PhotoStorage` interface; `LocalPhotoStorage` (dev/test) and `SupabasePhotoStorage` (prod, server key, private buckets `ticket-photos` and `face-photos`); type from magic bytes; random UUID names; the ERP streams photos after the owning record's permission check | Durable; private; testable locally | Every photo passes through the ERP |

## Decision — Quyết định
*We chose option C.* Selected by `steelsight.storage.type` (`local` default, `supabase` in `prod`). `PhotoAlbum` (`TICKET`, `FACE`) picks folder or bucket; one bucket for both refuses to start. Only JPEG, PNG, WebP by first bytes; `max-file-size: 5MB`. `isStoredName()` refuses anything that isn't a generated name, so a bad DB value can't reach another album. Delete is a best-effort cleanup that logs and never throws over the original error. Supabase client: 5 s connect, 30 s read timeouts.

## Rationale — Lý do
Photos survive restarts, stay private, and the storage layer stays permission-agnostic: callers check first.

## Consequences — Hệ quả
**Positive:**
- `SupabasePhotoStorageTest` (11), `LocalPhotoStorageTest` (3), `PhotoStorageSelectionTest` (3), `PagesIntegrationTest.aSafetyConcernKeepsItsZoneAndPhotoAndThePhotoStaysPrivate`.

**Negative (trade-offs accepted):**
- Photos load through the ERP's memory (`ByteArrayResource`); fine at 5 MB.
- The Supabase server key bypasses Storage policies; it must stay server-side.

**Risks:**
- A failed delete leaves an orphan file; nothing sweeps orphans.

**Compliance Impact:** ⭐ *CASE 2.0*
- Face photos are isolated in their own private bucket, readable only through an Admin-only endpoint with `no-store`. Supabase as a data processor for biometric data: **DPO review required.**

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Future: violation/check-in snapshots from detection in a third bucket.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-020:
1. Store and read photos ONLY through the PhotoStorage interface with the right PhotoAlbum; never write files
   or call Supabase directly.
2. PhotoStorage has no idea who is asking: the controller/service MUST check the owning record's permission
   BEFORE store() or load().
3. Serve photos with Cache-Control: no-store and the content type from PhotoStorage.contentType(name).
4. If saving the record fails after store(), call delete() on the stored name and rethrow.
5. Never trust the uploaded file name or content type; never build a path from user input.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
