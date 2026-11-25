# Report 5 – Software Test Documentation

**Capstone Project Report** · Steelsight: PPE monitoring and attendance for construction sites · Hanoi, Nov 2026

This is the test documentation of release 1.0, written against the code after migration V26. Every
figure comes from the test runs of 25 November 2026: the web ERP's Maven suite with JaCoCo coverage and
two rounds of the detection app's pytest suite. The test case sheets are generated from the same runs by
`scripts/report5_test_cases.py`, so they can be rebuilt after any change.

## I. Record of Changes

| Date | A\*M, D | In charge | Change Description |
| --- | --- | --- | --- |
| | | | **Version 1.0.0** |
| 25/11/2026 | A | Claude Code (AI-assisted) | Create the report from the Report 5 template: scope, strategy, plan, the test case sheets generated from the real test results, and the test report of the runs of 25/11/2026. |
| 25/11/2026 | M | Claude Code (AI-assisted) | DEF-01 fixed by V27 (team leaders see and appeal their own violations); the ERP suite re-run after the fix, 402 of 402; the case sheets rebuilt from that run. |

\*A - Added M - Modified D - Deleted

## II. Testing Documentation

## 1. Scope of Testing

### 1.1 System Under Test

Steelsight has two applications that share one PostgreSQL database on Supabase (Report 4, section 1):

- **The web ERP**, a Spring Boot 4 application with server-rendered pages (Thymeleaf and htmx), used by
  the Worker, Team Leader, HSE Officer and Admin. It owns the schema (Flyway V1 to V27), the business
  rules, the scheduled jobs and the notices (web first, then Zalo).
- **The detection app**, a Windows desktop app in Python that reads the cameras, runs YOLO26 and
  InsightFace, and writes check-ins, check-outs and violations to the database through a local outbox.

The functional scope is the SRS of 23/11/2026 (Report 3): 14 workflows, 24 use cases, 41 functions and
76 business rules. For testing it is split into nine ERP modules and six detection modules:

| Module | Name | Main SRS items |
| --- | --- | --- |
| MF01 | Accounts and Access (IAM) | UC-01, UC-02; BR-01 to BR-12; sign-in, forced password change, accounts, face photos and consent |
| MF02 | Teams, Zones and Shifts (SCH) | UC-04 to UC-06, UC-22; BR-20 to BR-26; teams, zones and cameras, shift planning, timetable |
| MF03 | Attendance and Overtime (ATT) | UC-07, UC-14 to UC-17; BR-27 to BR-39; visits, late and early rules, paid hours, Incomplete, pending overtime |
| MF04 | PPE Violations and Safety Score (PPE) | UC-09 to UC-13, UC-19; BR-40 to BR-59; review queue, confirm or dismiss, appeal, score and bonus level |
| MF05 | Tickets (TKT) | UC-24; BR-60 to BR-69; the five categories, Admin handling, team leader and HSE access |
| MF06 | Notifications and Zalo (NTF) | UC-03, UC-21; BR-13 to BR-19; in-app notices, Zalo link code, delivery and retries, bot commands |
| MF07 | Reports and Month Closing (RPT) | UC-20; BR-70, BR-71; month lock, monthly .xlsx report, export log |
| MF08 | Photo Storage (STO) | Face, ticket and evidence photos in private storage; type and size checks |
| MF09 | Platform (PLT) | Error pages, logging, health check, translations, database rules, scheduled jobs |
| DET-VIS, DET-IDG, DET-SAF, DET-SYN, DET-REC, DET-WEB | Detection app | UC-07 to UC-09, UC-23; BR-27 to BR-29, BR-40 to BR-45, BR-72 to BR-76; cameras and detector, gate and identity, PPE rules and voice, outbox and sync, local records, desktop pages |

### 1.2 Features in Scope

- Every business rule the ERP enforces, at the service and page level, as each role.
- The rules that live in the database: triggers, constraints, row level security and the detection
  role's column grants (Report 4, section 2.3).
- Authentication, authorization and the scope of each role (own record, own team, all).
- Every scheduled job and its run log, the Zalo bot's webhook and commands (with the Zalo API mocked),
  the monthly .xlsx report read back cell by cell.
- The detection app's logic that runs without cameras: the gate decision, PPE timers and cooldown,
  identity bookkeeping, the outbox, the mapping to ERP rows, the desktop pages and their launch token.

