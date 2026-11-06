# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-NTF-TDD-001`
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
- `03_implement/STEELSIGHT-NTF-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-005`, `ADR-014`, `ADR-019`, `ADR-022`
- `Luật 91/2025/QH15` — scores in notifications

> **Quy ước TDD:** viết test → chạy → FAIL 🔴 → implement → PASS 🟢 → refactor 🔵. Không mark ✅ nếu `sh ./mvnw verify` chưa xanh. Synthetic users; API keys in tests are fixed test strings, never production keys.
>
> **Retrospective note & status legend:** see `STEELSIGHT-IAM-TDD-001`. 🟢V = re-run here on `f005861` · 🟢L = last local run 2026-11-06 15:31 (code of `94a2c3c`), not re-run (no Docker) · 🔴 = not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho Notification & Detection API |

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
| **Feature / Gap ID** | `NTF` (existing) · gaps `G5` (quiet-period race), `G10` (no OpenAPI) |
| **Module** | `NTF — Notification & Detection API (Alerting)` |
| **Spec gốc** | `STEELSIGHT-NTF-IMP-001` |
| **Priority** | 🟠 P1 |
| **Sprint** | `Not defined — work done 2026-11-05 → 2026-11-06` |
| **Milestone** | `Demo — date not recorded` |
| **Data Classification** | `PII` (scores) / `Internal` (alerts) |
| **Compliance Scope** | `Luật 91/2025/QH15` — DPO pending |
| **Upstream Dependencies** | `IAM`, `PPE` (`safety_scores()`), detection service (HTTP caller) |
| **Downstream Consumers** | Sidebar bell, notification page, worker dashboard |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` (`chatlog/ai-log.md` 2026-11-05 15:47) |
| **Constraint Source** | `STEELSIGHT-NTF-IMP-001 §17`, `ADR-019/022` |
| **Constraints Injected** | Not recorded for the original code; new TCs: C1–C5 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | Template error shape `{"error": {"code", "message", "details"}}` | `{"error": "<message>"}` (gap G4) | Assertions check status and `$.error` text |
| L2 | Quiet period = "15 minutes after the last alert" | 15 minutes after the last alert **that notified**; silent alerts don't restart the clock | NTF-TC-005 asserts a silent alert in between doesn't block the next notice |
| L3 | Notification per violation | One per level per worker per month (trigger + unique index) | NTF-TC-010 asserts WARNING at the 3rd counted hit, DANGER at the 6th, never repeated |
| L4 | Quiet-period check is atomic | Check-then-insert without a lock (gap G5) | NTF-TC-030 🔴 |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
NTF bao gồm các layer:
├── Security filter          ApiKeyFilter + apiFilterChain (stateless, 401 JSON)    (MockMvc)
├── REST controller          DetectionAlertController + ApiExceptionHandler          (MockMvc)
├── Services                 SafetyAlertService, NotificationService                 (Testcontainers)
├── DB trigger               notify_safety_level() on violation                      (Testcontainers, SafetyScoreTest)
└── Pages                    /notifications, bell count                              (MockMvc)
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-SYS-001, US-HSE-006, US-WRK-005 | Alert API, HSE notice, worker level notice |
| `ADR-005`, `ADR-019`, `ADR-014`, `ADR-022` | Idempotency, key, quiet period, trigger, no stored text |
| `BR-NTF-001` … `BR-NTF-006` | Rules |
| `docs/deployment.md` §6 | "The detection API refuses a wrong `X-Api-Key`" |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | Key required; none configured → refuse all | `ApiKeyFilter` | NTF-TC-001, 002 |
| TC-COND-002 | Valid alert → 201 + HSE notices to active officers only | `SafetyAlertService.record` | NTF-TC-003 |
| TC-COND-003 | Same `eventId` → 200 duplicate, nothing new | `ON CONFLICT DO NOTHING` | NTF-TC-004 |
| TC-COND-004 | Quiet period and its restart rule | `existsByNotifiedTrueAndReceivedAtAfter` | NTF-TC-005, 006 |
| TC-COND-005 | Body validation → 400 JSON | `problemWith`, `ApiExceptionHandler` | NTF-TC-007, 008 |
| TC-COND-006 | API chain doesn't weaken page CSRF | `SecurityConfig` | NTF-TC-009 |
| TC-COND-007 | Level notices from the trigger | `notify_safety_level()` | NTF-TC-010…012 |
| TC-COND-008 | Own notifications; mark read | `NotificationService` | NTF-TC-013…015 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Equivalence Partitioning | Key: missing / wrong / right / none configured | Auth classes |
| Boundary Value Analysis | `violationCount` 0/1, `windowSeconds` 0/1, `cameraCode` 100/101 chars; quiet period 14:59/15:00/20:00 | Validation and throttle |
| State Transition Testing | Worker level SAFE → WARNING → DANGER within a month | Each notice once |
| Error Guessing | Malformed JSON, bad UUID, retries, concurrent alerts | Robustness |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | config | `steelsight.detection.api-key=<test key>` (and empty in `DetectionApiWithoutKeyTest`) | Auth |
| `FX-002` | DB seed | Active HSE `API-H1`, `API-H2`; inactive HSE; a worker and a manager | Recipients |
| `FX-003` | JSON | `{eventId: random UUID, violationCount: 3, windowSeconds: 60, detectedAt: now, cameraCode: "CAM-1"}` | Body |
| `FX-004` | DB update | `UPDATE safety_alert SET received_at = received_at - interval '20 minutes'` | Time travel for the quiet period |
| `FX-005` | DB seed | Violations in blocks 0…6 for one worker (`hit(worker, type, block)`) | Level notices |

---

## 4. Test Case Specification

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

```java
@SpringBootTest(properties = "steelsight.detection.api-key=test-key")
@AutoConfigureMockMvc @Import(TestDatabase.class) @Transactional
class AlertNewCasesTest extends PageTestSupport {
    ResultActions send(UUID eventId) throws Exception {          // a new body per call — no shared payload object
        return mvc.perform(post("/api/v1/detection/alerts")
                .header("X-Api-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"%s","violationCount":3,"windowSeconds":60,"detectedAt":"%s","cameraCode":"CAM-1"}
                    """.formatted(eventId, OffsetDateTime.now())));
    }
}
```

---

### NTF-TC-001 — A missing or wrong key is refused as JSON

**Severity:** `CRITICAL`
**OWASP:** `A07:2021 — Identification and Authentication Failures`
**CWE:** `CWE-306 — Missing Authentication for Critical Function`
**Feature Under Test:** `ApiKeyFilter`, `apiFilterChain` entry point
**Test File:** `DetectionAlertApiTest.java` — `aMissingOrWrongKeyIsRefusedAsJson`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-NTF-002`, `ADR-019`

**Test Steps (Attack Simulation):** POST a valid body with no header, then with a wrong key
**Expected Result (PASS = hệ thống an toàn):** `401`, `application/json`, `{"error":"A valid API key is required"}`; no `safety_alert` row
**Expected Result (FAIL = lỗ hổng tồn tại):** anyone can trigger HSE alerts
**Current Status:** 🟢L

---

### NTF-TC-002 — With no key configured, every call is refused

**Severity:** `CRITICAL`
**CWE:** `CWE-1188 — Insecure Default Initialization`
**Feature Under Test:** `ApiKeyFilter(key = "")`
**Test File:** `DetectionApiWithoutKeyTest.java` — `everyCallIsRefused`
**TDD Phase:** 🟢 GREEN
**Oracle Source:** `ADR-019` (fail closed)

**Expected Result (PASS):** `401` for `X-Api-Key: ""` and for `X-Api-Key: anything`
**Expected Result (FAIL):** empty key matches an empty header → open API
**Current Status:** 🟢L

---

### NTF-TC-003 — An alert tells every active HSE officer and nobody else

**Severity:** `HIGH`
**Feature Under Test:** `SafetyAlertService.record/tellHse`
**Test File:** `DetectionAlertApiTest.java` — `anAlertTellsEveryActiveHseOfficerAndNobodyElse`, `theCameraCodeIsOptional`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-002`
**Oracle Source:** `BR-NTF-004`

**Expected Result (PASS):** `201 {"status":"created"}`; one notice for each of the two active HSE officers; none for the inactive officer or the worker; the HSE notification page shows "Too many PPE violations", "7 violations in 60 seconds" and the camera code
**Current Status:** 🟢L

---

### NTF-TC-005 — A steady stream of alerts still tells HSE again after the quiet time

**Severity:** `MEDIUM`
**Feature Under Test:** quiet-period rule (`notified = true` only restarts the clock)
**Test File:** `DetectionAlertApiTest.java` — `aSteadyStreamOfAlertsStillTellsHseAgainAfterTheQuietTime`, `anotherAlertSoonAfterIsKeptWithoutTellingHseAgain`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `BR-NTF-004`, `ADR-019`

**Test Steps:**
1. Alert A notifies; move it 20 minutes into the past (FX-004)
2. Insert a silent alert (`notified=false`) just now
3. Send alert C

**Expected Result (PASS):** C notifies (2 notices total); separately, two alerts a moment apart → 2 rows, 1 notice
**Expected Result (FAIL):** a continuous bad spell silences HSE forever
**Current Status:** 🟢L

---

### NTF-TC-010 — A warning comes with the third counted violation and a danger with the sixth

**Severity:** `HIGH`
**Feature Under Test:** `notify_safety_level()` trigger, `uq_notification_level`
**Test File:** `SafetyScoreTest.java` — `aWarningComesWithTheThirdCountedViolationAndADangerWithTheSixth`, `jumpingPastWarningSendsOnlyTheDanger`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-007`
**Oracle Source:** `BR-NTF-001`, `BR-PPE-006` (70 → WARNING, 40 → DANGER)

**Test Steps:** FX-005 — NO_VEST in blocks 0…6
**Expected Result (PASS):** none after 2 hits; `SAFETY_WARNING` after the 3rd (score 70); still only WARNING after the 5th; `SAFETY_DANGER` added at the 6th (40); nothing new at the 7th. When six violations are named at once (trigger off), the next one sends **only** DANGER
**Expected Result (FAIL):** notice per violation (bell flood) or a missing level
**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### NTF-TC-009 — Pages still require a CSRF token

**Severity:** `HIGH`
**OWASP:** `A01:2021`
**CWE:** `CWE-352`
**Feature Under Test:** the page chain, next to the API chain with CSRF off
**Test File:** `DetectionAlertApiTest.java` — `pagesStillRequireACsrfToken`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** a signed-in HSE officer's `POST /notifications/read-all` without `_csrf` → 403
**Expected Result (FAIL):** disabling CSRF for the API leaked to pages
**Current Status:** 🟢L

---

### NTF-TC-008 — A bad body is refused as JSON, never an HTML page or stack trace

**Severity:** `MEDIUM`
**CWE:** `CWE-209 — Information Exposure Through an Error Message`
**Feature Under Test:** `ApiExceptionHandler`
**Test File:** `DetectionAlertApiTest.java` — `aBadBodyIsRefusedAsJson`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** each of: `not json`, `{}`, a bad UUID, `violationCount: 0`, `windowSeconds: 0`, missing `detectedAt` → `400` with a JSON `error` field; no alert row stored
**Current Status:** 🟢L

---

### NTF-TC-014 — Notifications are your own and can be marked read

**Severity:** `HIGH`
**CWE:** `CWE-639`
**Feature Under Test:** `NotificationService.markRead/listOwn`
**Test File:** `WorkerAccessPagesTest.java` — `notificationsAreYourOwnAndCanBeMarkedRead`
**TDD Phase:** 🟢 GREEN

**Test Steps (Attack Simulation):** worker B POSTs `/notifications/{A's id}/read`
**Expected Result (PASS):** 403; B's list says "You have no notifications"; A marks it read (302) and `read_at` is set
**Current Status:** 🟢L

---

### NTF-TC-030 — Two alerts at the same moment notify HSE once

**Severity:** `LOW`
**CWE:** `CWE-362 — Race Condition`
**Feature Under Test:** `SafetyAlertService.record` (gap G5)
**Test File:** *(new)* `DetectionAlertApiTest.java` (two threads, no class-level `@Transactional`)
**TDD Phase:** 🔴 RED — may fail intermittently today

**Test Steps:** two threads each `record()` a new eventId, released together by a `CountDownLatch`
**Expected Result (PASS):** two `safety_alert` rows, exactly one with `notified = true`
**Expected Result (FAIL):** both notify (double bell)
**Current Status:** 🔴 Not written
**Implementation Note:** e.g. `SELECT pg_advisory_xact_lock(<const>)` at the start of `record()`.

---

### NTF-TC-031 — No rate limit on the API

**Severity:** `LOW`
**OWASP:** `A04:2021 — Insecure Design`
**CWE:** `CWE-770 — Allocation of Resources Without Limits`
**Feature Under Test:** `/api/v1/detection/alerts`
**Test File:** *(new)*
**TDD Phase:** 🔴 RED — design decision needed

**Expected Result (PASS):** after N calls/minute with a valid key → 429 (N to be decided)
**Current Status:** 🔴 Not written

---

### INTEGRATION TEST CASES

---

### NTF-TC-INT-001 — A retry of the same event changes nothing

**Severity:** `HIGH`
**Feature Under Test:** Full flow: HTTP → filter → controller → `INSERT … ON CONFLICT (event_id) DO NOTHING RETURNING id`
**Test File:** `DetectionAlertApiTest.java` — `aRetryOfTheSameEventChangesNothing`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-003`

**Preconditions:** Testcontainers PostgreSQL; FX-001, FX-002

**Test Steps:**
1. Send eventId E → 201
2. Send E again → 200
3. Count rows

**Expected Result (PASS):** `{"status":"duplicate"}`; 1 alert row; notice count unchanged

**DB Assertion:**
```java
assertThat(jdbc.queryForObject("SELECT count(*) FROM safety_alert", Long.class)).isEqualTo(1);
```

**Current Status:** 🟢L

---

### TC catalogue — remaining existing tests

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| NTF-TC-004 | `DetectionAlertApiTest` — `aRetryOfTheSameEventChangesNothing` | COND-003 | HIGH | 🟢L |
| NTF-TC-006 | `DetectionAlertApiTest` — `anotherAlertSoonAfterIsKeptWithoutTellingHseAgain` | COND-004 | MEDIUM | 🟢L |
| NTF-TC-007 | `DetectionAlertApiTest` — `theCameraCodeIsOptional` | COND-005 | LOW | 🟢L |
| NTF-TC-011 | `SafetyScoreTest` — `jumpingPastWarningSendsOnlyTheDanger` | COND-007 | MEDIUM | 🟢L |
| NTF-TC-012 | `SafetyScoreTest` — `theDetectionRoleCanInsertAndTheTriggerStillWritesTheNotice` | COND-007 | HIGH | 🟢L |
| NTF-TC-013 | `WorkerAccessPagesTest` — `aWorkerAtWarningLevelSeesTheReminderAndTheBell` | COND-008 | MEDIUM | 🟢L |
| NTF-TC-015 | `WorkerAccessPagesTest` — `aWorkerAtDangerLevelIsToldAboutTheirPay` | COND-008 | MEDIUM | 🟢L |
| NTF-TC-032 | *(new)* `cameraCode` of 101 characters → 400 "cameraCode is too long" | COND-005 | LOW | 🔴 |
| NTF-TC-033 | *(new)* An OpenAPI document for `/api/v1/**` is published and matches the controller (gap G10) | — | LOW | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `NTF-TC-001…009` | `DetectionAlertApiTest.java`, `DetectionApiWithoutKeyTest.java` | `[ ]` not evidenced | `94a2c3c` (last local run) | — |
| `NTF-TC-010…015` | `SafetyScoreTest.java`, `WorkerAccessPagesTest.java` | `[ ]` | `94a2c3c` | — |
| `NTF-TC-030…033` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

**Stub cho Red Phase:**

```java
public boolean record(NewAlert alert) {
    throw new UnsupportedOperationException("Not implemented — Red Phase stub");
}
// For the filter: ApiKeyFilter.matches(...) returning true always — NTF-TC-001/002 must then FAIL.
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `NTF-TC-001` | `matches()` → true | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | ☐ Tautology ☐ Shared state ☐ Hallucinated import |
| `NTF-TC-003` | `throw` in `record` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `NTF-TC-010` | trigger dropped | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3)
- Log file: `target/surefire-reports/`

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-NTF-IMP-001` approved
- [ ] Detection team agrees the request/response contract (§9.2 of the TDS)
- [ ] Decision on NTF-TC-031 (rate limit) recorded

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` green (Docker)
- [ ] Coverage ≥ 80% for new files — **no JaCoCo**; add or waive
- [ ] The API key appears in no log line (`grep` in `RequestLoggingTest` style)
- [ ] `TranslationsTest` green for any new notification kind

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)**
- [ ] **Contract Existence:** `sh ./mvnw -q -o test-compile` clean
- [ ] **Props Isolation:** `@Transactional` per class; NTF-TC-030 commits for real and cleans up in `@AfterEach`
- [ ] **Oracle Source** cited

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable
- Detection contract change pending
- CI broken by another change

---

## 7. Rollback Plan

```bash
git checkout -- src/main/java/orochi/steelsight/notification src/main/java/orochi/steelsight/api src/main/java/orochi/steelsight/security/ApiKeyFilter.java
# Gaps G5, G10 stay OPEN in 02_design/MODULE-MAP.md §6
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ not verified | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ NTF-TC-031 (rate limit) — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ validation lives in the controller by design (ADR-019); accepted | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại | ☑ all exist at `f005861` | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-003` | `NTF-TC-031` | Rate limit not designed | ADR or explicit waiver | ☐ |
| `AP-AI-004` (accepted) | `NTF-TC-007/008` | Body validation in `DetectionAlertController.problemWith` | Keep; it's input validation, not a business rule | ☑ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
