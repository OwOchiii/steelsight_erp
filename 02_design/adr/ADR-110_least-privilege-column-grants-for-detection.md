# ADR-110 — Detection connects as a limited role and writes only granted columns

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #security #storage
**US References:** `US-SYS-001`, `US-SYS-002` ⭐ *CASE 2.0*
**BR References:** `BR-SYN-006`, `BR-SYN-007` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code Consumer side of ERP ADR-004; the ERP's Flyway migrations own the schema. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Detection shares the ERP database but must not see names or alter the schema. It runs on a site laptop.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Connect as the ERP's main role | Nothing to manage | A stolen laptop exposes everything |
| B | `steelsight_detection`, with column-level grants, through psycopg and the session pooler | Least privilege | Every new query must fit the grants |

## Decision — Quyết định
*We chose option B.* The role reads only `id`, `employee_code`, `face_file_path`, `is_active` and `deleted_at` from `app_user`. ERP secrets come only from `.env` or environment variables.

## Rationale — Lý do
The site machine is the least trusted part of the system.

## Consequences — Hệ quả
**Positive:**
- The app cannot read worker names or change tables.

**Negative (trade-offs accepted):**
- The worker list shows employee codes only (FR-5.11 partial).

**Risks:**
- SMTP, Telegram and camera passwords are still plain text in `settings.json` and `cameras.json` (NFR-8 partial).

**Compliance Impact:** ⭐ *CASE 2.0*
- Data minimisation and security of processing (Luật 91/2025/QH15).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-110:
1. Check every new SQL statement against the column GRANTs in the ERP's `V1__init_schema.sql` and `V2__outbox_event_ids.sql`.
2. Do NOT create or alter ERP tables from detection.
3. ERP secrets MUST NOT be accepted by `POST /api/settings`.
4. Local tables live in SQLite only.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
