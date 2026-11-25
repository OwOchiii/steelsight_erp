# Steelsight: Detailed Design

This is section 3 of the design (Report 4). It lists the features of the web ERP and the detection app,
with the classes and operations that carry each one. The class diagrams, sequence diagrams and state
machines are PlantUML in the technical design specifications (TDS) of `03_implement/`, one per module.
Each feature below points to the TDS that draws it.

Some TDS diagrams predate the SRS of 23 November 2026 and migrations V21 to V26. Where that is the
case, the feature says what changed; this document follows the code.

| TDS | Module | Diagrams | Up to date? |
| --- | --- | --- | --- |
| [IAM](../03_implement/STEELSIGHT-IAM-IMP-001_TDS.md) | Identity and Access | Class, sign-in sequences, account state machine | Yes |
| [SCH](../03_implement/STEELSIGHT-SCH-IMP-001_TDS.md) | Scheduling | Class, plan-a-shift sequences, shift state machine | Partly: `Team.lateAfterMinutes` is gone (V26); `Zone` now has `code`, `kind` and `team`; shifts have `kind` and `reason` |
| [ATT](../03_implement/STEELSIGHT-ATT-IMP-001_TDS.md) | Attendance | Class, check-in sequences, visit and enrolment state machines | Partly: `CheckIn.ppeLinking` is gone (V26); a forgotten check-out is flagged Incomplete and closed 4 hours after the shift (V22), not 2 |
| [PPE](../03_implement/STEELSIGHT-PPE-IMP-001_TDS.md) | PPE Violations | Class, review sequences, dispute state machine | No: vest links and burst alerts are gone; review works as in 3.6 below |
| [TKT](../03_implement/STEELSIGHT-TKT-IMP-001_TDS.md) | Tickets | Class, ticket sequences, ticket state machine | Partly: every ticket goes to the Admin (V20); categories, outcomes and cancelling are as in 3.8 below |
| [NTF](../03_implement/STEELSIGHT-NTF-IMP-001_TDS.md) | Notifications | Class, notice sequences, notice state machine | No: the burst alert API and the warning and danger notices are gone; notices work as in 3.9 below |
| [RPT](../03_implement/STEELSIGHT-RPT-IMP-001_TDS.md) | Reporting | Class, export sequences | Partly: the sheets are as in 3.11 below |
| [STO](../03_implement/STEELSIGHT-STO-IMP-001_TDS.md) | Photo Storage | Class, upload and read sequences | Yes |
| [PLT](../03_implement/STEELSIGHT-PLT-IMP-001_TDS.md) | Platform | Class, error-handling sequences | Yes |

The detection app's own TDS are in its repository, `steelsight-demo/03_implement/`: `STEELSIGHT-DET-DPL`
(settings, local database, start-up), `-VIS` (cameras, detector, tracker), `-IDG` (identity and gate),
`-SAF` (PPE rules and voice), `-REC` (local records and photos), `-SYN` (outbox and sync) and `-WEB`
(the desktop pages).

| # | Feature | Runs in | Main tables | TDS |
| --- | --- | --- | --- | --- |
| 3.1 | Sign-in and session | Web ERP | app_user, role, permission | IAM |
| 3.2 | Accounts, face photos and Zalo link | Web ERP | app_user, face_embedding, zalo_link_code | IAM, STO, NTF |
| 3.3 | Teams, zones and cameras | Web ERP | team, zone, camera | SCH |
| 3.4 | Shift planning and timetable | Web ERP | shift, schedule_notice | SCH |
| 3.5 | Attendance and pending overtime | Web ERP, database | check_in, check_in_correction, pending_overtime | ATT |
| 3.6 | Violation review and appeal | Web ERP, database | violation | PPE |
| 3.7 | Safety score and bonus level | Web ERP, database | violation, safety_policy, monthly_score | PPE, RPT |
| 3.8 | Tickets | Web ERP | ticket, ticket_category, ticket_comment, ticket_participant | TKT |
| 3.9 | Notifications and the Zalo bot | Web ERP, database | notification, notification_delivery | NTF |
| 3.10 | Scheduled jobs and month closing | Web ERP | job_run, month_lock, monthly_score | PLT |
| 3.11 | Home page and monthly report | Web ERP | all of the above, report_export | RPT |
| 3.12 | Photo storage | Web ERP | (files) | STO |
| 3.13 | Detection: gate check-in, PPE monitoring, outbox and sync | Detection app | check_in, violation, face_embedding | DET-* |

