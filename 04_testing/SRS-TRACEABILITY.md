# SRS traceability: web ERP

Maps each business rule (BR), change request (CR) and main workflow of
`Report3_Software_Requirement_Specification.docx` (revision 23/11/2026) to the tests that check it.
All tests are under `src/test/java/orochi/steelsight/`. Test methods named `brNN_…`, `msgNN_…`,
`crNN_…` or `workflowNNN_…` check that code directly. Run everything with `./mvnw test` (Docker needed
for the database tests).

**Status (23/11/2026):** 380 tests, all passing.

**Team decision that changes the SRS:** a monitoring zone may have **several cameras** and a work
zone **belongs to one team**. HSE picks a violator from that team first; the gate keeps the CAM-01
suggestion (BR-46). The SRS (1.1.3, 1.4) still says one camera per zone and needs updating.

**Desktop app:** rules that only the desktop/detection app can enforce are marked *Desktop*. This
repo can't test them; see `docs/detection-app-contract.md`.

## Business rules

| Rule | What | Where it is tested |
|---|---|---|
| BR-01 | Same error for every failed login | `LoginIntegrationTest` |
| BR-02 | Password 8 chars to 72 bytes, BCrypt | `ServiceIntegrationTest.createChecksWhatTheAdminTyped`, `AccountSecurityTest` |
| BR-03 | Forced password change | `AccountPagesTest.someoneGivenAPasswordByAnAdminMustChooseTheirOwnFirst` |
| BR-04 | Login lock 5/20 in 15 min | `SignInLimiterTest`, `LoginIntegrationTest` |
| BR-05 | 30-min session, CSRF | `ErrorPagesTest`, `PagesIntegrationTest.formsWithoutACsrfTokenAreRefused` |
| BR-06 | Code 3–20 `A-Z0-9-` upper case; phone 10 digits unique; one group | `ServiceIntegrationTest.createChecksWhatTheAdminTyped`, `…createQueuesTheFacePhoto…`, `ManagerPagesIntegrationTest.aPhoneNumber…` |
| BR-07 | Deleted account: no login, face erased, Zalo unlinked, future shifts cancelled | `ZaloNoticesTest.br07_…`, `FacePhotoCleanupTest` |
| BR-08 | Own data only; leader reads team | `WorkerAccessPagesTest`, `HsePagesIntegrationTest.theTeamLeaderReads…` |
| BR-09 | FRONT required; JPEG/PNG ≤ 5 MB ≥ 480×480 | `AccountPagesTest.uploadingNothingOrAFakeImageIsRefused` |
| BR-10, BR-11 | Consent tick required | `AccountPagesTest` (creation needs `consent`) |
| BR-12 | Duplicate face | *Desktop* (face matching in the enrolment queue) |
| BR-13 | 6-digit code, 24 h, single use, new replaces old | `ZaloNoticesTest.br13_…`, `msg21_…`, `msg22_…` |
| BR-14 | One chat per active account | `ZaloNoticesTest.br14_…` |
| BR-15 | In-app first | `ZaloNoticesTest.br15_…` |
| BR-16 | Retries 1/5/15 min then FAILED | `ZaloNoticesTest.br16_…` |
| BR-17 | Cut at 1,900 with link | `ZaloNoticesTest.br17_…` |
| BR-18 | Saturday 18:00 weekly schedule | `SrsShiftsAndJobsTest.br18_…` |
| BR-19 | Change notice for current/next week | `SrsShiftsAndJobsTest.br19_…` |
| BR-20 | One leader per team | `TeamsTicketsAndReportTest`, `ServiceIntegrationTest` |
| BR-21 | 30 min–12 h, same day | `SrsShiftsAndJobsTest.br21_…`, `ManagerPagesIntegrationTest.badShiftForms…` |
| BR-22 | No overlap | `SrsShiftsAndJobsTest.br22_…`, `ShiftOverlapPageTest` |
| BR-23 | Leader: own team, regular, before Saturday 18:00; overtime Admin only with reason | `SrsShiftsAndJobsTest.br23_…` (2) |
| BR-24 | Started shift locked, pending-OT exception | `SrsShiftsAndJobsTest.br24_…`, `br37_…` |
| BR-25 | Vietnam time, shift belongs to its start day | `ManagerPagesIntegrationTest.newShift…TakesSiteLocalTime`, `SafetyScoreTest.br57_…Resets…` |
| BR-26 | HSE duty = HSE shift | `SrsViolationReviewTest.br54_…` |
| BR-27–BR-29 | Only CAM-01, face confirmation, IN/OUT decision | *Desktop*: `steelsight-demo` `test_scanner.py`, `test_gate.py`, `test_srs_desktop.py` (2 matches 1 s apart, 5-minute rescan) |
| BR-30 | Attach within 60 min before start | `schema_checks.sql`, `srs/ShiftAttendanceTest.br30_…` |
| BR-31 | 5-min grace, no make-up | `srs/ShiftAttendanceTest.br31_…`, `AttendanceRulesTest`, `ManagerPagesIntegrationTest.attendanceTags…` |
| BR-32 | Early leave | `srs/ShiftAttendanceTest.br32_…`, `AttendanceRulesTest` |
| BR-33 | Paid hours, 10-min visits ignored, Absent | `srs/ShiftAttendanceTest.br33_…` (6) |
| BR-34 | 20-min buffer, pending OT | `srs/ShiftAttendanceTest.br34_…`, `SrsShiftsAndJobsTest.br34_…` |
| BR-35 | Incomplete at 23:00, closed at +4 h, no guessed time | `AutoCloseCheckInTest`, `SrsShiftsAndJobsTest.br35_…` |
| BR-36 | Half-day setting | `srs/ShiftAttendanceTest.br36_…` (2) |
| BR-37 | Pending OT 48 h / 2nd of month | `SrsShiftsAndJobsTest.br37_…`, `srs/ShiftAttendanceTest.br37_…` |
| BR-38 | Valid OT = overlap | `srs/ShiftAttendanceTest.br38_…` |
| BR-39 | Manual entry: reason 10–500, history | `ServiceIntegrationTest.aCorrectionKeeps…`, `HsePagesIntegrationTest.anOfficerCanAddAndCorrectACheckIn` |
| BR-40–BR-45 | PPE detection, timers, cooldown, combined type | *Desktop*: `steelsight-demo` `test_safety.py`, `test_erp_mapping.py`; ERP accepts `NO_HARD_HAT_AND_VEST` (`SrsViolationReviewTest`) |
| BR-46 | Gate suggestion ±30 s | `SrsViolationReviewTest.br46_…` (3) |
| BR-47 | Zone suggestion is never a decision | `SrsViolationReviewTest.br47_…` |
| BR-48 | PENDING_REVIEW, HSE confirms | `SrsViolationReviewTest.br48_…`, `RepositoryIntegrationTest`, `schema_checks.sql` |
| BR-49 | Person list order (+ zone team) | `SrsViolationReviewTest.br49_…` |
| BR-50 | Dismiss reasons, Other 10–500 | `SrsViolationReviewTest.br50_…` (2) |
| BR-51 | Office = reminder | `SrsViolationReviewTest.br51_…`, `ServiceIntegrationTest.anOfficeEmployees…` |
| BR-52 | Stranger | `SrsViolationReviewTest.br52_…`, `SafetyScoreTest.br52_…` |
| BR-53 | OVERDUE at 23:00, still decidable | `SrsViolationReviewTest.br53_…` |
| BR-54 | Alert to HSE on duty, else HSE + Admin | `SrsViolationReviewTest.br54_…` (2), `schema_checks.sql` |
| BR-55 | Alert text with zone name | `SrsViolationReviewTest.br55_…` |
| BR-56–BR-58 | Score 100 − 5N, Site group, levels 0–3 | `SafetyScoreTest` |
| BR-59 | Score notice, worse level said | `SrsViolationReviewTest.br59_…`, `WorkerAccessPagesTest.aWorkerWhoseLevelGetsWorse…` |
| BR-60 | Five categories | `TeamsTicketsAndReportTest.theFiveSrsCategories…`, `PagesIntegrationTest.newTicketForm…` |
| BR-61 | Admin handles all; no self-decision; leader comments | `SrsTicketRulesTest.br61_…`, `HsePagesIntegrationTest` |
| BR-62 | Status, cancel, decision notice | `SrsTicketRulesTest.br62_…` (2) |
| BR-63 | Working days Mon–Sat | `SrsTicketRulesTest.br69_…` |
| BR-64 | Appeal conditions | `SrsTicketRulesTest.br64_…`, `msg38_…`, `msg39_…` |
| BR-65 | Appeal decision, right person | `SrsTicketRulesTest.br65_…` (3) |
| BR-66 | Attendance error | `SrsTicketRulesTest.msg41_…`, `msg42_…`, `br66_…` |
| BR-67 | Missing check-out explanation | `SrsTicketRulesTest.br67_…`, `msg42_…`, `SrsWorkflowTest.workflow1210_…` |
| BR-68 | Leave request | `SrsTicketRulesTest.msg43_…`, `msg44_…`, `br68_…` |
| BR-69 | Age in working days; month-end list | `SrsTicketRulesTest.br69_…`, `SrsShiftsAndJobsTest` |
| BR-70 | Month lock freezes scores, blocks changes | `SafetyScoreTest.br70_…`, `SrsShiftsAndJobsTest.br70_…`, `SrsViolationReviewTest.msg33_…` |
| BR-71 | Report sheets, provisional, export log | `TeamsTicketsAndReportTest` |
| BR-72–BR-76 | Desktop texts, login, offline queue, camera offline, evidence retention | *Desktop*: `steelsight-demo` `test_srs_desktop.py`, `test_outbox.py`, `test_sync.py`; Zalo texts in Vietnamese: `ZaloNoticesTest` |

