# ADR-003 — Flyway owns the schema; the database enforces integrity

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #storage #integrity #migrations
**US References:** `US-SYS-002` ⭐ *CASE 2.0*
**BR References:** `BR-PLT-001`, `BR-SCH-001`, `BR-ATT-001`, `BR-ATT-002`, `BR-ATT-003`, `BR-PPE-001`, `BR-TKT-005`, `BR-TKT-007` ⭐ *CASE 2.0*

> Recorded retrospectively from `application.yaml` (`ddl-auto: validate`), `db/migration/V1`–`V11` and `src/test/resources/db/schema_checks.sql`.

---

## Context — Bối cảnh
Two programs write the same tables: the ERP through JPA, and the Python detection service through plain SQL. Rules such as "one open visit per worker", "no overlapping shifts", "a violation's worker matches its vest link" must hold whoever writes. The schema had to be reviewable as SQL (the database design document is written from it) and deployable to Supabase.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Hibernate `ddl-auto: update` and Java validation only | Fast to start | Python bypasses Java validation; no migration history; dangerous on production |
| B | Flyway versioned SQL migrations; Hibernate `validate`; constraints, partial unique indexes, exclusion constraints and triggers in the database; Java checks for friendly messages | Rules hold for every writer; history in Git; schema reviewable | Rules partly duplicated (Java message + DB constraint); triggers are less visible |
| C | Flyway, but rules only in Java | One language | Same hole as A for the detection service |

## Decision — Quyết định
*We chose option B.* `db/migration/V<n>__*.sql` is the only way the schema changes; `spring.jpa.hibernate.ddl-auto: validate` fails startup if entities and tables disagree. The database holds the hard rules (CHECKs, `uq_check_in_open`, `EXCLUDE USING gist` on shifts, `check_in_same_worker()`, `violation_set_worker()`, `uq_ticket_violation`). Services check the same rules first where they can give a clear message, and translate a `DataIntegrityViolationException` into a `BusinessRuleException` where they can't.

## Rationale — Lý do
The detection service writes directly, so the database is the only place every writer passes through. Migrations give a reviewable history and run the same on Testcontainers, the local Docker DB and Supabase.

## Consequences — Hệ quả
**Positive:**
- Integrity holds even for plain-SQL writers; `schema_checks.sql` (run by `SchemaChecksSqlTest`) proves the rules in SQL alone.
- `afterMigrate__enable_rls.sql` runs after every migrate, so new tables get RLS automatically (ADR-004).

**Negative (trade-offs accepted):**
- Some rules exist twice (Java message + DB constraint). The DB is the authority; Java only gives a better message.
- A unique-violation translated in Java gives one generic message for several causes (e.g. `UserService.saveUnique` can't say whether code or email clashed).

**Risks:**
- Flyway has no undo in the community edition; rollback means a new forward migration (see TDS §12).
- An applied migration must never be edited; Flyway's checksum would refuse to start.

**Compliance Impact:** ⭐ *CASE 2.0*
- Integrity and accuracy of personal data (attendance and violation records affect pay) are enforced at the storage layer, under Luật 91/2025/QH15.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-004, ADR-005, ADR-013, ADR-014, ADR-018 all rely on database-side logic.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-003:
1. Schema changes MUST be a NEW file src/main/resources/db/migration/V<next>__<snake_description>.sql.
   NEVER edit an existing V<n> file, and never use ddl-auto other than validate.
2. A rule that must hold for the detection service too MUST be a database constraint or trigger, not only Java.
3. Services MUST check the rule first when they can give a clear message (throw BusinessRuleException with an
   English sentence), and MUST catch DataIntegrityViolationException from saveAndFlush() and rethrow it as
   BusinessRuleException; never let it reach the user as a 500.
4. Map new columns on the JPA entity with the exact column name; generated columns use @Generated and
   insertable=false, updatable=false (see Shift.durationHours).
5. Every new table is covered by afterMigrate__enable_rls.sql automatically; if the detection role needs it,
   add GRANTs and a matching CREATE POLICY ... TO steelsight_detection in the same migration.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