### 1.3 Features out of Scope

- **Real devices and models:** detection accuracy on real cameras, the GPU and TensorRT engine, voice
  output. These depend on the site and are checked on rehearsal day (section 3.3).
- **External services:** the real Zalo Bot Platform, Supabase Storage in production, Render. Each is
  replaced by a mock server or local storage in the tests; only the ERP's side is asserted.
- **Non-functional testing:** load, stress, penetration testing and cross-browser testing.
- **Look and layout:** colours, spacing and the light and dark themes; pages are checked for content
  and behaviour, not pixels.
- **Out of the product:** pay calculation (done by accounting), vest numbers (CR-01), burst alerts (CR-02).

### 1.4 Test Levels Applied

| Level | In charge | Inputs / Time | Focus | Acceptance criteria |
| --- | --- | --- | --- | --- |
| Unit | Developer of the module | The module's Test-Spec in `04_testing/`; written with the code from 04/11/2026 | One class without Spring or a database: attendance rules, paid hours, sign-in limits, storage, the bot's text splitting, the API key filter | All unit cases pass |
| Integration | Developer of the module | Unit level green; Spring context with a disposable PostgreSQL 17 + pgvector (Testcontainers) and all migrations | Pages, services, repositories and database together, as each role, with the database state checked after each action | All integration cases pass on a fresh database |
| System (automated) | Developer | The SRS workflows (Report 3, 1.2) | A whole workflow across several roles over HTTP: gate check-in, violation and appeal, missing check-out, overtime, leave, month lock | All workflow tests pass |
| System (manual) | Whole team | A deployed build, the site laptop and cameras, a phone with Zalo | The workflows in a browser and on the real devices, which the automated levels can't reach | All manual cases pass; no Critical or Major defect open |
| Acceptance | Supervisor with the team | A build that passed the system level | Confirmation against the use cases from the user's side | The use cases accepted; no Critical or Major defect open |

### 1.5 Constraints and Assumptions

**Constraints**

- **C1:** A container runtime (Docker) is required for the integration and automated system levels; without it only the 80 unit cases run.
- **C2:** The test database is created fresh for each test run and migrated with the real Flyway scripts, so tests never depend on leftover data or run order.
- **C3:** External services are not available to the tests: the Zalo Bot API and Supabase Storage are mocked (`MockRestServiceServer`), photos go to a local folder, and the cameras and models are not used.
- **C4:** Time rules (late after 5 minutes, Incomplete at 23:00, closing after 4 hours, the 48-hour overtime window, the month lock) are tested with fixed times relative to now. One workflow test still depends on the wall clock across midnight (DEF-02).
- **C5:** The detection app's tests run on Linux with the CPU build of its libraries; the GPU path is not exercised.

**Assumptions**

- **A1:** All test data is synthetic; no real worker's name, face or phone number is used.
- **A2:** The SRS of 23/11/2026 is the reference. Where the team decided otherwise (several cameras per zone, automatic shift completion, the extra Zalo commands), the tests follow the decision; `04_testing/SRS-TRACEABILITY.md` lists those decisions.
- **A3:** Use case priority and defect severity are separate scales. Defects are Critical, Major, Minor or Trivial.

## 2. Test Strategy

### 2.1 Testing Types

| # | Testing type | Objective | Technique | Completion criteria |
| --- | --- | --- | --- | --- |
| 1 | Functional (business rule) | Each business rule gives the result the SRS prescribes: a check-in 5 minutes late is on time, 6 minutes is late; a violation counts only once confirmed; every ticket goes to the Admin | Equivalence partitioning and boundary values for times and limits; decision tables for rules with several conditions (paid hours, appeal eligibility); state transitions for violations, tickets, shifts and pending overtime | Every rule in the traceability table has a passing test |
| 2 | Negative and exception | Invalid input, forbidden transitions and stale forms are refused with the right message, and change nothing | Negation of each rule; stale versions; tampered form values; messages asserted as the user sees them | Every refusal path has a test that checks the message and the unchanged data |
| 3 | Data and persistence integrity | The database stays valid: constraints, triggers, transactions, optimistic locking, and the detection role's limits | Tests on a real PostgreSQL with all migrations; the SQL script `schema_checks.sql` run as owner and as `steelsight_detection`; forced failures to check rollback | All database rules and grants checked after V26 |
| 4 | Security and authorization | Each role reaches only its own pages, records and actions; sign-in limits, CSRF, escaping, private photos, the webhook secret and the API key hold | A role × page matrix as each seeded account; requests for other people's records; requests without a CSRF token or with a wrong secret | No protected page or action is reachable by a role that shouldn't |
| 5 | Regression | A change doesn't break what already passed | The whole automated suite on every push and pull request (GitHub Actions), and before each merge | The full suite passes before merge |
| 6 | Manual system and acceptance | The workflows work on the real devices and in a browser | The scenarios of sheet "System manual", following the SRS workflows | All manual cases pass |