In the tables below, *Controller* classes are in `web`, and the operations are the public methods.

## 3.1 Sign-in and Session

People sign in with their employee code and password. Each permission code a role holds becomes a
Spring Security authority. Someone whose password an Admin chose must pick their own first.

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `AuthController`, `AccountController` | Controller | Sign-in page; the forced password change page |
| `SecurityConfig` | Configuration | Two filter chains: `/api/**` (API key, no session) and the pages (form login, CSRF, sessions) |
| `AppUserDetailsService` | Service | `loadUserByUsername(code)`: active, not deleted, authorities = permission codes |
| `SignInLimiter`, `SignInLimitFilter` | Component | Locks sign-in for 15 minutes after 5 failures from one client or 20 for one code (BR-04) |
| `PasswordChangeRequiredFilter`, `FirstSignInHandler` | Component | Send a user with `must_change_password` to the change page until they choose a password (BR-03) |
| `UserSessions` | Component | `endAll(user)`, `endOthers(user)`: ends sessions when an account is deactivated, deleted or its password changes |
| `AdminBootstrap` | Component | Creates the first Admin from the environment when there is none |
| `AuthenticatedUser` | Model | The signed-in user, with `can(permission)` |

Diagrams: IAM §5 (class), §6 (sign-in and wrong-password sequences, account state machine).

## 3.2 Accounts, Face Photos and Zalo Link

Only the Admin creates and changes accounts, face photos, roles, teams and staff groups. A new FRONT
face photo needs the signed consent and queues a PENDING `face_embedding` row, which the detection app
turns into a vector (3.13). LEFT and RIGHT photos are optional. Soft-deleting an account erases its
embeddings (trigger), unlinks Zalo and cancels its future shifts (BR-07).

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `TeamController` (`/team/*`), `WorkerController` | Controller | Account list and form, role, team, group, password reset, deactivate, delete, face photos by pose, Zalo code |
| `UserService` | Service | `create`, `updateProfile`, `changeRole`, `assignTeam`, `changeGroup`, `changeFacePhoto`, `changeFaceAngle`, `removeFaceAngle`, `resetPassword`, `setActive`, `softDelete`, `changeOwnPassword`, `faceEnrolment` |
| `AccessPolicy` | Service | `has`, `require`, `canUpdateUser`, `canAssignShift`, `canReadTicket`, `canUpdateTicket`, `canCommentTicket`: every record-level check, mostly through the database `can_*()` functions |
| `ZaloController`, `ZaloLinks`, `ZaloStore` | Controller, Service | `newCode`, `currentCode`, `handle` (a code sent to the bot), `unlink`; 6-digit codes valid for 24 hours, used once (BR-13) |
| `PhotoStorage` | Interface | See 3.12 |
| `AppUser`, `FaceEmbedding` | Entity | See [database-design.md](database-design.md) 2.1.4 and 2.3.1 |

Diagrams: IAM §5 and §6 (create account, upload face photo); STO §6 (store a photo).

## 3.3 Teams, Zones and Cameras

The Admin creates teams and picks each one's leader, a MANAGER who leads at most one team. The Admin
adds zones (the database gives each its fixed code), renames them, gives each work area its team, and
links each camera code from the detection app to a zone. A camera in the gate attendance zone records
check-ins; every other camera records violations.

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `TeamController` (`/teams`), `AdminSettingsController` (`/zones`) | Controller | Teams and leaders; zones, their team and cameras |
| `TeamService` | Service | `create`, `changeManager`, `delete`, `listManaged`, `listLeaderCandidates` |
| `ZoneService` | Service | `create`, `rename`, `assignTeam`, `linkCamera`, `removeCamera` (only a camera with no records), `list`, `cameras` |
| `Team`, `Zone`, `Camera` | Entity | `Zone.code` is read back from the database (`@Generated`) and never written |

Diagrams: SCH §5 (class). Not drawn there: `Zone.code`, `Zone.kind`, `Zone.team` and `ZoneService`.

## 3.4 Shift Planning and Timetable