## Change requests (SRS 5.5)

| CR | Status in the ERP |
|---|---|
| CR-01 Remove vest numbers | ERP no longer uses them; the detection app stopped writing them (23/11/2026). The tables can be dropped in a later migration |
| CR-02 Remove burst alert | Done: the API stores and notifies nobody (`DetectionAlertApiTest`); the detection app no longer calls it |
| CR-03 Zones with kind and name | Done, plus owning team (`SrsViolationReviewTest.cr03_…`) |
| CR-04 CAM-02 in/out, any time | *Desktop*: PPE rules run on every camera at any time |
| CR-05 Review statuses, dismiss, stranger | Done |
| CR-06 `NO_HARD_HAT_AND_VEST` | Done in both; `steelsight-demo` `erp_integration_check.py` checks one row against V1–V22 |
| CR-07 Score and levels, `monthly_score` | Done |
| CR-08 Site/Office group, reminders | Done |
| CR-09 Every ticket to the Admin | Done |
| CR-10 Zalo bot: link code and QR code, webhook, delivery table, retries | Done (`ZaloNoticesTest.cr10_…`, `QrCodesTest`); not yet tried against the real Zalo service |
| CR-11 Alert routing by duty | Done |
| CR-12 Scheduled jobs with run log and catch-up | Done (`job_run`, `SrsShiftsAndJobsTest`) |
| CR-13 Uptime monitor | Deployment: set up outside the code |
| CR-14 FRONT photo and consent | Done in the ERP; the detection app must not enrol without READY FRONT |
| CR-15 My/Team Attendance | Done |
| CR-16 Regular/Overtime shifts, notices | Done |
| CR-17 Attendance rules | Done in both; the detection app attaches within 60 min, like the ERP trigger |
| CR-18 Incomplete flag, 4 h close, explanation, half day | Done |
| CR-19 Pending overtime | Done |
| CR-20 Leave requests | Done |
| CR-21 Month lock, report, export log | Done |
| CR-22 Clock on gate screen | *Desktop*: shown on `/gate` |