### 2.2 Test Levels

| Type of Tests | Unit | Integration | System | Acceptance |
| --- | --- | --- | --- | --- |
| Functional (business rule) | X | X | X | X |
| Negative and exception | X | X | X | |
| Data and persistence integrity | | X | X | |
| Security and authorization | X | X | X | |
| Regression | X | X | X | |
| Manual system and acceptance | | | X | X |

### 2.3 Supporting Tools

| Purpose | Tool | Vendor/In-house | Version |
| --- | --- | --- | --- |
| Build and test run (ERP) | Apache Maven through the Maven Wrapper, Surefire | Apache Software Foundation | 3.10.0, Surefire 3.5.4 |
| Unit test framework and assertions | JUnit Jupiter, AssertJ, Hamcrest, Mockito | Open source | 6.0.1, 3.27.6, 3.0, 5.20.0 |
| Page and HTTP testing | Spring MockMvc (spring-boot-starter-test) | Spring | Spring Boot 4.0.0 |
| Security test support | spring-security-test | Spring | 7.0.0 |
| Disposable database | Testcontainers with `pgvector/pgvector:pg17` | AtomicJar / Docker | 2.0.2 |
| Database rule checks | `src/test/resources/db/schema_checks.sql`, run by `SchemaChecksSqlTest` | In-house | V1 to V27 |
| Mock HTTP servers (Zalo Bot API, Supabase Storage) | Spring `MockRestServiceServer`, Mockito `@MockitoBean` | Spring / Mockito | Spring Framework 7.0.1 / 5.20.0 |
| Excel report checks | fastexcel-reader | dhatim | 0.18.4 |
| Code coverage | JaCoCo, attached from the command line | EclEmma | 0.8.13 |
| Detection app tests | pytest | Open source | 9.2.0 |
| Detection integration check | `tests/erp_integration_check.py` against the ERP's real migrations | In-house | V1 to V26 |
| Test case sheets | `scripts/report5_test_cases.py` (openpyxl) | In-house | current |
| Continuous integration | GitHub Actions (`mvn verify` on every push and pull request) | GitHub | SaaS |
| Version control and review | Git, GitHub (GitLab planned), CodeRabbit reviews | GitHub, CodeRabbit | SaaS |

## 3. Test Plan

### 3.1 Human Resources

The team members are those named in Reports 3 and 4; their testing roles are for the team to confirm.

| Worker/Doer | Role | Specific Responsibilities/Comments |
| --- | --- | --- |
| SonLQT | Developer (to confirm) | Modules and test responsibilities to be filled in by the team |
| AnNV | Developer (to confirm) | Modules and test responsibilities to be filled in by the team |
| DucPM | Developer (to confirm) | Modules and test responsibilities to be filled in by the team |
| Claude Code (AI assistant) | Test authoring and execution support | Wrote and ran automated tests with the developers, generated the case sheets and this report from the test results |

### 3.2 Test Environment

| Purpose | Tool | Provider | Version |
| --- | --- | --- | --- |
| Developer workstation | Windows 11 with WSL2 (Ubuntu 24.04.1 LTS) | Team-owned hardware | - |
| Runtime of the ERP under test | OpenJDK, Spring Boot | Ubuntu / Spring | 17.0.21.1 / 4.0.0 |
| Container runtime | Docker Engine through Docker Desktop | Docker Inc. | 29.5.3 |
| Test database | PostgreSQL 17 with pgvector and btree_gist, one fresh container per run | Testcontainers / pgvector | pg17 |
| Test data | Flyway V1 to V27 (schema and seeded roles, permissions and categories) plus accounts each test creates; synthetic only | In-house | V26 |
| Detection app test runtime | Python with the CPU builds of NumPy, OpenCV, FastAPI, SQLAlchemy, InsightFace and ONNX Runtime | Python Software Foundation | 3.12.3 |
| Manual system testing (planned) | The deployed ERP on Render, Supabase, the site laptop (Windows, NVIDIA GPU) with CAM-01 to CAM-04, a phone with Zalo | Render, Supabase, team hardware | - |
| Source of truth for test artifacts | Git repository, branch `feature/erp-backend` | GitHub (GitLab planned) | - |

