# ADR-015 — A dispute and its ticket change together, in one transaction

**Status:** `Accepted`
**Date:** 2026-11-04
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #consistency #workflow
**US References:** `US-WRK-003`, `US-HSE-001` ⭐ *CASE 2.0*
**BR References:** `BR-PPE-003`, `BR-PPE-004`, `BR-TKT-005`, `BR-TKT-007` ⭐ *CASE 2.0*

> Recorded retrospectively from `TicketService.openDispute()`, `ViolationService.decide()`, `ServiceAtomicityTest`.

---

## Context — Bối cảnh
A worker disputes a violation; HSE discusses it on a ticket and then decides UPHELD or OVERTURNED. The violation's `dispute_status` drives the safety score; the ticket drives the HSE queue. If one changes without the other, a dispute could be decided while its ticket stays open, or a ticket resolved while the violation still says DISPUTED.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Two independent actions (resolve ticket; decide violation) | Decoupled modules | Inconsistent states possible |
| B | Domain event from one to the other, async | Decoupled | No event bus exists; eventual consistency |
| C | One `@Transactional` service method changes both: `openDispute` marks DISPUTED and creates the ticket; `decide` records the decision and resolves the ticket (with the note as a comment); `TicketService.resolve` refuses dispute tickets | Always consistent; simple | `PPE ↔ TKT` package cycle |

## Decision — Quyết định
*We chose option C.* A dispute ticket can only be closed through `ViolationService.decide()`; a pending dispute's ticket can't be soft-deleted. `uq_ticket_violation` (partial unique, live tickets only) allows one live dispute ticket per violation. An officer can't decide a dispute about their own violation.

## Rationale — Lý do
Consistency of pay-relevant state matters more than module purity in a single-process monolith.

## Consequences — Hệ quả
**Positive:**
- `ServiceAtomicityTest.failedTicketInsertLeavesTheViolationUndisputed` and `failedDecisionLeavesBothTheViolationAndTheTicketUntouched` prove rollback; `twoOfficersDecidingAtOnceCannotBothWin` proves the version guard.

**Negative (trade-offs accepted):**
- `ViolationService` depends on `TicketRepository` and `TicketCommentRepository`, and `TicketService` on `ViolationRepository`.

**Risks:**
- If the modules are ever split into services, this needs a saga or outbox.

**Compliance Impact:** ⭐ *CASE 2.0*
- The worker's objection and its outcome are recorded together, with reviewer and time (right to object; accountability).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-016: ticket routing.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-015:
1. Opening a dispute MUST go through TicketService.openDispute(actorId, violationId, subject, reason); never set
   Violation.disputeStatus = DISPUTED elsewhere, and never create a 'PPE dispute' ticket through create().
2. Deciding MUST go through ViolationService.decide(actorId, violationId, UPHELD|OVERTURNED, note), which
   resolves the ticket in the same transaction; TicketService.resolve() MUST keep refusing dispute tickets.
3. A reviewer MUST NOT decide a violation that belongsTo(actorId).
4. Keep both changes inside one @Transactional method; do NOT introduce async events between them.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
