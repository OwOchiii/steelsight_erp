# TEST-DRIVEN DEVELOPMENT SPECIFICATION TEMPLATE
# Mẫu Đặc tả Kiểm thử Hướng Phát triển

**Document ID:** `STEELSIGHT-IAM-TDD-001`
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
- `02_design/MODULE-MAP.md` — BR / US catalog (no SRS exists in the repository)
- `03_implement/STEELSIGHT-IAM-IMP-001_TDS.md` — Technical Specification
- `02_design/adr/ADR-001`, `ADR-007`, `ADR-008`, `ADR-009`, `ADR-010`
- `Luật 91/2025/QH15`, `NĐ 356/2025/NĐ-CP` — legal basis (article mapping pending DPO)

> **Quy ước TDD:** Tài liệu này mô tả test cases TRƯỚC khi viết production code.
> Thứ tự bắt buộc: viết test → chạy → xác nhận FAIL 🔴 → implement → PASS 🟢 → refactor 🔵.
> Không mark test là ✅ nếu `sh ./mvnw verify` chưa xanh.
> Test data: synthetic users seeded per test class into a Testcontainers PostgreSQL. Không dùng PII thật.
>
> **Retrospective note.** The production code and most tests already exist; they were written together with AI assistance (`chatlog/ai-log.md`), not test-first. This spec records the existing tests as TCs with their real status, and adds 🔴 TCs for gaps found while reading the code. The Red Gate (§5.1) was **not** performed for existing tests.
>
> **Status legend:** 🟢V = passing, re-run in this session on `f005861` · 🟢L = passing in the last local run (2026-11-06 15:31, code of `94a2c3c`; no source changes since), **not re-run here** because Docker isn't available · 🔴 = not written.

---

## CHANGELOG

> **Policy 4.4 — Immutable History:** Không bao giờ xóa thông tin cũ.

| Ngày | Người thực hiện | Nội dung thay đổi |
|------|-----------------|-------------------|
| `2026-11-06` | `Claude Code (Opus 5.5)` | Khởi tạo tài liệu — TDD spec cho IAM, mapped from the existing suite |

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
| **Feature / Gap ID** | `IAM` (existing) · gaps `G3`, `G6`, `G9` (`02_design/MODULE-MAP.md` §6) |
| **Module** | `IAM — Identity & Access` |
| **Spec gốc** | `STEELSIGHT-IAM-IMP-001` |
| **Priority** | 🔴 P0 |
| **Sprint** | `Not defined — work done 2026-11-02 → 2026-11-06` |
| **Milestone** | `Demo — date not recorded in the repository` |
| **Data Classification** | `PII` (gates `Sensitive-PII`) |
| **Compliance Scope** | `Luật 91/2025/QH15`, `NĐ 356/2025` — articles pending DPO |
| **Upstream Dependencies** | `PLT`, `SCH` (TeamRepository), `ATT` (FaceEmbeddingRepository), `STO` |
| **Downstream Consumers** | All modules (`AccessPolicy`) |

### 1.1 AI Generation Context (CASE 2.0)

| Field | Value |
|-------|-------|
| **AI Assisted?** | `Yes` — code and tests (`chatlog/ai-log.md`, `transcript/*.md`); this spec too |
| **Constraint Source** | `STEELSIGHT-IAM-IMP-001 §17`, `ADR-001/008/009/010 §AI Prompt Constraint` |
| **Constraints Injected** | Not recorded for the original code; for new TCs: C1–C6 of TDS §17.1 |
| **Model** | `Claude Code — Opus 5.5` |
| **Trust Level** | `T2` (Red Gate not performed) → `T3` after §5.1 |

---

## 2. Logic Issues Resolved