### 3.3 Test Milestones

The full-suite runs below come from the AI usage log (`chatlog/ai-log.md`).

| Milestone | Task | Start Date | End Date | Status |
| --- | --- | --- | --- | --- |
| M1 | Test infrastructure: Testcontainers database with the real migrations; first entity, rule and service tests (143 passing) | 02/11/2026 | 04/11/2026 | Completed |
| M2 | Page tests as each role, shift grid, accounts and report tests (192, then 244 passing) | 05/11/2026 | 06/11/2026 | Completed |
| M3 | Module Test-Specs written in `04_testing/` (IAM, SCH, ATT, PPE, TKT, NTF, RPT, STO, PLT) | 06/11/2026 | 06/11/2026 | Completed |
| M4 | Security hardening and attendance audit tests (266 passing) | 09/11/2026 | 12/11/2026 | Completed |
| M5 | Error handling and face enrolment tests (282, then 284 passing) | 16/11/2026 | 17/11/2026 | Completed |
| M6 | Detection app Test-Specs and pytest suite | 09/11/2026 | 17/11/2026 | Completed |
| M7 | Tests for the SRS of 23/11: one test per business rule, change request and workflow, and `SRS-TRACEABILITY.md` (381 passing) | 21/11/2026 | 23/11/2026 | Completed |
| M8 | Zalo notices and bot commands (402 passing) | 24/11/2026 | 24/11/2026 | Completed |
| M9 | Round 1 of this report: V26 regression with JaCoCo coverage (402 of 402), detection round 1 (166 of 167) | 25/11/2026 | 25/11/2026 | Completed |
| M10 | Round 2 of this report: detection re-run (167 of 167) | 25/11/2026 | 25/11/2026 | Completed |
| M11 | DEF-01 fixed (V27) and the ERP suite re-run: 402 of 402 | 25/11/2026 | 25/11/2026 | Completed |
| M12 | Manual system testing on the deployed build and the site devices (sheet "System manual") | To be scheduled | | Not started |
| M13 | Acceptance testing with the supervisor | To be scheduled | | Not started |

## 4. Test Cases

The test cases are in two workbooks in `04_testing/`, built from the test results by
`scripts/report5_test_cases.py`. Each case names the test (class and method) that runs it, so a case
can't drift from the code without the next run showing it.

- **Unit Test Cases – `Report5_Unit_Test.xlsx`:** a summary sheet and one sheet per module, 80 cases.
  Each case has its UTCID, the condition and confirmation (taken from the test name), the SRS rule, the
  test, the type N/A/B, the result, the date and the defect ID.
- **Other Test Cases – `Report5_Test_Report.xlsx`:**
  - one sheet per module for the integration level, 315 cases: case ID, description, SRS rule, testing
    type, procedure (the automated test), pre-conditions, expected result, round result, date, tester
    and defect ID;
  - "System automated": the 7 SRS workflow tests;
  - "System manual": 15 scenarios for the deployed build and the site devices, not run yet (M12);
  - "Detection app": the 167 pytest cases with their round 1 and round 2 results.

The module Test-Specs in `04_testing/STEELSIGHT-*-TDD-001_Test-Spec.md` give the test design behind
these cases (conditions, techniques, test data), and `04_testing/SRS-TRACEABILITY.md` maps every
business rule, change request and workflow to its tests. How the sheets classify the cases is described
in the generator script: the module comes from the test class, or from the method name for classes that
test several features; the level from whether a Spring context or a database starts; the testing type
and N/A/B from words in the test name.

## 5. Test Reports

The ERP figures are from the full run after V27 on 25/11/2026 (402 of 402); the coverage is from the
run just before it, which also passed 402 of 402. The detection figures are from its two rounds the same day.

### 5.1 Unit Test Results

The unit level runs without Spring or a database. All 80 cases passed on 25/11/2026.