## Team decisions that differ from the SRS

| Topic | SRS | Code | Test |
|---|---|---|---|
| Shift COMPLETED | 3.2.10 #10: a person clicks "Mark completed" after the shift ends | Automatic: the hourly and 23:00 jobs mark every ended shift COMPLETED, except in a locked month (BR-70); no manual button (team decision 23/11/2026) | `SrsShiftsAndJobsTest.endedShiftsAreCompletedAutomatically`, `ManagerPagesIntegrationTest.aFinishedShiftIsCompletedByTheSystem…` |
| Zones | 1.4: one camera per zone | A zone has several cameras and belongs to one team | `SrsViolationReviewTest` |

## Workflows (SRS 1.2)

| Workflow | Tests |
|---|---|
| 1.2.1 Log in, change password | `LoginIntegrationTest`, `AccountPagesTest` |
| 1.2.2 Onboarding, consent, face, Zalo | `AccountPagesTest`, `ZaloNoticesTest` |
| 1.2.3 Teams and weekly scheduling | `SrsShiftsAndJobsTest`, `ManagerPagesIntegrationTest` |
| 1.2.4 Gate check-in/out | `SrsWorkflowTest.workflow124_…`, `AutoCloseCheckInTest` |
| 1.2.5 Gate PPE check | `SrsViolationReviewTest.br46_…` (ERP side) |
| 1.2.6 Work-zone monitoring and alert | `SrsWorkflowTest.workflow126_127_…`, `SrsViolationReviewTest.br54_…` |
| 1.2.7 Review and confirmation | `SrsWorkflowTest.workflow126_127_…`, `SrsViolationReviewTest` |
| 1.2.8 Appeal | `SrsWorkflowTest.workflow128_…`, `SrsTicketRulesTest` |
| 1.2.9 Attendance error | `SrsTicketRulesTest.br66_…` |
| 1.2.10 Missing check-out | `SrsWorkflowTest.workflow1210_…` |
| 1.2.11 Pending overtime | `SrsWorkflowTest.workflow1211_…`, `SrsShiftsAndJobsTest.br37_…` |
| 1.2.12 Leave | `SrsWorkflowTest.workflow1212_…` |
| 1.2.13 Score, bonus, report | `SrsWorkflowTest.workflow1213_…`, `SafetyScoreTest`, `TeamsTicketsAndReportTest` |
| 1.2.14 Offline queue and notifications | Notifications: `ZaloNoticesTest`; event-ID retries: `schema_checks.sql`; offline queue: *Desktop* |
