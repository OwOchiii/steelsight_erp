# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-TKT-TDD-001`
**Version:** `1.0`
**Date:** `2026-11-06`
**Status:** `Draft`
**Standard:** ISO/IEC/IEEE 29119-3:2021 — Software Testing Part 3: Test Documentation
**Author:** `Claude Code (Opus 5.5) — documentation, from the existing test suite at commit f005861`
**Reviewed by:** `[ ] Tech Lead — Pending`
**DPO Sign-off:** `[ ] Pending`
**Approved by:** `[ ] Pending`
**Classification:** `Internal — Confidential`

**References:**
- `02_design/MODULE-MAP.md` — BR / US catalog
- `03_implement/STEELSIGHT-TKT-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-001`, `ADR-007`, `ADR-015`, `ADR-016`, `ADR-020`
- `Luật 91/2025/QH15` — personal data in free text and photos

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `sh ./mvnw verify` chưa xanh. Synthetic tickets and generated image bytes only.
>
> **Retrospective note & status legend:** see `STEELSIGHT-IAM-TDD-001`. 🟢V = re-run here on `f005861` · 🟢L = last local run 2026-11-06 15:31 (code of `94a2c3c`), not re-run (no Docker) · 🔴 = not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Ticketing |

---

## MỤC LỤC