| Module | Cases | Passed | Failed | Pass rate |
| --- | --- | --- | --- | --- |
| MF01 - Accounts and Access | 9 | 9 | 0 | 100% |
| MF03 - Attendance and Overtime | 34 | 34 | 0 | 100% |
| MF06 - Notifications and Zalo | 6 | 6 | 0 | 100% |
| MF08 - Photo Storage | 20 | 20 | 0 | 100% |
| MF09 - Platform | 11 | 11 | 0 | 100% |
| **TOTAL** | **80** | **80** | **0** | **100%** |

MF02, MF04, MF05 and MF07 have no unit cases: their rules involve the database (triggers, permission
functions, `safety_scores()`), so they are tested at the integration level.

**Test case distribution by type**

| Type | Count | Percentage | Description |
| --- | --- | --- | --- |
| Normal (N) | 29 | 36.3% | Valid input; the rule gives the prescribed result |
| Abnormal (A) | 23 | 28.8% | Invalid input, refusals, failures of an external service |
| Boundary (B) | 28 | 35.0% | Edge values: 5 and 6 minutes late, the 20-minute buffer, the 10-minute visit, the 2,000-character Zalo limit, the upload ceiling, the sign-in and API call limits |
| **Total** | **80** | **100%** | |

| Module | Cases | Normal | Abnormal | Boundary |
| --- | --- | --- | --- | --- |
| MF01 - Accounts and Access | 9 | 3 | 4 | 2 |
| MF03 - Attendance and Overtime | 34 | 12 | 4 | 18 |
| MF06 - Notifications and Zalo | 6 | 3 | 1 | 2 |
| MF08 - Photo Storage | 20 | 8 | 11 | 1 |
| MF09 - Platform | 11 | 3 | 3 | 5 |
| **TOTAL** | **80** | **29** | **23** | **28** |

### 5.2 Integration Test Results

The integration level runs the pages, services, repositories and database together on a fresh
PostgreSQL 17 with all migrations, as each role. All 315 cases passed on 25/11/2026.

| Module | Cases | Passed | Failed | Pass rate |
| --- | --- | --- | --- | --- |
| MF01 - Accounts and Access | 55 | 55 | 0 | 100% |
| MF02 - Teams, Zones and Shifts | 53 | 53 | 0 | 100% |
| MF03 - Attendance and Overtime | 30 | 30 | 0 | 100% |
| MF04 - PPE Violations and Safety Score | 43 | 43 | 0 | 100% |
| MF05 - Tickets | 61 | 61 | 0 | 100% |
| MF06 - Notifications and Zalo | 34 | 34 | 0 | 100% |
| MF07 - Reports and Month Closing | 7 | 7 | 0 | 100% |
| MF08 - Photo Storage | 2 | 2 | 0 | 100% |
| MF09 - Platform | 30 | 30 | 0 | 100% |
| **TOTAL** | **315** | **315** | **0** | **100%** |

**Distribution by testing type**

| Testing type | Cases | Percentage | What it verifies |
| --- | --- | --- | --- |
| Functional (business rule) | 168 | 53.3% | Business rules end to end through the pages and services, with the database state after each action |
| Security and authorization | 101 | 32.1% | Each role's pages, records and actions; other people's records; CSRF, escaping, sign-in limits, private photos |
| Data and persistence integrity | 46 | 14.6% | Constraints, triggers, transactions, stale edits, the detection role's grants and row level security |
| **Total** | **315** | **100%** | |

### 5.3 System Test Results

**Automated workflows.** Seven tests drive SRS workflows across several roles over HTTP on a real
database. All 7 passed on 25/11/2026.

| Workflow (Report 3) | Module | Result |
| --- | --- | --- |
| 1.2.4 Gate check-in attached to the shift and shown on My Attendance | MF03 | Passed |
| 1.2.6 and 1.2.7 Violation detected, HSE alerted, confirmed, the worker sees the score | MF04 | Passed |
| 1.2.8 Appeal decided by the Admin with the HSE facts | MF04 | Passed |
| 1.2.10 Missing check-out flagged, explained and approved | MF03 | Passed |
| 1.2.11 Pending overtime listed and the overtime shift form opened | MF03 | Passed |
| 1.2.12 Leave request sent, read by the leader and approved | MF05 | Passed |
| 1.2.13 Month locked and the report final | MF07 | Passed |

