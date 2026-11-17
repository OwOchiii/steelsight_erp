# ADR-006 — Optimistic locking, with the version carried through forms

**Status:** `Accepted` — amended by [ADR-025](ADR-025_mistakes-shown-where-they-were-made.md) on 2026-11-16
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #consistency #concurrency
**US References:** `US-MGR-001`, `US-HSE-001`, `US-HSE-004`, `US-ADM-002` ⭐ *CASE 2.0*
**BR References:** `BR-PLT-008`, `BR-SCH-004`, `BR-ATT-007`, `BR-PPE-004` ⭐ *CASE 2.0*

> Recorded retrospectively from `model/Versioned.java`, the `@Version` fields, `V5__check_in_version.sql` and `PageExceptionHandler.conflict()`.

---

## Context — Bối cảnh
Two HSE officers can decide the same dispute at once; a manager can edit a shift another admin just changed; an HSE officer can correct a visit while the detection service auto-closes it with plain SQL. JPA's `@Version` alone only catches conflicts inside one transaction, but a form is loaded in one request and saved in another, minutes later.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Last write wins | Nothing to build | Silent lost updates on pay-relevant data |
| B | Pessimistic row locks while a form is open | No conflicts | Locks held across HTTP requests; impossible with stateless pages |
| C | `@Version` column, the version rendered in the form as a hidden field, compared on save (`Versioned.requireVersion`), plus a DB trigger that bumps `check_in.version` on any update | Catches stale forms across requests and plain-SQL writers | Users must reload on conflict |

## Decision — Quyết định
*We chose option C.* `AppUser`, `Shift`, `CheckIn`, `Violation` and `Ticket` have `@Version`. Edit forms carry `version`; services call `entity.requireVersion(formVersion)`, which throws `StaleEditException` (an `OptimisticLockingFailureException`). `PageExceptionHandler` answers **409** "Someone else changed this". *Since ADR-025:* `PageController.submit()` catches it and shows the form again with status 409, the person's input, and "Someone else changed this while you were editing. Reload the page, check their changes and try again."; `attempt()` turns it into a flash error. For `check_in`, `bump_check_in_version()` increments the version when an `UPDATE` didn't change it, so detection's plain-SQL check-outs also invalidate open forms.

## Rationale — Lý do
It gives cross-request safety without locks, and covers the database's second writer.

## Consequences — Hệ quả
**Positive:**
- `ServiceAtomicityTest.twoOfficersDecidingAtOnceCannotBothWin` and `ServiceIntegrationTest.aStaleCorrectionFailsEvenIfOnlyPlainSqlChangedTheVisit` prove it.

**Negative (trade-offs accepted):**
- ~~The user loses what they typed on a 409 and must reload.~~ Since ADR-025 the form stays with what was typed; the person still has to reload to save.
- `Violation.decide()` relies on JPA `@Version` within the transaction, not on a form version (the decision form doesn't carry one); a decision made from a stale page still succeeds if nobody else decided first, which is acceptable because the state check (`isDisputed`) guards it.

**Risks:**
- Only `check_in` has the bump trigger; if detection ever updates `violation` or `shift` with plain SQL, forms won't see it.

**Compliance Impact:** ⭐ *CASE 2.0*
- Protects accuracy and integrity of attendance and violation records.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-015: concurrent dispute decisions.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-006:
1. An entity edited through a form MUST implement Versioned with a @Version Long version field, and its form
   object MUST carry `long version` as a hidden field.
2. The service update method MUST take `long expectedVersion` and call entity.requireVersion(expectedVersion)
   before changing any field.
3. Do NOT catch StaleEditException/OptimisticLockingFailureException in controllers yourself. Forms use
   PageController.submit(...), which re-shows the form with 409; buttons use attempt(...) (ADR-025).
4. If a table is also updated by plain SQL (detection), add a BEFORE UPDATE trigger that bumps version like
   bump_check_in_version() in V5.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
