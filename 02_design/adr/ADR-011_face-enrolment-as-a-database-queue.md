# ADR-011 — Face enrolment is a database queue; the ERP never handles vectors

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #biometrics #integration #storage
**US References:** `US-ADM-003`, `US-SYS-002` ⭐ *CASE 2.0*
**BR References:** `BR-ATT-009`, `BR-ATT-010` ⭐ *CASE 2.0*

> Recorded retrospectively from `V1__init_schema.sql` (`face_embedding`), `FaceEmbedding.java`, `FaceEmbeddingRepository.queueForEnrolment()`.

---

## Context — Bối cảnh
Gate check-in matches faces with InsightFace on the GPU laptop. Someone must turn an admin's uploaded photo into a 512-dimension vector. The ERP has no GPU and sleeps when idle; the detection service is not always running.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | ERP calls detection synchronously to enrol | Instant feedback | Fails whenever the laptop is off |
| B | ERP computes the embedding | No dependency | Needs the model and a GPU on Render; impossible on the free tier |
| C | `face_embedding` row per worker as a queue: ERP upserts `PENDING` with the photo path; detection picks up `PENDING`, writes `embedding` + `model_name` and sets `READY` or `FAILED` with `error_message` | Works asynchronously; ERP never touches vectors | Enrolment status lags until detection runs |

## Decision — Quyết định
*We chose option C.* `queueForEnrolment(userId, path)` is one atomic `INSERT … ON CONFLICT (user_id) DO UPDATE` that resets status to `PENDING` and clears `embedding`, `model_name`, `error_message`. The JPA entity deliberately doesn't map the `embedding` column. `CHECK (status <> 'READY' OR (embedding IS NOT NULL AND model_name IS NOT NULL))` keeps READY honest.

## Rationale — Lý do
Decoupled through the shared database (ADR-004), safe when either side is offline, and the ERP process never holds biometric vectors.

## Consequences — Hệ quả
**Positive:**
- A replaced photo can never match the old face, because the old vector is cleared in the same statement.
- Admin sees PENDING / READY / FAILED per person on the team page.

**Negative (trade-offs accepted):**
- No immediate feedback that a photo has no detectable face.

**Risks:**
- Detection must store vectors from one model only, and re-enrol when the model changes (`model_name` exists for this).

**Compliance Impact:** ⭐ *CASE 2.0*
- Biometric processing is confined to the detection service and one table; erased on delete (ADR-007). **Needs a recorded legal basis/consent for biometric processing — DPO.**

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-020: a separate private bucket for face photos.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-011:
1. To enrol or re-enrol a face, call FaceEmbeddingRepository.queueForEnrolment(userId, storedPhotoName) in the
   SAME transaction that sets app_user.face_file_path. Never insert/update face_embedding any other way.
2. Do NOT map, read or log face_embedding.embedding in the ERP.
3. Only USER_MANAGE holders may change or view face photos and enrolment status (UserService.faceEnrolment,
   faceStatuses, facePhotoPath).
4. face_file_path holds only the stored file name in the FACE album, never a path or URL.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