**Manual system tests.** The 15 scenarios of sheet "System manual" cover what the automated levels
can't: the real cameras and models, the real Zalo bot on a phone, the deployed site, and every page in
both languages on a phone. They have **not been run yet** (M12).

### 5.4 Detection App Test Results

The detection app's pytest suite ran twice on 25/11/2026, on Linux with the CPU builds of its libraries.

| Module | Cases | Round 1 passed | Round 2 passed | Pass rate (round 2) |
| --- | --- | --- | --- | --- |
| DET-VIS - Cameras, detector, tracker | 15 | 15 | 15 | 100% |
| DET-IDG - Identity and gate | 71 | 70 | 71 | 100% |
| DET-SAF - PPE rules and voice | 19 | 19 | 19 | 100% |
| DET-SYN - Outbox and sync | 45 | 45 | 45 | 100% |
| DET-REC - Local records and reports | 8 | 8 | 8 | 100% |
| DET-WEB - Desktop pages | 9 | 9 | 9 | 100% |
| **TOTAL** | **167** | **166** | **167** | **100%** |

The one round 1 failure is a flaky test in the vest-number OCR code, which the app no longer uses (DEF-04).
The detection app's integration check (`tests/erp_integration_check.py`) also ran against the ERP's
migrations V1 to V26 as the `steelsight_detection` role: 22 of 22 checks passed.

### 5.5 Code Coverage

JaCoCo measured the ERP during the full run before V27 (V27 changes only role permissions, no Java code). Overall: **89.2% of lines (3,440 of 3,856) and 70.6% of
branches.**

| Package | Module | Line coverage | Branch coverage |
| --- | --- | --- | --- |
| `security` | MF01 | 98.1% | 85.5% |
| `identity` | MF01 | 87.8% | 71.5% |
| `scheduling` | MF02 | 94.0% | 80.4% |
| `attendance` | MF03 | 92.6% | 77.0% |
| `ppe` | MF04 | 95.5% | 76.6% |
| `ticketing` | MF05 | 92.1% | 69.2% |
| `notification` | MF06 | 82.1% | 64.2% |
| `reporting` | MF07 | 93.5% | 81.2% |
| `storage` | MF08 | 87.4% | 73.8% |
| `web` | All (page controllers) | 89.5% | 66.4% |
| `jobs` | MF09 | 60.6% | 25.0% |
| `calendar` | MF09 | 68.3% | 55.0% |
| others (`config`, `model`, `exception`, `validation`, `repository`) | MF09 | 94.3% | 78.1% |
| **TOTAL** | | **89.2%** | **70.6%** |

The detection app's coverage was not measured.

### 5.6 Key Test Analysis and Observations

- **Coverage follows the risk.** Security and authorization make up 101 of the 315 integration cases
  (32.1%). Every role is tested against the pages it may and may not open, including other people's
  records and tampered form values. The database rules that must hold while the ERP sleeps (triggers,
  grants, row level security) are checked by `schema_checks.sql`, once as the owner and once as the
  detection role.
- **The SRS is traced to tests.** 65 distinct business rules, messages, change requests and workflows
  are named in test method names (`br31_…`, `msg22_…`, `cr10_…`, `workflow124_…`), and
  `SRS-TRACEABILITY.md` maps every rule to its tests. Rules only the detection app can enforce are
  tested in its own suite.
- **Weak spots.** The scheduled jobs (`jobs`, 60.6% of lines, 25.0% of branches) and the working-day
  calendar (`calendar`, 68.3%) have the lowest coverage: the month-end and catch-up paths after Render
  sleeps run only for some dates. These deserve tests with an injectable clock.
- **An SRS gap was encoded in a test, and is now fixed.** `WorkerAccessPagesTest` asserted that a
  team leader can't open violation pages, the design before the SRS of 23/11, while the SRS (3.1.3)
  gives the team leader "My Violations and Safety Score" (DEF-01). V27 grants it, and the test was
  replaced by `aTeamLeaderSeesAndAppealsTheirOwnViolationsButNoOneElses`, which also checks that a
  team leader still can't see anyone else's violation or the review pages.