A team leader plans regular shifts for their own team on a week grid, before Saturday 18:00 of the week
before; the Admin plans anyone and adds overtime shifts with a reason (BR-21 to BR-24). A shift is 30
minutes to 12 hours, can't overlap another of the same person, and is locked once it starts. On Saturday
at 18:00 everyone gets next week's schedule; a later change to the current or next week is sent on its
own (BR-18, BR-19). A check-out completes the shift (V25); the hourly job completes the rest.

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `ShiftController`, `TimetableController` | Controller | Week grid, add, edit, cancel, copy last week; own timetable |
| `ShiftService` | Service | `create`, `update`, `cancel`, `copyLastWeek`, `listTeam`, `listAll`, `listOwn`, `isOpenForManagers`, `publishedAt` |
| `ShiftGrid`, `ShiftForm` | Web helper | The grid rows and the form |
| `Notices` | Service | `shiftAdded`, `shiftChanged`, `shiftCancelled`, `weekSchedule` |
| `Shift` | Entity | `durationHours` is generated by the database |

Diagrams: SCH §6 (plan a shift, overlap refused, shift state machine).

## 3.5 Attendance and Pending Overtime

The detection app records face check-ins and check-outs (3.13). A database trigger attaches each visit
to the person's shift if it starts at most 60 minutes before (BR-30). The ERP judges each visit against
its shift: late after 5 minutes, early leave, paid hours by the shift frame, visits under 10 minutes
ignored (BR-31 to BR-33). A visit with no check-out 20 minutes after the shift is flagged Incomplete and
closed 4 hours after, with no guessed time (BR-35). An HSE Officer, or the Admin, records or corrects a
visit by hand with a reason, and each correction is kept (BR-39). Time after the shift end plus 20
minutes becomes pending overtime until the Admin adds an overtime shift (BR-34, BR-37).

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `AttendanceController` | Controller | My attendance, team attendance, the attendance list, record and correct a visit, pending overtime |
| `CheckInService` | Service | `recordManual`, `correct`, `listOwn`, `listTeam`, `listOnSite`, `listCorrections`, `listOwnIncomplete` |
| `AttendanceRules` | Service | `assess(visit)`: late, early, missing check-out, too short |
| `AttendanceCalculator`, `ShiftAttendance` | Service | Paid hours, absences, half day for an unexplained missing check-out (company setting, BR-36), valid and pending overtime |
| `CheckOutHours` | Component | `fill`: adds the paid hours to CHECK_OUT notices before Zalo sends them |
| `PendingOvertimeService` | Service | `createFromVisits`, `expire`, `cancel`, `leaderNote`, `listOpen` |
| `CheckIn`, `CheckInCorrection`, `PendingOvertime` | Entity | See [database-design.md](database-design.md) 2.3 |

Diagrams: ATT §5 and §6 (gate check-in, manual correction, visit and enrolment state machines). See the
table at the top for what changed.

## 3.6 Violation Review and Appeal

The detection app writes each violation with only a suggested person (3.13). A trigger fills the zone
from the camera, makes any named person a suggestion, and alerts the HSE Officers on duty (BR-54). The
review is:

1. An HSE Officer, or the Admin, opens the Review Queue and **takes** a violation (`taken_by`), so two
   people don't review the same one.
2. They **confirm** the person (from the work area's team first, or the gate suggestion within 30
   seconds, BR-46), mark a **stranger**, or **dismiss** it with a reason (BR-49 to BR-52). An OFFICE
   employee's violation becomes a REMINDER, which doesn't count.
3. A violation not reviewed by the end of its day becomes **OVERDUE** (BR-53).
4. The confirmed person can **appeal** within the company's appeal period (3 working days by default).
   The appeal opens a PPE appeal ticket; the Admin decides it as **UPHELD** or **OVERTURNED** (3.8).

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `ViolationController` | Controller | My violations, Review Queue, violation list, detail, photo, take, confirm, dismiss, appeal |
| `ViolationService` | Service | `queue`, `take`, `candidates`, `suggestion`, `confirm`, `dismiss`, `appealDeadline`, `whyNoAppeal`, `markAppealed`, `decideAppeal`, `list`, `listOwn`, `countOpen`, `countOverdue` |
| `MonthLocks` | Component | `requireOpen`: nothing in a locked month changes (BR-70) |
| `Violation` | Entity | See [database-design.md](database-design.md) 2.4.1 |

Diagrams: PPE §5 and §6 are out of date (vest links, burst alerts). The steps above are the current
flow, and the database rules are in [database-design.md](database-design.md) section 3.