| # | Spec gốc (sai / thiếu) | Thực tế (schema / policy) | Fix áp dụng trong test |
|---|------------------------|--------------------------|------------------------|
| L1 | V1 gave managers `USER_UPDATE_TEAM` (edit own team's workers) | V10 removed it; `can_update_user()` = `USER_MANAGE` only | TCs assert managers get 403 on every account action, even for their own team (IAM-TC-012, IAM-TC-013) |
| L2 | Template assumes JWT Bearer auth | Session cookie + CSRF; `employeeCode` form login | Tests use MockMvc `user(...)`/`formLogin()`; CSRF-missing cases expect 403 |
| L3 | Template assumes error codes in responses | Only messages (gap G4) | Assertions match message text or status, never a code |
| L4 | `ROLE_MANAGE` seeded and held by Admin | Never checked (gap G3) | IAM-TC-030 🔴 records the decision needed; no behaviour asserted |
| L5 | First admin is expected to be "forced to change" like other admin-set passwords | `AdminBootstrap` doesn't set `must_change_password` (gap G9) | IAM-TC-031 🔴 encodes the expected behaviour (fail today) |
| L6 | Sign-in counts are assumed durable | In memory; restart clears (gap G6) | IAM-TC-032 🔴 documents it as accepted behaviour |

---

## 3. Test Design Specification (TDS)

### TDS-01 — Scope / Phạm vi

```
IAM bao gồm các layer:
├── Domain / pure logic          SignInLimiter (no DB) — unit
├── Services                     UserService, AccessPolicy (SQL functions) — Testcontainers PostgreSQL
├── Security filters             SignInLimitFilter, PasswordChangeRequiredFilter, FirstSignInHandler — MockMvc
├── Controller / pages           TeamController (accounts), AccountController, /login — MockMvc + Spring Security Test
└── Database rules               has_permission, can_update_user, unique code/email, erase-embedding trigger
```

### TDS-02 — Test Basis / Cơ sở Kiểm thử

| Source | Items Derived |
|--------|--------------|
| `MODULE-MAP.md` US-ADM-002, US-ADM-003, US-ALL-001 | Account management, face photo, sign-in, own password |
| `ADR-001` | Permission AND scope checked in SQL |
| `ADR-008`, `ADR-009`, `ADR-010` | Limiter thresholds; forced change; admin-only |
| `BR-IAM-001` … `BR-IAM-011` | Rules in §3.1 of MODULE-MAP |
| Luật 91/2025/QH15 | Access control, security of personal data |
| `STEELSIGHT-IAM-IMP-001` §8, §16 | Signatures; authorization matrix |

### TDS-03 — Test Conditions and Coverage Items

| Condition ID | Test Condition | Coverage Item | Test Cases |
|-------------|---------------|---------------|-----------|
| TC-COND-001 | Valid credentials sign in; role and permissions loaded | `AppUserDetailsService`, `AuthenticatedUser` | IAM-TC-001 |
| TC-COND-002 | Wrong password = unknown code answer; deactivated/deleted refused | `formLogin`, `AuthenticatedUser.enabled` | IAM-TC-002…004 |
| TC-COND-003 | CSRF required for login and forms | Spring Security | IAM-TC-005 |
| TC-COND-004 | Limiter: 5 per client, 20 per code, spoofed addresses, success clears own only | `SignInLimiter`, `SignInLimitFilter` | IAM-TC-006…008 |
| TC-COND-005 | Admin creates account: hashed, must change, face queued | `UserService.create`, `POST /team/new` | IAM-TC-009, 010 |
| TC-COND-006 | Input validation and duplicates | `UserService.create/updateProfile` | IAM-TC-011, 014, 015 |
| TC-COND-007 | Only admins manage accounts | `can_update_user`, `TeamController` | IAM-TC-012, 013 |
| TC-COND-008 | Self-protection and leader protection | `changeRole/setActive/softDelete` | IAM-TC-016, 017 |
| TC-COND-009 | Forced password change flow | `FirstSignInHandler`, `PasswordChangeRequiredFilter`, `changeOwnPassword` | IAM-TC-018…020 |
| TC-COND-010 | Bootstrap creates one admin only with a password | `AdminBootstrap` | IAM-TC-021, IAM-TC-031 🔴 |
| TC-COND-011 | Stale profile edit fails | `requireVersion` | IAM-TC-022 |
| TC-COND-012 | Soft delete erases embedding; face photo files after commit | `softDelete`, trigger | IAM-TC-023, 024 |
| TC-COND-013 | Admin holds every permission | seed data | IAM-TC-025 |
| TC-COND-014 | Deactivation takes effect on the next request | `has_permission` reads `is_active` | IAM-TC-033 🔴 |

### TDS-04 — Test Techniques / Kỹ thuật Kiểm thử

| Technique (ISO 29119-4) | Applied To | Rationale |
|------------------------|------------|-----------|
| Equivalence Partitioning | Employee code (valid / invalid chars / too long), email (valid / no `@`), role names | Few classes cover the input domain |
| Boundary Value Analysis | Password 7/8 chars, 72/73 UTF-8 bytes; limiter 4/5 and 19/20 failures | Off-by-one is the likely bug |
| State Transition Testing | Account: must-change → active → deactivated → deleted (TDS §6.3) | Each transition has a guard |
| Error Guessing | CSRF missing, XSS in names, log forging via `\r\n` in codes, spoofed `X-Forwarded-For` | Known attack vectors |

### TDS-05 — Test Data Requirements

| Fixture ID | Type | Value / Logic | Mục đích |
|-----------|------|---------------|---------|
| `FX-001` | DB seed | Roles and permissions from Flyway V1–V11 | Real permission data |
| `FX-002` | DB seed | `ADMIN` user `A001`, BCrypt of a synthetic password | Actor for account actions |
| `FX-003` | DB seed | `MANAGER` `M001` leading team `Tổ 1`; `WORKER` `W001`, `W002` on it | Scope and leader rules |
| `FX-004` | DB seed | `HSE_OFFICER` `H001` | Non-admin actor |
| `FX-005` | Principal | `user(new AuthenticatedUser(appUser))` (Spring Security Test) | Session context without a real login |
| `FX-006` | File | Generated PNG bytes (`89 50 4E 47 …`) and a fake `.jpg` text file | Face photo upload |
| `FX-007` | env | `steelsight.bootstrap.admin-password` set/unset | Bootstrap cases |

---

## 4. Test Case Specification

> **TC ID format:** `IAM-TC-[NNN]` · **Severity:** CRITICAL / HIGH / MEDIUM / LOW · **Status:** 🔴 Not written / 🟡 Written-failing / 🟢 Passing

### Props Isolation Boilerplate (CASE 2.0 — BẮT BUỘC)

> The suite isolates data per test with a **rolled-back transaction** (`@Transactional` on the class) and a fresh `@BeforeEach seed()`; page tests share helpers from `PageTestSupport`. Use this pattern for new TCs.

```java
// ═══════════════════════════════════════════════════════════
// CASE 2.0 — Props Isolation Pattern (JUnit 6 + Spring Boot Test)
// ═══════════════════════════════════════════════════════════
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)          // one pgvector/pgvector:pg17 container for the whole run
@Transactional                       // every test rolls back: no shared mutable state
class IamNewCasesTest extends PageTestSupport {

    Long admin, manager, worker;     // ids only — re-created for every test

    @BeforeEach
    void seed() {                    // baseline = FX-002 / FX-003
        admin = userWithRole("A001", "Admin One", "ADMIN");
        manager = userWithRole("M001", "Manager One", "MANAGER");
        worker = userWithRole("W001", "Worker One", "WORKER");
        joinTeam(worker, team("Tổ 1", manager));
    }
}
// Pure unit tests (no Spring): create the object under test in a field initialiser or per test,
// e.g. `private final SignInLimiter limiter = new SignInLimiter();` — JUnit makes a new instance per test.
```

---

### IAM-TC-001 — Correct password signs in with role and permissions

**Severity:** `CRITICAL`
**Legal:** `Luật 91/2025/QH15 — access control`
**Feature Under Test:** `POST /login` → `AppUserDetailsService`, `AuthenticatedUser`
**Test File:** `src/test/java/orochi/steelsight/LoginIntegrationTest.java` — `correctPasswordSignsInWithRoleAndPermissions`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-001`
**Oracle Source:** `BR-IAM-001`, `ADR-008 §Decision`

**Preconditions:**
- FX-001, a user with a known synthetic password

**Test Steps:**
1. Arrange: seed the user (BCrypt hash)
2. Act: `formLogin().user("employeeCode", code).password(pw)`
3. Assert: authenticated; authorities contain `ROLE_<role>` and the role's permission codes

**Expected Result (PASS — hành vi đúng):**
- Redirect to `/`; principal is `AuthenticatedUser` with the right `userId` and authorities

**Expected Result (FAIL — dấu hiệu lỗi):**
- Login refused, or authorities missing a permission (UI would hide actions)

**Current Status:** 🟢L
**Implementation Note:** username parameter is `employeeCode`, not `username`.

---

### IAM-TC-006 — Repeated wrong passwords lock the sign-in, even with the right password

**Severity:** `HIGH`
**CWE:** `CWE-307 — Improper Restriction of Excessive Authentication Attempts`
**Legal:** `Luật 91/2025/QH15 — security measures`
**Feature Under Test:** `SignInLimitFilter` + `SignInLimiter`
**Test File:** `DeploymentEndpointsTest.java` — `repeatedWrongPasswordsLockTheSignInEvenWithTheRightPassword`; unit: `security/SignInLimiterTest.java` — `locksAClientAfterTooManyFailures`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `BR-IAM-004`, `ADR-008` (5 per client, 20 per code, 15 min)

**Preconditions:**
- Fresh limiter (new Spring context or new instance)

**Test Steps:**
1. Arrange: one user
2. Act: 5 wrong passwords from one address, then the right password
3. Assert: last response redirects to `/login?locked`

**Expected Result (PASS):**
- `302 /login?locked`; the password was never checked

**Expected Result (FAIL):**
- Signed in after 5 failures → brute force possible

**Current Status:** 🟢L (page) / 🟢V (unit)

---

### IAM-TC-007 — Spoofed addresses still hit the per-code limit

**Severity:** `HIGH`
**CWE:** `CWE-307`, `CWE-348 — Use of Less Trusted Source` (`X-Forwarded-For`)
**Feature Under Test:** `SignInLimiter.isLocked()`
**Test File:** `security/SignInLimiterTest.java` — `fakingADifferentAddressEachTimeStillHitsTheLimitForTheCode`, `differentSpellingOfACodeCountsAsTheSameCode`, `handlesAMissingOrVeryLongCode`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-004`
**Oracle Source:** `ADR-008 §Decision` (normalise: strip, upper-case, ≤ 64)

**Test Steps:**
1. Record 20 failures for `w001 ` from 20 addresses
2. Ask `isLocked("W001", newAddress)`

**Expected Result (PASS):** `true`; a different code is not locked; null and 10 000-char codes don't throw
**Expected Result (FAIL):** `false` → limit dodged by faking addresses or case
**Current Status:** 🟢V

---

### IAM-TC-009 — Admin creates an account that must change its password

**Severity:** `HIGH`
**Feature Under Test:** `POST /team/new`, `UserService.create()`
**Test File:** `AccountPagesTest.java` — `adminCreatesAnAccountThatMustChangeItsPassword`; `ServiceIntegrationTest.java` — `adminCreatesUsersWithAHashedPasswordAndOthersCannot`, `createQueuesTheFacePhotoAndTheStartingPasswordMustBeChanged`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-005`
**Oracle Source:** `BR-IAM-002`, `BR-IAM-003`, `ADR-009`

**Test Steps:**
1. As admin, post a complete form
2. Read the row

**Expected Result (PASS):** `password_hash` is BCrypt (not the raw password), `must_change_password = true`, redirect to `/team/{id}/edit`
**Expected Result (FAIL):** raw password stored, or flag false (admin keeps a working password)
**Current Status:** 🟢L

---

### IAM-TC-012 — Managers and workers can't manage accounts

**Severity:** `CRITICAL`
**OWASP:** `A01:2021 — Broken Access Control`
**CWE:** `CWE-285 — Improper Authorization`
**Feature Under Test:** every `/team/new`, `/team/{id}/*` account endpoint
**Test File:** `AccountPagesTest.java` — `managersAndWorkersCannotManageAccounts`; `ManagerPagesIntegrationTest.java` — `aManagerCannotEditEvenTheirOwnTeam`, `aManagerCannotEditOutsidersOrThemselves`; `ServiceIntegrationTest.java` — `onlyAnAdminEditsDetailsAndAManagerCannotEvenEditTheirOwnTeam`; `RepositoryIntegrationTest.java` — `onlyAnAdminEditsPeople`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-007`
**Oracle Source:** `ADR-010 §Decision`, `BR-IAM-005`

**Test Steps (Attack Simulation):**
1. Sign in as manager (leader of the worker's team) with a CSRF token
2. POST role change, deactivate, password reset, face photo, delete for the worker
3. Check response and DB

**Expected Result (PASS = hệ thống an toàn):** `403` for each; row unchanged; no photo stored
**Expected Result (FAIL = lỗ hổng tồn tại):** any change applied → privilege escalation
**Current Status:** 🟢L

---

### IAM-TC-018 — Someone given a password by an admin must choose their own first

**Severity:** `HIGH`
**CWE:** `CWE-620 — Unverified Password Change` (inverse: forced change)
**Feature Under Test:** `FirstSignInHandler`, `PasswordChangeRequiredFilter`, `POST /account/password`
**Test File:** `AccountPagesTest.java` — `someoneGivenAPasswordByAnAdminMustChooseTheirOwnFirst`, `everyoneCanChangeTheirOwnPasswordFromTheMenu`; `ServiceIntegrationTest.java` — `aResetPasswordMustBeChangedAndTheNewOneMustDiffer`, `passwordChangeNeedsTheCurrentPassword`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-009`
**Oracle Source:** `ADR-009 §Decision`, `BR-IAM-003`

**Test Steps:**
1. Admin resets W001's password
2. W001 signs in → redirected to `/account/password`; `GET /` also redirects there
3. W001 changes it (current correct, new different, confirm matches)
4. `GET /` now 200

**Expected Result (PASS):** forced page until changed; then normal; same-as-current refused; wrong current refused
**Expected Result (FAIL):** user can browse with the admin-known password
**Current Status:** 🟢L

---

### IAM-TC-023 — Soft delete erases the embedding; can't delete yourself or a leader

**Severity:** `HIGH`
**Legal:** `Luật 91/2025/QH15 — sensitive (biometric) data`
**Feature Under Test:** `UserService.softDelete()`, `trg_user_erase_embedding`
**Test File:** `ServiceIntegrationTest.java` — `softDeleteErasesTheEmbedding`, `cannotDeleteYourselfOrAManagerWhoStillRunsATeam`; `RepositoryIntegrationTest.java` — `softDeletingAWorkerErasesTheirEmbedding`; `LoginIntegrationTest.java` — `softDeletedUserCannotSignIn`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-012`, `TC-COND-008`
**Oracle Source:** `ADR-007 §Decision`, `BR-ATT-010`, `BR-IAM-006/007`

**Expected Result (PASS):** `deleted_at` set, `is_active=false`, `face_file_path` null, no `face_embedding` row; deleted user can't sign in; self/leader deletion → rule error
**Expected Result (FAIL):** embedding survives deletion (biometric retained)
**Current Status:** 🟢L

---

### SECURITY TEST CASES

---

### IAM-TC-005 — Forms and login without a CSRF token are refused

**Severity:** `HIGH`
**OWASP:** `A01:2021 — Broken Access Control` (CSRF)
**CWE:** `CWE-352 — Cross-Site Request Forgery`
**Feature Under Test:** Spring Security CSRF on the page chain
**Test File:** `LoginIntegrationTest.java` — `loginWithoutACsrfTokenIsRefused`; `PagesIntegrationTest.java` — `formsWithoutACsrfTokenAreRefused`
**TDD Phase:** 🟢 GREEN

**Test Steps (Attack Simulation):**
1. POST `/login` and a page form without `_csrf`

**Expected Result (PASS = hệ thống an toàn):** `403`
**Expected Result (FAIL = lỗ hổng tồn tại):** request processed → CSRF possible
**Current Status:** 🟢L

---

### IAM-TC-026 — A user's name is escaped on the page

**Severity:** `MEDIUM`
**OWASP:** `A03:2021 — Injection`
**CWE:** `CWE-79 — Cross-site Scripting`
**Feature Under Test:** `home.html` greeting
**Test File:** `LoginIntegrationTest.java` — `homeGreetsTheUserAndEscapesTheirName`
**TDD Phase:** 🟢 GREEN

**Expected Result (PASS):** `<script>` appears as `&lt;script&gt;`
**Expected Result (FAIL):** script executes for anyone viewing the page
**Current Status:** 🟢L

---

### IAM-TC-027 — Typed employee codes can't forge log lines; passwords never logged

**Severity:** `MEDIUM`
**CWE:** `CWE-117 — Improper Output Neutralization for Logs`; `CWE-532 — Sensitive Information in Log File`
**Feature Under Test:** `SignInLog`, `SignInLimitFilter`
**Test File:** `RequestLoggingTest.java` — `aFailedSignInIsLoggedWithoutThePassword`
**TDD Phase:** 🟢 GREEN (password); 🔴 for the CR/LF case — see IAM-TC-034

**Expected Result (PASS):** log has the code, never the password
**Current Status:** 🟢L

---

### IAM-TC-034 — A code containing CR/LF is logged on one line

**Severity:** `LOW`
**CWE:** `CWE-117`
**Feature Under Test:** `SignInLog.clean()`, `SignInLimitFilter`
**Test File:** *(new)* `src/test/java/orochi/steelsight/RequestLoggingTest.java`
**TDD Phase:** 🔴 RED — chưa viết

**Test Steps (Attack Simulation):**
1. POST `/login` with `employeeCode="W001\r\nINFO Signed in: ADMIN"` and a wrong password
2. Capture output

**Expected Result (PASS):** one log line containing `W001__INFO Signed in: ADMIN`; no line starting with `INFO Signed in: ADMIN`
**Expected Result (FAIL):** a forged second line
**Current Status:** 🔴 Not written

---

### IAM-TC-033 — Deactivation removes rights on the next request

**Severity:** `HIGH`
**OWASP:** `A01:2021`
**CWE:** `CWE-613 — Insufficient Session Expiration`
**Feature Under Test:** `has_permission()` (`u.is_active`) via any service call
**Test File:** *(new)* `ServiceIntegrationTest.java`
**TDD Phase:** 🔴 RED

**Test Steps (Attack Simulation):**
1. Sign in as manager M001 (session principal built before deactivation)
2. Admin deactivates M001
3. With the same principal, POST `/shifts` for a team worker

**Expected Result (PASS):** `403` — the SQL check sees `is_active = false`
**Expected Result (FAIL):** shift created by a deactivated account
**Current Status:** 🔴 Not written
**Implementation Note:** expected to pass already (`has_permission` checks `is_active`); the test pins it.

---

### IAM-TC-031 — The bootstrap admin must change the bootstrap password

**Severity:** `MEDIUM`
**CWE:** `CWE-1392 — Use of Default Credentials`
**Feature Under Test:** `AdminBootstrap.run()`
**Test File:** *(new)* `LoginIntegrationTest.java`
**TDD Phase:** 🔴 RED — **expected to fail today** (gap G9)
**Oracle Source:** `ADR-009` intent ("any password chosen by someone else must be changed")

**Test Steps:**
1. Start with no admin and `ADMIN_PASSWORD` set
2. Read the created admin

**Expected Result (PASS):** `must_change_password = true`
**Expected Result (FAIL):** `false` (current behaviour)
**Current Status:** 🔴 Not written
**Implementation Note:** one line in `AdminBootstrap`: `admin.setMustChangePassword(true)`. Needs product confirmation first.

---

### INTEGRATION TEST CASES

---

### IAM-TC-INT-001 — Admin creates an account with a face photo queued for enrolment

**Severity:** `HIGH`
**Feature Under Test:** Full flow: `POST /team/new` (multipart) → `PhotoStorage.store(FACE)` → `UserService.create` → `queueForEnrolment`
**Test File:** `AccountPagesTest.java` — `adminCreatesAnAccountWithAFacePhotoThatIsQueuedForEnrolment`
**TDD Phase:** 🟢 GREEN
**Condition Ref:** `TC-COND-005`

**Preconditions:**
- Testcontainers PostgreSQL, Flyway migrated; local photo storage in a temp folder
- Seed: FX-001, FX-002, FX-006

**Test Steps:**
1. Admin posts the form with a PNG
2. Assert DB and storage

**Expected Result (PASS):**
- `app_user.face_file_path` = stored name; one `face_embedding` row with `status='PENDING'`; the file exists in `faces/`

**Expected Result (FAIL):**
- Account without enrolment row, or file stored but no account (orphan)

**DB Assertion:**
```java
String status = jdbc.queryForObject(
    "SELECT status FROM face_embedding WHERE user_id = (SELECT id FROM app_user WHERE employee_code = ?)",
    String.class, "W010");
assertThat(status).isEqualTo("PENDING");
```

**Current Status:** 🟢L

---

### TC catalogue — remaining existing tests

| TC ID | Test (class — method) | Condition | Severity | Status |
|-------|-----------------------|-----------|----------|--------|
| IAM-TC-002 | `LoginIntegrationTest` — `wrongPasswordAndUnknownCodeGetTheSameAnswer` | COND-002 | HIGH | 🟢L |
| IAM-TC-003 | `LoginIntegrationTest` — `deactivatedUserCannotSignIn` | COND-002 | HIGH | 🟢L |
| IAM-TC-004 | `LoginIntegrationTest` — `softDeletedUserCannotSignIn`, `anonymousVisitorIsSentToLogin`, `logoutEndsTheSession` | COND-002 | HIGH | 🟢L |
| IAM-TC-008 | `SignInLimiterTest` — `oneClientsFailuresDoNotLockAnotherClientOrCode`, `successClearsOnlyThatClientsCount`; `DeploymentEndpointsTest` — `aFewWrongPasswordsDoNotBlockTheRightOne` | COND-004 | MEDIUM | 🟢V / 🟢L |
| IAM-TC-010 | `AccountPagesTest` — `aBadNewAccountIsShownAgainWithoutThePassword` | COND-005 | MEDIUM | 🟢L |
| IAM-TC-011 | `ServiceIntegrationTest` — `createChecksWhatTheAdminTyped` | COND-006 | MEDIUM | 🟢L |
| IAM-TC-013 | `AccountPagesTest` — `aManagerStillPlansShiftsAndSetsTheLateRule` | COND-007 | MEDIUM | 🟢L |
| IAM-TC-014 | `ServiceIntegrationTest` — `duplicateEmployeeCodeIsARuleError` | COND-006 | MEDIUM | 🟢L |
| IAM-TC-015 | `ManagerPagesIntegrationTest` — `anAdminCanEditSomeonesDetails`, `badProfileEditsAreRefused` | COND-006 | MEDIUM | 🟢L |
| IAM-TC-016 | `AccountPagesTest` — `anAdminsOwnAccountHidesTheActionsThatWouldLockThemOut` | COND-008 | MEDIUM | 🟢L |
| IAM-TC-017 | `AccountPagesTest` — `adminChangesRoleTeamStatusPasswordAndPhoto`, `deletingNeedsTheBoxTicked` | COND-008 | MEDIUM | 🟢L |
| IAM-TC-019 | `AccountPagesTest` — `everyoneCanChangeTheirOwnPasswordFromTheMenu` | COND-009 | MEDIUM | 🟢L |
| IAM-TC-020 | `ServiceIntegrationTest` — `passwordChangeNeedsTheCurrentPassword` | COND-009 | HIGH | 🟢L |
| IAM-TC-021 | `LoginIntegrationTest` — `bootstrapCreatesTheFirstAdminOnceAndOnlyWithAPassword` | COND-010 | HIGH | 🟢L |
| IAM-TC-022 | `ServiceIntegrationTest` — `staleProfileEditFails` | COND-011 | MEDIUM | 🟢L |
| IAM-TC-024 | `ServiceIntegrationTest` — `onlyAnAdminHandlesFacePhotos`; `FacePhotoCleanupTest` — `anOldFacePhotoIsRemovedOnlyAfterItsReplacementOrTheDeletionCommits` | COND-012 | HIGH | 🟢L |
| IAM-TC-025 | `RepositoryIntegrationTest` — `adminHoldsEveryPermission` | COND-013 | MEDIUM | 🟢L |
| IAM-TC-028 | `AccountPagesTest` — `uploadingNothingOrAFakeImageIsRefused` | COND-005 | MEDIUM | 🟢L |
| IAM-TC-030 | *(new)* `ROLE_MANAGE` decision: remove it or use it (gap G3) | — | LOW | 🔴 |
| IAM-TC-032 | *(new)* Restart clears sign-in counts (documented, accepted — gap G6) | COND-004 | LOW | 🔴 |

---

## 5. Red-Green-Refactor Tracker

| TC ID | Test File | 🔴 RED confirmed | 🟢 GREEN (commit) | 🔵 REFACTOR note |
|-------|-----------|-----------------|-------------------|------------------|
| `IAM-TC-001…004` | `LoginIntegrationTest.java` | `[ ]` not evidenced | `94a2c3c` (last local run) | — |
| `IAM-TC-006…008` | `security/SignInLimiterTest.java`, `DeploymentEndpointsTest.java` | `[ ]` | `f005861` (re-run) / `94a2c3c` | — |
| `IAM-TC-009…025, 028` | `AccountPagesTest`, `ServiceIntegrationTest`, `ManagerPagesIntegrationTest`, `RepositoryIntegrationTest`, `FacePhotoCleanupTest` | `[ ]` | `94a2c3c` | `saveUnique` gives one message for code and email clashes |
| `IAM-TC-026, 027` | `LoginIntegrationTest`, `RequestLoggingTest` | `[ ]` | `94a2c3c` | — |
| `IAM-TC-030…034` | *(new)* | `[ ]` | — | — |

### 5.1 Red Gate Protocol (CASE 2.0 — GATE-2)

> Before implementing a 🔴 TC, run it against a throwing stub and confirm it FAILS. For the existing 🟢 TCs this gate was **not** performed; to back-fill it, stub the method under test and run the class.

**Stub cho Red Phase:**

```java
// Red Phase — temporary stub of the method under test (MUST throw)
public void softDelete(Long actorId, Long userId) {
    throw new UnsupportedOperationException("Not implemented — Red Phase stub");
}
```

**Red Gate Verification:**

| TC ID | Stub Result | Expected | Actual | Root Cause (nếu PASS bất thường) |
|-------|-------------|----------|--------|----------------------------------|
| `IAM-TC-023` | `throw UnsupportedOperationException` | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | ☐ Tautology ☐ Shared state ☐ Hallucinated import |
| `IAM-TC-031` | current code (no stub needed) | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `IAM-TC-033` | `has_permission` stub returning true | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |
| `IAM-TC-034` | `clean()` returning input unchanged | 🔴 FAIL | ☐ FAIL ☐ PASS — not run | |

**Red Gate Evidence:**

- Stub commit hash: `___`
- Tất cả FAIL? ☐ Yes → **GATE-2 PASS** (T2→T3) → tiếp tục implement
- Log file: `target/surefire-reports/` of the stub run

> **Nếu bất kỳ test PASS:** Dừng lại. Xác định root cause từ bảng trên. Rewrite test với Props Isolation Pattern.

---

## 6. Entry / Exit Criteria

### Entry Criteria (Điều kiện bắt đầu)

- [ ] `STEELSIGHT-IAM-IMP-001` reviewed and approved
- [ ] Logic Issues (Section 2) confirmed with the Principal Architect (L4, L5 need a product decision)
- [ ] Migrations for any change approved
- [ ] Fixtures (TDS-05) available through `PageTestSupport` helpers

### Exit Criteria (Điều kiện kết thúc — DoD)

- [ ] `sh ./mvnw -B verify` — all tests green (needs Docker), no `@Disabled`
- [ ] GitHub Actions `test` job green on the branch
- [ ] Line coverage ≥ 80% for new files — **not measurable today: no JaCoCo in `pom.xml`**; add `jacoco-maven-plugin` or waive
- [ ] No raw password, hash or typed form value in logs (`RequestLoggingTest`)
- [ ] `TranslationsTest` green for any new message
- [ ] 🔴 TCs above either written and green, or explicitly deferred

**Exit Criteria bổ sung — CASE 2.0:**

- [ ] **Red Gate (§5.1)** — new TCs FAIL with a throwing stub before implementing
- [ ] **Contract Existence** — every import in test files resolves:
  ```bash
  sh ./mvnw -q -o test-compile 2>&1 | grep -E "cannot find symbol|package .* does not exist"
  # Expected: no output
  ```
- [ ] **Props Isolation** — integration test classes are `@Transactional` or clean up in `@AfterEach` (as `ServiceAtomicityTest` does):
  ```bash
  grep -L -E '@Transactional|@AfterEach' src/test/java/orochi/steelsight/*IntegrationTest.java
  # Review any file listed
  ```
- [ ] **Oracle Source** — every expected value cites a BR/ADR (this document's TC blocks)

### Suspension Criteria (Điều kiện tạm dừng)

- Docker unavailable (Testcontainers can't start) — only DB-free tests can run
- A permission-model change needing a new ADR (e.g. `ROLE_MANAGE`)
- CI broken by another change

---

## 7. Rollback Plan

```bash
# Dev only — the test database is a throwaway Testcontainer; nothing to reset.
# Local dev DB (port 55433): drop and recreate, Flyway rebuilds it on start
docker exec -it steelsight-db psql -U postgres -c 'DROP DATABASE steelsight_local;' -c 'CREATE DATABASE steelsight_local;'

# Revert implementation files
git checkout -- src/main/java/orochi/steelsight/identity src/main/java/orochi/steelsight/security

# Gap stays OPEN → keep its row in 02_design/MODULE-MAP.md §6
```

---

## 8. CASE 2.0 Anti-Pattern Detection (AI-Assisted TCs)

| AP-ID | Anti-Pattern | Dấu hiệu trong TDD spec | Check | Gate chặn |
|-------|-------------|--------------------------|-------|-----------|
| AP-AI-001 | Unconstrained Generation | TC không reference ADR/TDS constraint nào | ☑ none found — every block cites BR/ADR | G-0 |
| AP-AI-002 | Green-from-Birth | Test PASS với empty/throw stub (§5.1) | ☐ **not verified** for existing tests | G-2 ★ |
| AP-AI-003 | Implicit Decision | Test assume architecture decision không có ADR | ☑ IAM-TC-031 depends on a product decision — flagged | G-1 |
| AP-AI-004 | Layer Violation | Test verify controller có business logic | ☑ none: page tests check status/DB only | G-4 |
| AP-AI-005 | Hallucinated Contract | Test import service/type không tồn tại trong codebase | ☑ all referenced classes/methods exist (checked against `f005861`) | G-3 |

**Kết quả review:**

- [ ] Không phát hiện anti-pattern nào → TDD spec approved
- [x] Phát hiện AP → ghi vào bảng dưới → fix trước khi implement

| AP detected | TC ID | Mô tả | Fix action | Fixed? |
|------------|-------|-------|------------|--------|
| `AP-AI-002` (risk) | all 🟢 | Tests written alongside AI-generated code; never run against a stub | Run §5.1 on a sample (IAM-TC-012, 018, 023) | ☐ |
| `AP-AI-003` | `IAM-TC-031` | Encodes a behaviour no ADR yet requires | Confirm with product, then extend ADR-009 | ☐ |

---

*TDD Template v2.0 — Tích hợp CASE 2.0 Anti-Pattern Detection & Red Gate Protocol*
*Sections đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