- **Limitations.** The manual system tests and acceptance haven't been run, so nothing here proves the
  system works with the real cameras, the real Zalo bot or the deployed site. All automated cases ran
  in one round each on 25/11 (the ERP suite has run in full on every working day since 04/11, but only
  this run is recorded case by case). The N/A/B types and the testing types are derived from test names,
  not chosen by hand.

### 5.7 Defect Report

Defects found by the tests, by code review or by checking the code against the SRS, from 23/11 to 25/11.

| Defect ID | Test case | Module | Severity | Status | Description |
| --- | --- | --- | --- | --- | --- |
| DEF-01 | `WorkerAccessPagesTest.aTeamLeaderSeesAndAppealsTheirOwnViolationsButNoOneElses` | MF04 | Major | Closed | The MANAGER role lacked `VIOLATION_READ_OWN`, so a team leader couldn't see their own violations or appeal, though they have a safety score and the SRS (3.1.3) allows it; a test asserted the old behaviour. Fixed on 25/11 by V27, with the test rewritten to the SRS behaviour |
| DEF-02 | `SrsWorkflowTest.workflow124_…` | MF03 | Minor | Open | The test uses the wall clock: run across midnight, its check-in "3 hours ago" falls on the previous day and Team Attendance shows the new one (seen on 24/11 at 23:58 and 00:05). Test defect, not a product defect |
| DEF-03 | `ZaloNoticesTest.aGateCheckInTellsTheEmployeeTheTimeAndTheShift` | MF06 | Major | Closed | Check-in and check-out notices were never created for gate scans. Fixed on 23/11 with V23 (database triggers) and `CheckOutHours` |
| DEF-04 | `test_vest_ocr.test_debug_mode_shows_every_candidate_and_keeps_only_recent_crops` | DET-IDG | Minor | Open | Flaky: five debug crops saved within the same millisecond get the same file name, so fewer files are kept (failed round 1, passed round 2). The vest OCR code is no longer used (CR-01); the test can go with it |
| DEF-05 | `ZaloNoticesTest.aCheckOutGoesToZaloOnlyOnceItsHoursAreFilled` | MF06 | Major | Closed | A check-out notice could be sent to Zalo before its paid hours were worked out (found by CodeRabbit review). Fixed on 24/11: the dispatcher holds it back until the hours are filled |
| DEF-06 | `ZaloNoticesTest.aLongMonthComesInSeveralMessagesWithTheTotalsFirst` | MF06 | Minor | Closed | A long `/chamcong` reply was cut off by Zalo's length limit (found by CodeRabbit review). Fixed on 24/11: split at line ends into messages of at most 2,000 characters, totals first |
| DEF-07 | `SrsViolationReviewTest.aNewZoneGetsTheNextFreeCodeOfItsKind` | MF02 | Trivial | Closed | The new test assumed only work areas hold AREA codes, but other tests change a zone's kind after adding it. Test defect, fixed on 25/11 |

### 5.8 Conclusion and Recommendations

**Result.** On 25/11/2026, all 402 automated ERP cases passed: 80 of 80 at unit level, 315 of 315 at
integration level and 7 of 7 automated system workflows, with 89.2% line and 70.6% branch coverage. The
detection app's 167 cases passed in round 2 (166 in round 1, because of one flaky test in unused code).
DEF-01 (team leaders couldn't see their own violations) was fixed the same day by V27, and the suite
passed 402 of 402 again after the fix. Two Minor defects are open: DEF-02 (a clock-dependent test) and
DEF-04 (a flaky test in unused detection code). The manual system tests and acceptance have not been run.

**Recommendations, in order of priority:**

1. Run the 15 manual system scenarios (M12) on the deployed build with the real cameras and Zalo bot,
   recording each result and defect in sheet "System manual", then hold the acceptance session (M13).
2. Give the scheduled jobs and the calendar an injectable clock, test the month-end and catch-up paths,
   and fix DEF-02 the same way.
3. Remove the unused vest OCR code and its tests from the detection app (DEF-04), and measure the
   detection app's coverage with `pytest --cov`.
4. Add JaCoCo to `pom.xml` with a coverage gate in CI (for example 85% of lines), so coverage is
   reported on every run instead of from the command line.
5. Update the Test-Specs that predate the SRS of 23/11 (the ATT, SCH, PPE, TKT, NTF and RPT ones and the
   detection `STEELSIGHT-DET-*` ones), which still describe vest numbers, burst alerts and the old ticket
   routing.