## 3.7 Safety Score and Bonus Level

Each active SITE employee with the role WORKER or MANAGER starts the month on 100 and loses 5 points for
each confirmed violation that isn't overturned (BR-56, BR-57). The score gives a bonus level: 0 above 80,
then 1, 2 and 3 at 80, 70 and 60 and below, which reduce the monthly safety bonus by 2%, 4% and 6%
(BR-58). The score is worked out by `safety_scores()` in the database; a locked month reads its frozen
`monthly_score` rows.

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `SafetyScoreService` | Service | `own`, `of`, `current`, `inMonth`, `listWorkers`, `listTimeSlots` (violations per 100 worker-hours) |
| `SafetyController` | Controller | Unsafe time slots for HSE |
| `SafetyLevel` | Enum | LEVEL_0 to LEVEL_3 with their reduction |

Diagrams: PPE §5 and RPT §5 show the older warning and danger levels.

## 3.8 Tickets

Anyone can open a ticket in one of five categories: PPE appeal, attendance error, missing check-out,
leave request and other (BR-60). Every ticket goes to the Admin (BR-61), and an Admin can't handle a
ticket they opened. The team leader of the author can read and comment. The Admin can ask an HSE
Officer for the facts of a shift, who can then read and comment. The author can cancel an open ticket.
The Admin decides: APPROVED or REJECTED, or UPHELD or OVERTURNED for an appeal. Approving an attendance
error or a missing check-out corrects the visit, and the correction names the ticket.

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `TicketController` | Controller | My tickets, the queue, new ticket, detail, comment, take, assign, ask HSE, HSE facts, decide, resolve, cancel, photo |
| `TicketService` | Service | `create`, `openDispute`, `addComment`, `assign`, `askHse`, `saveHseFacts`, `decide`, `decideAppeal`, `prepareAttendanceApproval`, `approveAttendanceError`, `resolve`, `cancel`, `listOwn`, `listQueue`, `listTeam`, `listCategoriesForNewTicket` |
| `Ticket`, `TicketCategory`, `TicketComment` | Entity | See [database-design.md](database-design.md) 2.5 |

Diagrams: TKT §5 and §6 show routing to HSE and the older categories; the rules above are current.

## 3.9 Notifications and the Zalo Bot

Every notice is saved first and shown in the web ERP (the bell and the Notifications page), then a
delivery row says whether Zalo must send it (BR-15, BR-16). Notices store a kind and parameters, so each
reader sees them in their own language; the Zalo copy is Vietnamese. Violation alerts and gate check-in
and check-out notices are made by database triggers; the rest by the ERP.

1. A notice is inserted (by a service through `NotificationService.notify`, or by a trigger).
2. A trigger adds its delivery row: PENDING if the person linked Zalo, otherwise SKIPPED.
3. Every minute, `JobScheduler` runs `CheckOutHours.fill`, then `ZaloDispatcher.sendDue`, which sends
   each due notice with `ZaloBot.send` and retries after 1, 5 and 15 minutes before FAILED. A text over
   1,900 characters is cut, with a link to the page (BR-17).
4. Messages people send to the bot reach `ZaloController` on `/zalo/webhook`: a 6-digit link code,
   `/stop`, or the commands `/help`, `/lich [tuantoi]` and `/chamcong [thangtruoc]` (team addition).

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `NotificationController` | Controller | Notifications page, mark read, mark all read |
| `NotificationService` | Service | `notify`, `notifyAll`, `listOwn`, `countUnread`, `markRead`, `markAllRead`, `deliveries` |
| `Notices`, `NoticeText` | Service | Build the parameters of each kind; `render` the text in a language |
| `ZaloDispatcher` | Service | `sendDue` |
| `ZaloBot` | Component | `send`, `registerWebhook`, `acceptsSecret`, `botLink` |
| `ZaloCommands` | Component | `reply`, `split` (replies over 2,000 characters are split at line ends) |
| `NotificationKind` | Enum | Every kind and the parameters it needs |

Diagrams: NTF §5 and §6 are out of date (burst alerts, warning and danger notices). The steps above are
the current flow.

## 3.10 Scheduled Jobs and Month Closing

`JobScheduler` ticks every 60 seconds in Vietnam time. For each job it works out the latest time it was
due; a `job_run` row for that time means it already ran, so a job never runs twice and a run missed while
Render slept runs once on the next tick (CR-12).

