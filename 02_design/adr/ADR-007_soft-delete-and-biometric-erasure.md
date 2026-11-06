# ADR-007 — Soft delete for people and tickets; hard erasure for biometric data

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #retention #erasure #consent
**US References:** `US-ADM-002`, `US-ADM-003` ⭐ *CASE 2.0*
**BR References:** `BR-ATT-010`, `BR-IAM-001`, `BR-TKT-003`, `BR-TKT-007` ⭐ *CASE 2.0*

> Recorded retrospectively from `model/SoftDeletableModel.java`, `repository/SoftDeletableRepository.java`, the `trg_user_erase_embedding` trigger (V1) and `UserService.softDelete()`.

---

## Context — Bối cảnh
A worker who leaves still has shifts, check-ins and violations that past pay was based on; deleting their row would break those references and the monthly reports. But their face photo and embedding are biometric data, which must not be kept once they're no longer needed. Tickets also need to disappear from queues without losing a dispute's history.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Hard delete users and tickets | Data is really gone | Breaks foreign keys; loses pay history |
| B | Soft delete everything, including the embedding | Simple, uniform | Keeps biometric data after it's needed |
| C | Soft delete (`deleted_at`) for `app_user` and `ticket`; on a user's soft delete a trigger deletes the `face_embedding` row in the same transaction, and the ERP clears `face_file_path` and removes the photo file after commit | History kept; biometrics erased | Two mechanisms to understand |

## Decision — Quyết định
*We chose option C.* `UserService.softDelete()` sets `deleted_at`, `is_active = false`, clears `face_file_path`, and registers deletion of the photo file for after commit. `trg_user_erase_embedding` deletes the embedding row when `deleted_at` goes from NULL to a value. `SoftDeletableRepository.require()` treats soft-deleted rows as not found; sign-in uses `findByEmployeeCodeAndDeletedAtIsNull`.

## Rationale — Lý do
Pay history stays consistent; biometric data is erased in the same transaction, so there is no window where a deleted worker could still be matched at the gate.

## Consequences — Hệ quả
**Positive:**
- A deleted worker can't sign in or be matched; the photo file is removed only after the commit, so a rolled-back delete never loses a photo still in use (`FacePhotoCleanupTest`).

**Negative (trade-offs accepted):**
- Every query on users and tickets must filter `deleted_at IS NULL`; the JPQL queries do so explicitly.
- Employee codes and emails of deleted users stay unique, so they can't be reused.

**Risks:**
- There is no consent record or "withdraw consent" action, though the schema comment mentions it (gap G11).
- Snapshot images written by detection (`check_in.snapshot_path`, `violation.snapshot_path`) are not removed on soft delete.

**Compliance Impact:** ⭐ *CASE 2.0*
- Biometric data (sensitive personal data under Luật 91/2025/QH15 and NĐ 356/2025) is erased when the account is deleted. A documented legal basis or consent for face enrolment, and a retention period for other personal data, are still needed: **DPO decision required.**

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-011: Face enrolment queue.
- A future ADR on consent capture and retention periods.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-007:
1. Do NOT hard-delete app_user or ticket rows; set deleted_at through the service's softDelete method.
2. Every query on app_user or ticket MUST exclude deleted_at IS NOT NULL (use SoftDeletableRepository.require()
   or a ...DeletedAtIsNull method).
3. Removing a stored photo MUST be registered with TransactionSynchronization.afterCommit, never before the
   transaction commits.
4. Biometric data (face_embedding, face photos) MUST be erased, not soft-deleted, when its owner is deleted.
5. Do NOT add new copies of face images or embeddings anywhere outside face_embedding and the FACE album.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
