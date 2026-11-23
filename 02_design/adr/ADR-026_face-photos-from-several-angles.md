# ADR-026 — A worker can have face photos from several angles, one queue row per angle

**Status:** `Accepted`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #biometric #enrolment #detection
**US References:** `US-ADM-003` ⭐ *CASE 2.0*
**BR References:** `BR-ATT-009`, `BR-ATT-011`, `BR-IAM-005` ⭐ *CASE 2.0*

> Decided on 2026-11-17, after the detection app kept failing to recognise workers on the gate camera. The camera is mounted about 2.5 m high and looks down at about 30°, so faces are seen turned and from above. Extends [ADR-011](ADR-011_face-enrolment-as-a-database-queue.md) (enrolment is a database queue). Migration `V14__face_photos_from_several_angles.sql`.

---

## Context — Bối cảnh
ArcFace scores a turned face much lower against a photo taken straight on. On the gate camera, correct matches scored only 0.45 to 0.64, at the 0.45 cutoff, so many frames of the right person were rejected. The detection app already compares a face against every vector in its gallery and keeps the best score. If a worker had photos from more than one angle, a turned face could match the photo turned the same way. ADR-011's `face_embedding` table allowed one row per worker (`user_id UNIQUE`).

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Extra vector columns on the one row (`embedding_left`, `embedding_right`, …) | One row per worker stays | Every angle needs its own status, model, error and photo path, so the columns multiply; detection's single-row queue update no longer fits |
| B | Average the angle vectors into the one existing vector | No schema change | Averaging blurs the angles; a turned face matches less well than against its own angle |
| C | One row per worker and angle: a `pose` column (`FRONT`, `LEFT`, `RIGHT`) and a unique key on `(user_id, pose)` | Each angle is its own queue item with its own status and error, as ADR-011 already works; detection keeps the best match | The ERP picks a worker's status from one row (FRONT); a soft delete must also remove LEFT/RIGHT photo files |

## Decision — Quyết định
*We chose option C.* V14 adds `pose text NOT NULL DEFAULT 'FRONT' CHECK (pose IN ('FRONT','LEFT','RIGHT'))`, drops `face_embedding_user_id_key` and adds `UNIQUE (user_id, pose)`. Existing rows become FRONT. `app_user.face_file_path` stays the FRONT photo; LEFT and RIGHT photos are kept only in `source_file_path`.

- `queueForEnrolment(userId, pose, path)` upserts on `(user_id, pose)`; the two-argument form queues FRONT.
- `UserService.changeFaceAngle` and `removeFaceAngle` handle LEFT and RIGHT, for `USER_MANAGE` only, and need a FRONT photo first.
- Both take a `SELECT … FOR UPDATE` lock on the worker's `app_user` row (`AppUserRepository.requireForUpdate`), and so does `softDelete`. Without it, an upload could add a row after the delete trigger had run, or two uploads could leave one photo file orphaned. `changeFacePhoto` was already serialised by `AppUser`'s `@Version`.
- The team list and the account's main status show the FRONT row. The edit page shows each extra angle with its own status.

## Rationale — Lý do
It keeps ADR-011's queue model unchanged, with one row per photo, and the detection app needs no new write grant: its existing `UPDATE (status, embedding, model_name, error_message)` covers every row, and it can't change a row's pose.

## Consequences — Hệ quả
**Positive:**
- A worker can be recognised from the side as well as straight on, and a photo taken by the gate camera itself can be added as an extra angle.
- An angle that fails enrolment shows its own reason without affecting the others.

**Negative (trade-offs accepted):**
- The ERP shows "enrolled" from the FRONT row only, even when an extra angle failed.
- Up to three biometric photos per worker instead of one.

**Risks:**
- An admin could upload another person's photo into an angle slot. Detection refuses a photo that matches a different enrolled worker, but not one of an unenrolled person.

**Compliance Impact:** ⭐ *CASE 2.0*
- More biometric data per worker (Luật 91/2025/QH15). Every angle is erased with the account by the existing trigger (ADR-007), and the photo files after commit. **The consent and legal basis for biometric processing (gap G11) should name the extra photos — DPO.**

**Decisions unlocked (ADR tiếp theo có thể viết):**
- More poses (for example a looking-down photo for high cameras) need only a new value in the `CHECK`.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-026:
1. Queue an extra angle only through UserService.changeFaceAngle / removeFaceAngle; FRONT only through
   create / changeFacePhoto. Never insert or delete face_embedding rows any other way.
2. LEFT and RIGHT need a FRONT photo first; FacePose.FRONT is refused by the angle methods.
3. changeFaceAngle, removeFaceAngle and softDelete MUST lock the user with users.requireForUpdate(userId)
   before reading face_embedding.
4. Remove a replaced or deleted angle photo only after commit (removeFacePhotoAfterCommit); softDelete must
   collect LEFT/RIGHT paths BEFORE setting deleted_at, because the trigger deletes the rows.
5. Status shown in lists comes from the FRONT row (findAllByUserIdInAndPose(ids, FRONT)).
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
