# ADR-004 — The detection service integrates through the shared database, as a least-privilege role behind RLS

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #integration #enforcement #storage #ha
**US References:** `US-SYS-002` ⭐ *CASE 2.0*
**BR References:** `BR-PLT-001`, `BR-PPE-001`, `BR-ATT-003` ⭐ *CASE 2.0*

> Recorded retrospectively from `V1__init_schema.sql` (role, grants, policies), `afterMigrate__enable_rls.sql` and `docs/deployment.md`.

---

## Context — Bối cảnh
The Python detection service (YOLO26, EasyOCR, InsightFace) runs on a demo laptop with the only GPU, and writes check-ins, vest links, violations and enrolment results. The ERP runs on Render's free tier and **sleeps after 15 minutes idle**, so it can't be the service detection writes through. The database is Supabase, whose public Data API exposes every table in `public` unless RLS blocks it. Detection must never read password hashes, tickets or change dispute decisions.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Detection calls an ERP REST API for every write | Single writer; Java validation | ERP asleep or slow → lost or delayed events; much more API to build |
| B | Detection writes to the shared database as the table owner | Simplest | Detection can read and change everything, including password hashes |
| C | Detection writes as its own `steelsight_detection` role with column-level GRANTs, and RLS is on for every table with policies only for that role | Works while the ERP sleeps; least privilege; Supabase Data API blocked | Two writers; rules must be in the database (ADR-003) |

## Decision — Quyết định
*We chose option C.* `steelsight_detection` is created `NOLOGIN` (its password is set by hand in Supabase, never in Git) and granted only: SELECT on `zone`, `camera`, `ppe_vest`, `shift`; SELECT on six `app_user` columns (`id, employee_code, full_name, face_file_path, is_active, deleted_at`; `full_name` added in V13 so the detection app's gate screen can show who checked in); INSERT/limited UPDATE on `ppe_linking`, `check_in`; INSERT of detection columns and SELECT on `violation`; SELECT and UPDATE of result columns on `face_embedding`; EXECUTE on `close_stale_check_ins`. RLS is switched on for every table by `afterMigrate__enable_rls.sql`; the ERP connects as the owner, which bypasses RLS.

## Rationale — Lý do
Detection keeps working while the ERP sleeps, and can't touch more than its job needs. RLS with no public policies shuts Supabase's Data API off completely, with no extra configuration.

## Consequences — Hệ quả
**Positive:**
- No event is lost while the ERP sleeps; triggers (ADR-014) still react to detection's writes.
- `schema_checks.sql` proves the role can do its job and can't read `password_hash`, change a dispute, or read tickets.

**Negative (trade-offs accepted):**
- Business rules for detection's writes must live in SQL (triggers, constraints), not Java.
- The ERP learns about new rows only when it next reads; there is no push except the alert API (ADR-019).

**Risks:**
- The ERP's connection is the table owner; a SQL-injection in the ERP would bypass RLS. Mitigation: only parameterised `JdbcTemplate` calls and JPA (no string-built SQL; `SafetyScoreService.listWorkers` escapes LIKE wildcards and binds them).
- Supabase's session pooler allows few connections; the ERP is capped at 5 (`application-prod.yaml`) to leave room for detection.

**Compliance Impact:** ⭐ *CASE 2.0*
- Face embeddings (biometric, sensitive personal data under Luật 91/2025/QH15) are readable only by the owner and the detection role; the public API is blocked. Data-processor terms with Supabase and Render need DPO review.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-005: Idempotent detection writes.
- ADR-011, ADR-013, ADR-014.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-004:
1. A new table or column the detection service must read or write MUST get explicit column-level GRANTs to
   steelsight_detection AND a matching CREATE POLICY ... TO steelsight_detection in the same migration.
2. NEVER grant steelsight_detection access to password_hash, ticket, ticket_comment, notification, safety_alert,
   or UPDATE on violation.dispute_status/reviewed_by/reviewed_at.
3. NEVER put a database password, CREATE ROLE ... PASSWORD, or ALTER ROLE ... PASSWORD in a migration.
4. NEVER add a policy TO public, anon or authenticated (Supabase roles); RLS must keep the Data API closed.
5. Any SQL the ERP runs MUST be parameterised (JdbcTemplate ? placeholders or JPA); never concatenate input into SQL.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