| Job | When | What `SiteJobs` does |
| --- | --- | --- |
| hourly | Every hour | Cancels pending overtime past its 48 hours, closes visits open 4 hours after their shift, marks ended shifts COMPLETED |
| day-end | 23:00 | Flags Incomplete attendance and closes stale visits (BR-35), splits time after the shift end plus 20 minutes into pending overtime (BR-34), sets the day's PENDING_REVIEW violations to OVERDUE (BR-53) |
| morning | 07:00 | Tells the Admins how many violations are overdue and how much overtime is pending, and each team leader their team's pending overtime |
| old-tickets | 08:00 | Tells the Admins how many tickets have been open too long |
| weekly-schedule | Saturday 18:00 | Sends next week's schedule to everyone (BR-18) |
| month-end | Last working day | Tells the Admins the open tickets and the HSE Officers the overdue violations, before the month is locked |
| cancel-last-month-overtime | 2nd of the month, after midnight | Cancels last month's pending overtime (BR-37) |
| month-lock | 23:59 of the 2nd working day | Locks last month and freezes every SITE employee's score and bonus level in `monthly_score` (BR-70) |

Diagrams: PLT §5 (platform classes). The jobs postdate the PLT diagrams.

## 3.11 Home Page and Monthly Report

The home page shows each role what it needs today: own shift, tickets and violations; HSE's queue and
on-site list; the Admin's open tickets and failed face photos; team rosters for leaders. Charts are bars
worked out in Java (`Charts`) and drawn in HTML and CSS.

The monthly report is exported by a team leader for their own team or by the Admin for the site
(BR-71). Before the month is locked it says "Provisional". Each export is logged. The sheets are in
[security-and-report-design.md](security-and-report-design.md), section 4.

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `HomeController`, `NavCounts`, `TodayRoster`, `Charts` | Controller, Web helper | The home page and the sidebar counts |
| `ReportController` | Controller | `/reports` (choose month and team) and `/team/report` (download) |
| `MonthlyReportService` | Service | `build(month, team)`, `logExport`, `exports` |
| `MonthlyReportWorkbook` | Component | `write`: streams the .xlsx with fastexcel; `fileName` |

Diagrams: RPT §5 and §6 (build and export). The sheets changed; see section 4 of the security and report
design.

## 3.12 Photo Storage

Photos are kept outside the database: on local disk in development and in private Supabase Storage
buckets in production. The type comes from the file's bytes (JPEG or PNG), names are random, and faces,
tickets and evidence are kept in separate albums. Callers check permissions first; browsers never get a
Storage URL.

| Class | Stereotype | Main operations |
| --- | --- | --- |
| `PhotoStorage` | Interface | `store(album, photo)` returns the stored name; `load(album, name)`; `delete(album, name)`; `contentType(name)` |
| `LocalPhotoStorage`, `SupabasePhotoStorage` | Component | The two implementations, chosen by `steelsight.storage.type` |
| `PhotoAlbum`, `PhotoFormat` | Enum | TICKET, FACE and EVIDENCE, each with its bucket (the ERP only reads EVIDENCE); the image type from its bytes |

Diagrams: STO §5 and §6.

## 3.13 Detection App

The detection app is designed in its own repository. In short:

- **Gate check-in (CAM-01):** a face matched twice, 1 second apart, against the READY embeddings is a
  check-in, or a check-out if the person is on site; the same person is ignored for 5 minutes (BR-27 to
  BR-29). The event goes to the outbox.
- **PPE monitoring (CAM-02 to CAM-04):** a person without a hard hat for 10 seconds or a vest for 3
  seconds gets a spoken reminder, then a violation with an evidence photo and a suggested person when
  the face is known; both missing is one NO_HARD_HAT_AND_VEST (BR-40 to BR-45).
- **Outbox and sync:** every event waits in SQLite with its own UUID. `ErpSync` uploads the photo to
  `evidence-photos`, then inserts the row with `ON CONFLICT (event_id) DO NOTHING`, oldest first. It
  also enrols PENDING face photos and calls `close_stale_check_ins()` on a timer.

The SQL it may run is fixed by [detection-app-contract.md](detection-app-contract.md). Diagrams: the
`STEELSIGHT-DET-*` TDS listed at the top. `DET-IDG` and `DET-SYN` still mention vest numbers, which the
app no longer reads (CR-01).