1. [Thông tin Module](#1-thông-tin-module)
   - 1.1 [AI Generation Context (CASE 2.0)](#11-ai-generation-context-case-20) ⭐ *Mới*
2. [Logic Issues Resolved](#2-logic-issues-resolved)
3. [Test Design Specification (TDS)](#3-test-design-specification-tds)
4. [Test Case Specification](#4-test-case-specification)
   - Props Isolation Boilerplate ⭐ *Mới*
5. [Red-Green-Refactor Tracker](#5-red-green-refactor-tracker)
   - 5.1 [Red Gate Protocol (CASE 2.0)](#51-red-gate-protocol-case-20--gate-2) ⭐ *Mới*
6. [Entry / Exit Criteria](#6-entry--exit-criteria)
7. [Rollback Plan](#7-rollback-plan)
8. [CASE 2.0 Anti-Pattern Detection](#8-case-20-anti-pattern-detection-ai-assisted-tcs) ⭐ *Mới*

---

## 1. Thông tin Module

| Field | Value |
|-------|-------|
| **Feature / Gap ID** | `TKT` (existing) · gap `G2` (soft delete has no endpoint) |
| **Module** | `TKT — Ticketing (Requests and reports)` |
| **Spec gốc** | `STEELSIGHT-TKT-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined — work done 2026-11-04 → 2026-11-06` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` |
| **Compliance Scope** | `Luật 91/2025/QH15` — DPO pending |
| **Upstream Dependencies** | `IAM`, `PPE`, `SCH` (zones), `STO` |
| **Downstream Consumers** | `PPE` (`decide` resolves dispute tickets), sidebar counts |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` (`chatlog/ai-log.md` 2026-11-04 14:52, 16:49; 2026-11-06) |
| **Constraint Source** | `STEELSIGHT-TKT-IMP-001 §17`, `ADR-015/016` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | V1: managers handle shift and equipment tickets; V9: their own team's | V11: HSE handles PPE dispute + Safety concern; Admin everything else; managers handle none | TKT-TC-001…003 assert the V11 routing and that leaders get 403 on the queue |
| L2 | A dispute ticket is resolved like any other | Only via `ViolationService.decide` | TKT-TC-010 asserts `resolve` and `create(PPE dispute)` are refused |
| L3 | A soft-deleted ticket returns 404 | `can_read_ticket` excludes deleted rows → **403** to the creator | TKT-TC-013 asserts `AccessDeniedException` (behaviour as built) |
| L4 | Tickets can be deleted from the UI | `softDelete` exists, no endpoint (gap G2) | TKT-TC-030 🔴 |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
TKT bao gồm các layer:
├── Entity behaviour         Ticket.assignTo / resolveBy (exercised via services)
├── Services                 TicketService                                (Testcontainers PostgreSQL)
├── Controller / pages       TicketController (multipart, photo endpoint) (MockMvc)
└── Database rules           can_read/update/delete_ticket, RESOLVED CHECKs, uq_ticket_violation
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-WRK-004, US-HSE-003, US-ADM-004 | Open, handle, see-all |
| `ADR-016`, `ADR-015`, `ADR-020` | Routing, dispute coupling, private photos |
| `BR-TKT-001` … `BR-TKT-008` | Rules |
| Memory note: *Safety concern* = broken safety gear or a dangerous condition, HSE, optional zone and photo | TKT-TC-005 |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | Routing by category handler role | queues, `can_update_ticket` | TKT-TC-001…004 |
| TC-COND-002 | Create: validation, zone, photo, no dispute category | `create`, `POST /tickets` | TKT-TC-005…009 |
| TC-COND-003 | Dispute ticket ends only by decision | `resolve`, `softDelete` | TKT-TC-010 |
| TC-COND-004 | Assign: assignee must handle; OPEN → IN_PROGRESS | `assign` | TKT-TC-011, 012 |
| TC-COND-005 | Privacy: creator + handler + admin only; soft-deleted hidden | `can_read_ticket` | TKT-TC-013…015 |
| TC-COND-006 | Comments: creator or handler; none after resolved | `addComment` | TKT-TC-016, 017 |
| TC-COND-007 | Resolve with note → comment; RESOLVED final | `resolve` | TKT-TC-018 |
| TC-COND-008 | DB invariants | CHECKs, unique index | TKT-TC-019…022 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Equivalence Partitioning | Categories (HSE vs Admin), actors (creator / other worker / HSE / Admin / leader) | Routing classes |
| State Transition Testing | OPEN → IN_PROGRESS → RESOLVED; actions after RESOLVED | Final state |
| Boundary Value Analysis | Subject 200/201, description 4000/4001, photo 5 MB | Limits |
| Error Guessing | Tampered `categoryId`, non-image file, script in subject, forged assignee | Tampering and XSS |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | DB seed | Categories from V1/V11 (looked up by name with `category("Safety concern")`) | Routing |
| `FX-002` | DB seed | Workers `W001`, `W002`; HSE `H001`, `H002` (one inactive in some tests); Admin `A001`; leader `M001` | Actors |
| `FX-003` | DB seed | Violation for W001 (for dispute cases) | Dispute coupling |
| `FX-004` | File | Generated JPEG/PNG bytes; a text file named `.jpg` | Photo upload |
| `FX-005` | DB seed | Zone `Zone A` | Ticket location |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```java
@SpringBootTest @AutoConfigureMockMvc @Import(TestDatabase.class) @Transactional
class TicketNewCasesTest extends PageTestSupport {
    Long worker, otherWorker, hse, admin;

    @BeforeEach
    void seed() {                                               // FX-002, rolled back after each test
        worker = userWithRole("W001", "Worker One", "WORKER");
        otherWorker = userWithRole("W002", "Worker Two", "WORKER");
        hse = userWithRole("H001", "Hse One", "HSE_OFFICER");
        admin = userWithRole("A001", "Admin One", "ADMIN");
    }
    Long safetyTicket() { return ticket(category("Safety concern"), worker, "Spill"); }   // fresh per call
}
```

---

### TKT-TC-001 — Safety tickets go to HSE and everything else to Admin

**Severity:** `HIGH`
**Feature Under Test:** `GET /tickets/queue`, `TicketService.listQueue`
**Test File:** `TeamsTicketsAndReportTest.java` — `safetyTicketsGoToHseAndEverythingElseToAdmin`; `WorkerAccessPagesTest.java` — `nonSafetyTicketsGoToAdminWhicheverTeamTheyComeFrom`, `hseTicketsDoNotDependOnTheTeam`; `HsePagesIntegrationTest.java` — `queueShowsOnlyTheCategoriesYourRoleHandles`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-TKT-001`, `ADR-016 §Decision`

**Test Steps:** worker opens a Safety concern and a Leave/Equipment request; read both queues
**Expected Result (PASS):** HSE queue: Safety concern only; Admin queue: the other; team doesn't matter
**Expected Result (FAIL):** HSE sees leave requests (personal data outside purpose)
**Current Status:** 🟢L

---

### TKT-TC-003 — Team leaders no longer handle tickets

**Severity:** `HIGH`
**OWASP:** `A01:2021 — Broken Access Control`
**Feature Under Test:** queue and resolve for MANAGER
**Test File:** `TeamsTicketsAndReportTest.java` — `teamLeadersNoLongerHandleTickets`
**TDD Phase:** 🟢 GREEN
**Oracle Source:** `ADR-016` (V11 removed `TICKET_*_TEAM`)

**Expected Result (PASS):** dashboard has no queue link but has "new ticket"; `GET /tickets/queue` → 403; `POST /tickets/{equipment}/resolve` → 403
**Current Status:** 🟢L

---

### TKT-TC-005 — A safety concern keeps its zone and photo, and the photo stays private

**Severity:** `HIGH`
**CWE:** `CWE-200 — Exposure of Sensitive Information`
**Feature Under Test:** `POST /tickets` (multipart), `GET /tickets/{id}/photo`
**Test File:** `PagesIntegrationTest.java` — `aSafetyConcernKeepsItsZoneAndPhotoAndThePhotoStaysPrivate`; `ServiceIntegrationTest.java` — `safetyConcernCanSayWhereTheHazardIsAndShowIt`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-002`
**Oracle Source:** `BR-TKT-008`, `ADR-020`

**Expected Result (PASS):** a PNG uploaded as `../../evil.exe` with `text/html` is stored as `<uuid>.png` (no `evil` in the name); the page shows the zone and photo link; the creator gets 200 `image/png` with the same bytes; another worker gets 403
**Expected Result (FAIL):** photo reachable by anyone with the id
**Current Status:** 🟢L

---

### TKT-TC-010 — A dispute ticket can only end through a decision

**Severity:** `HIGH`
**Feature Under Test:** `TicketService.create/resolve/softDelete` on a dispute
**Test File:** `ServiceIntegrationTest.java` — `disputeTicketCanOnlyEndThroughADecision`; `HsePagesIntegrationTest.java` — `aDisputeTicketPointsToTheDecisionInsteadOfOfferingResolve`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-003`
**Oracle Source:** `ADR-015`, `BR-TKT-007`

**Expected Result (PASS):** `create(PPE dispute)` → rule error; `resolve` → "Resolve a PPE dispute by deciding its violation"; `softDelete` while DISPUTED → "Decide the dispute before deleting its ticket"; page links to the violation instead of a Resolve button
**Expected Result (FAIL):** ticket closed while the violation stays DISPUTED
**Current Status:** 🟢L

---

### TKT-TC-016 — Comments come from the creator or handlers, and stop when resolved

**Severity:** `MEDIUM`
**Feature Under Test:** `TicketService.addComment`
**Test File:** `ServiceIntegrationTest.java` — `commentsComeFromTheCreatorOrHandlersAndStopWhenResolved`; `PagesIntegrationTest.java` — `youCanCommentOnYourOwnTicketButNotSomeoneElses`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-006`
**Oracle Source:** `BR-TKT-006`

**Expected Result (PASS):** creator and HSE comment (2 comments); another worker → `AccessDeniedException`; after resolve, the creator → `BusinessRuleException`
**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### TKT-TC-014 — Tickets stay private to their creator and handlers

**Severity:** `CRITICAL`
**OWASP:** `A01:2021`
**CWE:** `CWE-639`
**Legal:** `Luật 91/2025/QH15 — purpose limitation` (leave reasons, photos)
**Feature Under Test:** `can_read_ticket()`, `GET /tickets/{id}`
**Test File:** `ServiceIntegrationTest.java` — `ticketsStayPrivateToTheirCreatorAndHandlers`; `HsePagesIntegrationTest.java` — `anotherRolesTicketCannotBeHandled`
**TDD Phase:** 🟢 GREEN

**Test Steps (Attack Simulation):** W002 and a leader request W001's ticket by id; HSE requests an Admin-category ticket
**Expected Result (PASS = hệ thống an toàn):** 403 for all three; Admin can read every ticket
**Expected Result (FAIL = lỗ hổng tồn tại):** cross-user reading by id
**Current Status:** 🟢L

---

### TKT-TC-007 — A tampered category is refused without a server error

**Severity:** `MEDIUM`
**CWE:** `CWE-20`
**Feature Under Test:** `POST /tickets` with an unknown `categoryId`
**Test File:** `PagesIntegrationTest.java` — `aTamperedCategoryIsRefusedWithoutAServerError`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** 4xx page, never 500; nothing stored
**Current Status:** 🟢L

---

### TKT-TC-008 — Ticket text is escaped

**Severity:** `MEDIUM`
**OWASP:** `A03:2021 — Injection`
**CWE:** `CWE-79`
**Feature Under Test:** `tickets/detail.html`, `tickets/list.html`
**Test File:** `PagesIntegrationTest.java` — `pageTextIsEscaped`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** `<script>` in subject/description renders as text
**Current Status:** 🟢L

---

### TKT-TC-031 — A forged assignee outside the handler role is refused

**Severity:** `HIGH`
**CWE:** `CWE-639`
**Feature Under Test:** `POST /tickets/{id}/assignee`
**Test File:** `HsePagesIntegrationTest.java` — `aTicketCannotBeAssignedToSomeoneOutsideTheHandlingRole`; `ServiceIntegrationTest.java` — `assigneeMustBeAbleToHandleTheTicket`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** flash "That user cannot handle this ticket"; assignee unchanged
**Current Status:** 🟢L

---

### INTEGRATION TEST CASES

---

### TKT-TC-INT-001 — Assigning and resolving a ticket moves it through the queue

**Severity:** `HIGH`
**Feature Under Test:** Full flow: `GET /tickets/queue` → `POST …/assignee` → `POST …/resolve` (note) → `GET /tickets/queue?resolved=true`
**Test File:** `HsePagesIntegrationTest.java` — `assigningAndResolvingATicketMovesItThroughTheQueue`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`, `TC-COND-007`

**Preconditions:** Testcontainers PostgreSQL; FX-001, FX-002

**Test Steps:**
1. Safety concern appears in HSE's open queue (oldest first)
2. HSE assigns themself → `IN_PROGRESS`
3. HSE resolves with a note
4. Ticket appears in the resolved queue; the note is a comment

**DB Assertion:**
```java
Map<String, Object> t = jdbc.queryForMap("SELECT status, resolver_id, resolved_at FROM ticket WHERE id = ?", ticket);
assertThat(t.get("status")).isEqualTo("RESOLVED");
assertThat(t.get("resolver_id")).isEqualTo(hse);
assertThat(t.get("resolved_at")).isNotNull();
```

**Current Status:** 🟢L

---

### TC catalogue — remaining existing tests

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| TKT-TC-002 | `ServiceIntegrationTest` — `safetyConcernGoesToHseAndOnlyHandlersCanResolveIt` | COND-001 | HIGH | 🟢L |
| TKT-TC-004 | `ServiceIntegrationTest` — `adminCanWorkAnyTicket`; `RepositoryIntegrationTest` — `ticketsAreEditedByTheHandlingRoleAndAdmin` | COND-001 | HIGH | 🟢L |
| TKT-TC-006 | `PagesIntegrationTest` — `newTicketFormOffersZonesAndLeavesOutDisputes` | COND-002 | MEDIUM | 🟢L |
| TKT-TC-009 | `PagesIntegrationTest` — `aBlankTicketShowsTheFormAgainWithTheProblem`, `aFileThatIsNotAnImageIsRefused` | COND-002 | MEDIUM | 🟢L |
| TKT-TC-011 | `ServiceIntegrationTest` — `assigneeMustBeAbleToHandleTheTicket` | COND-004 | HIGH | 🟢L |
| TKT-TC-012 | `HsePagesIntegrationTest` — `assigningAndResolvingATicketMovesItThroughTheQueue` | COND-004 | MEDIUM | 🟢L |
| TKT-TC-013 | `ServiceIntegrationTest` — `softDeletedTicketDisappears` | COND-005 | MEDIUM | 🟢L |
| TKT-TC-015 | `HsePagesIntegrationTest` — `anotherRolesTicketCannotBeHandled` | COND-005 | HIGH | 🟢L |
| TKT-TC-017 | `PagesIntegrationTest` — `youCanCommentOnYourOwnTicketButNotSomeoneElses` | COND-006 | MEDIUM | 🟢L |
| TKT-TC-019 | `RepositoryIntegrationTest` — `ticketCannotHalfResolve` | COND-008 | MEDIUM | 🟢L |
| TKT-TC-020 | `RepositoryIntegrationTest` — `openTicketCannotCarryAResolver` | COND-008 | MEDIUM | 🟢L |
| TKT-TC-021 | `RepositoryIntegrationTest` — `violationHasOnlyOneLiveDisputeTicket` | COND-008 | HIGH | 🟢L |
| TKT-TC-022 | `RepositoryIntegrationTest` — `softDeletedDisputeFreesTheViolation` | COND-008 | MEDIUM | 🟢L |
| TKT-TC-023 | `ServiceAtomicityTest` — `failedTicketInsertLeavesTheViolationUndisputed` | COND-003 | HIGH | 🟢L |
| TKT-TC-030 | *(new)* A delete endpoint for handlers, refusing pending disputes — or drop `TICKET_DELETE_*` (gap G2) | COND-005 | LOW | 🔴 |
| TKT-TC-032 | *(new)* Subject 201 chars / description 4001 chars are refused with the length message | COND-002 | LOW | 🔴 |
| TKT-TC-033 | *(new)* `?all=true` is ignored for HSE (no `TICKET_READ_ALL`) and works for Admin | COND-001 | MEDIUM | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `TKT-TC-001…023, 031` | `ServiceIntegrationTest`, `RepositoryIntegrationTest`, `HsePagesIntegrationTest`, `PagesIntegrationTest`, `TeamsTicketsAndReportTest`, `WorkerAccessPagesTest`, `ServiceAtomicityTest` | `[ ]` not evidenced | `94a2c3c` (last local run) | `listHandled`, `listAll`, `listOwn(List)` unused by pages |
| `TKT-TC-030, 032, 033` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```java
public Page<Ticket> listQueue(Long actorId, boolean resolved, boolean everyCategory, Pageable page) {
    throw new UnsupportedOperationException("Not implemented — Red Phase stub");
}
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `TKT-TC-001` | `throw` in `listQueue` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | ☐ Tautology ☐ Shared state ☐ Hallucinated import |
| `TKT-TC-014` | `can_read_ticket` replaced by `SELECT true` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `TKT-TC-033` | current code | 🔴 FAIL or 🟢 (pins existing behaviour) | ☐ — not run | |

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3)
- Log file: `target/surefire-reports/`

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-TKT-IMP-001` approved
- [ ] Logic Issues confirmed (L3 is behaviour as built; L4 needs a decision)
- [ ] Private `ticket-photos` bucket available for any manual check

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` green (Docker)
- [ ] Coverage ≥ 80% for new files — **no JaCoCo**; add or waive
- [ ] `TranslationsTest` green for any new message
- [ ] No orphan photo after a failed save — TKT-TC-009 checks that no ticket row is created; add an assertion that the storage folder stays empty

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)**
- [ ] **Contract Existence:** `sh ./mvnw -q -o test-compile` clean
- [ ] **Props Isolation:** `@Transactional` per class; `ServiceAtomicityTest` cleans up
- [ ] **Oracle Source** cited

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable
- Routing change (needs ADR-016 update)
- CI broken by another change

---

## 7. Rollback Plan

```bash
git checkout -- src/main/java/orochi/steelsight/ticketing src/main/java/orochi/steelsight/web/TicketController.java
# Testcontainers DB is throwaway. Gap G2 stays OPEN in 02_design/MODULE-MAP.md §6
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ not verified | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ TKT-TC-030 — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ none | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☑ all exist at `f005861` | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-003` | `TKT-TC-030` | Delete UI not designed | Decide: build it or remove `TICKET_DELETE_*` | ☐ |
| Behaviour note | `TKT-TC-013` | Asserts 403 for a deleted ticket, which may surprise reviewers expecting 404 | Keep; documented in L3 | ☑ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
