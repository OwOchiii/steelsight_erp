# AI usage log

How AI was used on the Steelsight ERP. The full conversations are in the transcripts for [4 November](../transcript/4-11-2026.md), [5 November, including the session that ran past midnight](../transcript/5-11-2026.md) [6 November](../transcript/6-11-2026.md), [9 November](../transcript/9-11-2026.md), [16 November](../transcript/16-11-2026.md), [17 November](../transcript/17-11-2026.md), [21 November](../transcript/21-11-2026.md), [23 November](../transcript/23-11-2026.md), [24 November](../transcript/24-11-2026.md) and [25 November](../transcript/25-11-2026.md). The 17 November session was opened in the detection app's folder, and the 21 and 23 November sessions in the ERP's; all three changed both programs. Only their ERP work is logged here, and the detection work is in that repository's `chatlog/detection-ai-log.md`.

| | |
|---|---|
| Tool | Claude Code (Opus 5.5) |
| Dates | 2026-11-04 to 2026-11-25 (one session ran past midnight) |
| Branch | `feature/erp-backend` |

## Summary by request

Times are UTC+7.

### 2026-11-04

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 13:57 | Add models and repositories for the new database design | Flyway schema, check-in and face embedding models, integrity rules, integration tests | `06cf814` |
| 14:52 | Commit, then build the services | Service layer with permission and scope checks | `5588b25` |
| 16:49 | Add login, ticket zone and photo, and the database design document | Login, first admin bootstrap, ticket zone and photo, design document | `aa33c4c` |
| 17:15 | Add pages and redo the database design document in a set format | Worker pages, plus `docs/database-design.md` | `43a03df` |
| 20:23 | Build the HSE and manager pages, and refactor using Generic OOP | HSE and manager pages, generic repository lookups, status rules on entities, shared controller base class | `9d5f3d2` |
| 21:13 | Add paging, a check-in version column, and late and early tags | Paged lists, version column, attendance tags, a late threshold per team | `9ffc888` |
| 21:37 | Cap make-up time and close unfinished visits after the day ends | 17:15 limit, replaced automatic check-out function | `14e030d` |
| 22:02 | Decide on DTOs and create these logs | Advice to skip full output DTOs for now, this log and the transcript | `0ce8d08` |
| 22:07 | Fix the database password error when starting the app | Local `steelsight-db` Docker database on port 55433, and `.env` pointed at it | Not in git |
| 22:33 | Add sample data, keep the logs up to date, and commit | `scripts/dev-sample-data.sql`, loaded locally and checked for each role | `0ce8d08` |
| 22:39 | Build a schedule grid | Shifts page shows people by day, with weekly hours and add-shift cells; screenshots checked on desktop and phone | `3fa57be` |

### 2026-11-05

The developer committed all of this day's work together at 23:47.

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 13:30 | Add console logging, and look into the missing REST API | Request, error and sign-in logging with a shared request ID; advice to skip REST until something needs it | `0f3611f` |
| 13:50 | Consider moving to Next.js, React and Tailwind on Vercel | Advice against it for now, with the costs and when it would be worth doing | None |
| 14:05 | Make the pages less basic, using Odoo as the reference: sidebar, informative dashboard, dark and light mode, Vietnamese and English | Sidebar layout, new dashboard, both themes, both languages, Tailwind styles, pages that update without a full reload through `htmx` | `0f3611f` |
| 15:47 | Worker timetable grid, safety scores with notifications, wider manager and HSE access, and a REST API for detection alerts | Weekly worker grid, calculated safety score, warning, danger and HSE notifications, `/workers`, `/safety/shifts`, own-team ticket limits for managers, `POST /api/v1/detection/alerts` | `0f3611f` |
| 00:09 (6 Nov) | Update this log and write the transcript | This log update and `transcript/5-11-2026.md` | `bc53de5` |
| 00:19 (6 Nov) | Remove the password default and commit the logs; asked whether to deploy, build CI/CD and move to Supabase | Password default removed, and an approved plan: fix deployment gaps, move to Supabase, add a small GitHub to Render pipeline | `bc53de5` for the password and logs |
| 00:21 (6 Nov) | Carry out the deployment plan | `Dockerfile`, `prod` profile, health check, sign-in limit, Supabase photo storage, GitHub Actions pipeline, `docs/deployment.md` | `d4f978f` |
| 00:31 (6 Nov) | Update the log and transcript for the night | This update | `d4f978f` |
| 09:29 (6 Nov) | Update the database design document, check for a notification dropdown and the diagram, and write a tech stack matrix | `docs/database-design.md` section 1.6 for the 3 new tables; no dropdown found, since the bell links to a page; diagram updated; `docs/tech-stack.md` | `d4f978f` |
| 09:36 (6 Nov) | Change the tech stack's connections into a protocol and port table, and show GitLab CI/CD as the pipeline with GitHub only testing for now | Protocol and port table; GitLab CI/CD rows, with GitHub Actions as testing for now | `d4f978f` |
| 11:24 (6 Nov) | Check why an admin can't upload a face photo when creating or editing an account | Found that no page creates accounts at all, and only name, email and phone could be edited | None |
| 11:29 to 11:44 (6 Nov) | Plan admin account management, and then stop managers editing anyone | Approved plan, then built: create account, role, team, active status, password reset, face photo upload with enrolment status, delete, own password change, forced change after an admin sets a password, admin-only editing | `94a2c3c` |
| 15:09 (6 Nov) | New rules: one leader per team, leaders plan only their team's shifts, only admin changes workers, leaders export a monthly Excel report for accounting, and tickets go to HSE for safety or to admin for everything else | Ticket routing to HSE or admin with a new leave request category, team setup for admins with one team per leader, and a monthly `.xlsx` report of safety scores, violations, attendance and no-shows | `94a2c3c` |
| 15:37 (6 Nov) | Set up a Docker registry, to get an image URL to give Render | The workflow builds and pushes `ghcr.io/owochiii/steelsight_erp` and deploys that commit's tag through Render's deploy hook; image built and started locally | `94a2c3c` |
| 15:47 (6 Nov) | Commit and push | Commit `94a2c3c` with the day's work; the push failed because WSL has no GitHub sign-in | `94a2c3c`, pushed later from Windows and merged in PR #1 |
| 15:50 (6 Nov) | Write today's log and transcript | This update and `transcript/6-11-2026.md` | `f005861` |
| 16:09 (6 Nov) | List the environment the app and Supabase need | The required and optional variables for Render and local use, the Supabase setup, and what the detection service needs | None |
| 16:11 (6 Nov) | Make a `.env.example` with those | `.env.example` with local defaults, Render values as comments, and the detection service's settings | `f005861` |
| 16:29 (6 Nov) | Fix a start-up error: `Driver org.postgresql.Driver claims to not accept jdbcUrl` | The Supabase URI had the username and password inside it, which the JDBC driver refuses, and still held the `[YOUR-PASSWORD]` placeholder; split it into `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` | Not in git |
| 16:37 (6 Nov) | Write the docs and transcript | The database URL format and `.env.example` added to `docs/deployment.md`, this log and `transcript/6-11-2026.md` brought up to date | `f005861` |
| 16:59 (6 Nov) | Write a tutorial for using the app: signing in as admin, creating accounts and so on | `docs/user-guide.md`, by role, using the app's own labels and routes | `f005861` |
| 17:08 (6 Nov) | Didn't understand the detection service part of `.env.example` | Explained that those lines are comments for the separate Python service, and offered to move them into `docs/deployment.md`; no answer yet | None |
| 21:01 (6 Nov) | Read the whole codebase, split it by module, and write the ADR, TDS and Test-Spec from the templates in `00_policy/` | 9 modules; `02_design/MODULE-MAP.md` with the module split, business rules, user stories and 12 gaps; 24 ADRs in `02_design/adr/`; a TDS per module in `03_implement/`; a Test-Spec per module in `04_testing/`, mapping all 244 existing tests | `8611bf3`, committed by the developer |
| 22:16 (6 Nov) | Write today's log and transcript | This update and the rest of `transcript/6-11-2026.md` | Not committed |

### 2026-11-09

The developer committed this day's work, with their own attendance and report changes, as `23e5929` on 12 November, then added `8f9d2f1` (sign-ins that race a password change are refused) without Claude.

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 16:34 | Find the API the detection server calls for violation alerts, and suggest how to secure it | Located `DetectionAlertController`, found it was already guarded by an API key, and listed the remaining ways to abuse it | None |
| 16:39 | Add rate limiting and stop fake alerts silencing real ones; suggest JWT and other security | `ApiLimiter`, a quiet period per registered camera, delayed or future alerts kept without notifying, upper limits on counts; advice against JWT | `23e5929` |
| 16:46 | Run a full security review of the pages and permissions | Review of every route and its service checks, plus a temporary test of routes as a worker: no critical or high findings, 2 medium, 5 low | None |
| 16:51 | Fix the three top findings | Password changes end other sessions, `check_in_correction` history table (`V12`), Swagger library removed | `23e5929` |
| 16:59 | Add the history section, and write the log and today's transcript | Change history on the visit edit page in both languages, this update and `transcript/9-11-2026.md` | `23e5929` |

### 2026-11-16

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 21:12 | An oversized photo upload opens a JSON page instead of showing the error; check every error a user can make by breaking a rule | Found the cause (Tomcat refused the upload, the CSRF check then failed, and Spring Boot's `/error` answered JSON to htmx). The 5 MB rule moved into the app with a browser check first; `PageErrorController`, `MultipartLimitFilter`, `SamePage`; failed POSTs go back to their page; stale edits keep the form; report refusals go back to the Team page; line breaks counted once; `ErrorPagesTest`, `MultipartLimitFilterTest`, `TextTest` | Not committed |
| 21:38 | Docker is running, check it | Full suite run: 2 test expectations corrected, then 282 tests passed | Not committed |
| 21:48 | Write the log, today's transcript, and the design, implementation and testing documents for anything new | `ADR-025`, ADR-006 amended, `MODULE-MAP.md` (BR-PLT-009 and updated rules), 8 TDS and 4 Test-Specs updated with changelog entries, this update and `transcript/16-11-2026.md` | Not committed |

### 2026-11-17

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 09:56 | Asked whether Claude could change the ERP so a worker has more face photos for better recognition, and whether that needs another column | No new embedding column: migration `V14` adds `face_embedding.pose` (FRONT, LEFT, RIGHT) with one row per angle; `FacePose`, repository and `UserService` angle methods, `/team/{id}/face/{left\|right}` routes, an "Extra angles" section on the account edit page in both languages, photo clean-up on delete; 5 new tests | Not committed |
| 10:16 | `TranslationsTest` failed: 4 new messages had no Vietnamese translation | The 4 `msg.*` keys added to `messages_vi.properties` | Not committed |
| 10:21 | Pasted a CodeRabbit review; check each finding against the code before fixing | Two real races fixed with a `SELECT … FOR UPDATE` lock on the worker (`requireForUpdate`) in `changeFaceAngle`, `removeFaceAngle` and `softDelete`; the report page's flash message now saved under the same path as its redirect; the translations finding was already fixed | Not committed |
| 10:27 | Docker is running, run the full suite | 284 tests passed | Not committed |
| 13:46 | Write the logs, transcript and documents, in step with the ERP documents | `ADR-026`; ADR-011, `MODULE-MAP.md` (BR-ATT-009, new BR-ATT-011), the ATT and IAM TDS and Test-Specs, `docs/database-design.md` and `docs/user-guide.md` updated; this update and `transcript/17-11-2026.md` | Not committed |

### 2026-11-21

This day's ERP work was committed in `b6bbb9a` on 23 November, together with that day's work.

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 12:58 | The ERP and detection app added tables, so update `docs/database-design.md` | Checked V1–V14 column by column against the document: already up to date. The detection app's new tables are in its local SQLite file, not the shared database | None |
| 13:07 | Read the SRS (Report 3, `.docx`) and say what both programs have, miss, or have extra | Review of the 78 use cases and BR-01 to BR-46: done, missing, built differently (10 rows), extra, and a suggested order | None |
| 13:21 | Make auto-close and the ticket categories match the SRS, merge both category lists, skip recorded video, add Telegram for check-ins and monthly schedules | `V15` (open visits close 2 h after the shift ends), `V16` (Attendance correction, Personal information update, Other, handled by Admin), ADR-013 amendment, ATT TDS and Test-Spec, `docs/database-design.md`; a view on the zone-to-team idea | Not committed |
| 13:35 | Docker Desktop is on, run whatever tests are needed | Full suite: 285 passed; a V16 test added to `TeamsTicketsAndReportTest` | Not committed |
| 13:49 | Build Telegram, leave the zone fallback out, and make sure the gate workflow works: account and face, vector in the detection app, face scan with PPE, the name or missing PPE spoken | `V17` (`app_user.telegram_chat_id`, `telegram_link_code`, `schedule_notice`, read grant for detection); `TelegramBot`, `TelegramStore`, `TelegramNotices`, `TelegramLinks`, `ScheduleNoticeService`; webhook, link and unlink, a Telegram card on the home page, "Send … schedule on Telegram" on the Shifts page; messages for visits entered or corrected by hand and for shift changes; 10 tests. The gate workflow and gate Telegram messages are in the detection log | Not committed |
| 15:36 | Update the chat log and transcript | This update and `transcript/21-11-2026.md`, in both repositories | Not committed |

### 2026-11-23

The developer committed the work up to 22:15 as `b6bbb9a` at 22:23, and the QR code as `a6b9302` at 22:44. That commit also took in the 21 November work (V15 to V17) and the SRS `.docx`.

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 12:47 | Compare the ERP with the SRS (23/11 revision) and check the requirements and workflows | Review: what matches, 11 areas where the code contradicts the SRS, what isn't built (no scheduled jobs at all, month lock, overtime, ticket workflows, Zones and Settings pages), the 14 workflows end to end, and old SRS rule numbers cited in uncommitted migrations | None |
| 13:03 | Remove Telegram, and match the SRS for ticket handling | `V19` drops `telegram_link_code` and `app_user.telegram_chat_id`; the Telegram package, controller, panel and settings removed; the schedule button moved to `/shifts/schedule` on Zalo. `V20` sends every ticket to the Admin, lets the Admin decide disputes, stops anyone handling their own ticket, and lets team leaders read and comment on their team's tickets | `b6bbb9a` |
| 13:25 | Test every SRS requirement and workflow, match the SRS as closely as possible, change zones to several cameras owned by a team, and check how the report handles violations | `V21` (violation review lifecycle, suggestions, zones with a kind and owning team, 5-point score with levels 0–3, `monthly_score`, `month_lock`, notification deliveries, company settings, export log, `job_run`) and `V22` (shift kind and reason, Incomplete flag, `pending_overtime`, the five SRS ticket categories, ticket participants). New and rewritten services, controllers and pages: Review Queue, My and Team Attendance, Pending Overtime, Zones, Company Settings, Monthly Report; `JobScheduler` and `SiteJobs`; the Zalo dispatcher with retries; translations, with SRS message texts in Vietnamese. The report now lists every confirmed violation, reminder and stranger | `b6bbb9a` |
| 19:23 | Continue after the usage limit | `SrsViolationReviewTest`, `SrsTicketRulesTest`, `SrsShiftsAndJobsTest`, `SrsWorkflowTest`, `srs/ShiftAttendanceTest`; `04_testing/SRS-TRACEABILITY.md`; `docs/detection-app-contract.md`; 380 tests pass | `b6bbb9a` |
| 20:15 | Can Claude reach the detection app's folder | Found at `/mnt/d/SteelSight_Demo/steelsight-demo`; nothing breaks against V19–V22, with a list of what it still does the old way | None |
| 20:32 | Update the desktop app to match the SRS | Done in the detection app; see its log. On the ERP side, the contract and traceability docs record it | `b6bbb9a` for the ERP docs |
| 21:18 | Claude's opinion of the SRS | Review: testable rules and IDs are its strengths. Problems: the zone model is out of date, BR-46 and BR-47 will rarely give a suggestion, BR-42 can't be expressed with two camera roles, tracking assumptions aren't stated, ticket ownership is easy to misread, facts are repeated | None |
| 21:31 | How Zalo works, what happens to someone with no shift who walks on site, and how a worker's shift gets marked done | Zalo was tested only against a fake bot, with a 15-minute test plan. A check-in with no shift is recorded with no pay and no alert. Shift `COMPLETED` was set only by hand and nothing read it | None |
| 21:34 | Make shift completion automatic; manual steps only where the Admin has to decide | The hourly job, and the 23:00 job as a backstop, mark ended shifts `COMPLETED`, skipping cancelled shifts and locked months. The "Mark completed" button, endpoint and `ShiftService.complete()` removed. Home page counts fixed; 381 tests pass | `b6bbb9a` |
| 21:55 | Add the Zalo bot to `.env.example` | `ZALO_BOT_TOKEN`, `ZALO_BOT_LINK`, `ZALO_WEBHOOK_SECRET`, `ZALO_WEBHOOK_URL`, `APP_BASE_URL`, `SUPABASE_EVIDENCE_BUCKET`; the same in `docs/deployment.md`; a wrong variable name fixed in a `ZaloBot` comment | `b6bbb9a` |
| 21:59 | Is the webhook secret Zalo's secret token | Yes: you make it up, the ERP registers it, and Zalo sends it back in a header the ERP checks | None |
| 22:02 | Which webhook URL goes in the Zalo bot settings | `https://<service>.onrender.com/zalo/webhook`; the ERP registers it on start-up if the variables are set; a tunnel is needed for local testing | None |
| 22:05 | Update the docs again, and the tech stack now that OCR is gone | `docs/tech-stack.md` (EasyOCR gone; Flyway V1–V22, 30 tables; jobs, Zalo, evidence bucket, uptime monitor added), user guide sections for Zalo and the SRS worker rules, a note at the top of the PPE TDS; the detection repo's docs and copies (in its log) | `b6bbb9a` |
| 22:15 | Is the branch ready to merge to `master` | Mostly: `mvn verify` passes 381 tests and no secrets are in the changes. The stale stylesheet was rebuilt. Commit first, back up Supabase and check its Flyway history before merging, and deploy the ERP before the new desktop app | `b6bbb9a`, committed by the developer |
| 22:30 | Send Zalo messages by phone number instead of a link code, with the phone required | Not possible with the Zalo Bot Platform; phone-number sending needs a verified Official Account and paid ZBS templates. Suggested keeping the code and adding the QR | None |
| 22:32 | Do both: phone required and the QR | Phone was already required on the forms and in the service; a hint added. Inline SVG QR of the bot link on the Admin's account page and the worker's Dashboard, using `io.nayuki:qrcodegen:1.8.0`; `QrCodesTest` and two page tests; 385 tests pass | `a6b9302` |
| 22:54 | Explain the workflow for registering a worker and linking Zalo | The steps from the code: account, first link code and QR, face photo queue, scanning and sending the code, the bot's reply, and what can go wrong | None |
| 23:01 | What the Zalo bot sends; the base requirement is check-in and check-out | Found that check-in and check-out notices were never created. `V23` adds triggers on the detection app's check-in writes; `CheckOutHours` fills in paid hours before each Zalo send; 3 tests, 388 pass. A full list of what the bot sends | Not committed |
| 23:13 | Write the transcript and chat log | Not done: the usage limit was reached. Written on 24 November as this update and `transcript/23-11-2026.md` | Not committed |

### 2026-11-24

Nothing from this day is committed yet.

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 10:15 | Write the chat log and the previous session's transcript | `transcript/23-11-2026.md` and the 23 November entries in this log. The 23 November session hit the usage limit before writing them. Copied to the detection repo, with that day's desktop work in its own log | Not committed |
| 10:28 | How Zalo notices work; scanning the QR showed a string instead of opening the bot | The QR encodes `ZALO_BOT_LINK` unchanged, so the server's value isn't the bot's full `https://` link. Copy the link from Bot Creator's own QR, and regenerate the token if it was put in that variable | None |
| 10:34 | Render fails to start: `missing column [score] in table [notification]` | The database has V21, which dropped that column, but Render started an image from before the merge. CI for `febb2bf` passed and called the deploy hook. Fix: set the image URL to `:latest` and deploy, and don't roll back to an older image | None |
| 10:41 | How to link the bot | The 5 server variables, automatic webhook registration on start-up, the linking steps, and what each failure looks like | None |
| 10:42 | How to test the webhook | Three layers: `getMe` and `getWebhookInfo` against Zalo, a `curl` that pretends to be Zalo calling `/zalo/webhook`, and the real link while watching the log | None |
| 11:02 | Can one Zalo account be relinked to test other roles | Yes, after `/stop` or Stop Zalo notices. A notice is marked "not linked" at creation, so link first. Fake chat IDs via `curl` let other accounts count as linked | None |
| 11:04 | Does the bot have help, schedule and monthly check-in commands | Only the link code and `/stop`. Proposed `/help`, `/lich` and `/chamcong`, one line per shift, because Zalo shows plain text | None |
| 11:06 | Build all three, one line per day | `ZaloCommands`: `/lich [tuantoi]`, `/chamcong [thangtruoc]`, and the command list for `/help` or anything else; capitals, spaces and Vietnamese marks ignored; own data only, nothing for an unlinked chat. `SafetyScoreService.inMonth`; bot texts in both languages; 6 tests; user guide 6.6 (and the detection repo's copy); a "Team decisions" row in the traceability table | Not committed |
| 11:40 | Apply two CodeRabbit review comments | Both still valid. Check-out notices wait for their hours: `ZaloDispatcher` holds a CHECK_OUT back until `CheckOutHours.fill` has run, `fill` covers older waiting notices, handles each notice on its own, and gives "-" with a log warning when the hours can't be worked out. Long `/chamcong` replies are split at line ends into messages of at most 2,000 characters, totals first. 3 integration tests and `ZaloCommandsTest` (4) | Not committed |
| 13:43 | Docker is on, run the tests | `ZaloNoticesTest` 26 of 26; full suite 402 of 402 after fixing one test | Not committed |
| 13:53 | Write today's chat log and transcript | This update and `transcript/24-11-2026.md` | Not committed |
| 23:52 | The Zalo check-in and check-out messages should name the shift they start and finish, with its date and time, then write the logs | V24 (`shift_label()`, check-in and check-out triggers rewritten, older notices relabelled): "Chấm công vào lúc 07:52, bắt đầu ca T3 24/11 08:00–17:00." and "Chấm công ra lúc 17:05, kết thúc ca T3 24/11 08:00–17:00, số giờ làm N.NN.", with "(không có ca)" for a visit with no shift. Texts in both languages, `NoticeText` picks the no-shift wording, 2 new tests plus 2 updated, user guide 6.6, detection-app contract. The code was already in the working tree; this session checked and tested it | Not committed |
| 00:05 (25 Nov) | Make check-out complete the shift too | V25: a trigger on `check_in` marks the visit's SCHEDULED shift COMPLETED on any real check-out (gate, typed-in visit, correction, approved missing check-out), with the version raised; not for the system's close of a stale visit, a cancelled shift or a locked month. Shifts already checked out of are completed by the migration. The hourly job stays for shifts nobody checked out of. Coming back during the shift still attaches to it. 2 tests; shift page wording, user guide, traceability, database design | Not committed |

### 2026-11-25

Nothing from this day is committed yet.

| Time | What the developer asked for | What was produced | Commit |
|---|---|---|---|
| 11:02 | Check Report 4 (the Software Design Document) and its diagrams against the code and the detection app | Report 4 stops at the 4 November design (V1–V9): its package diagram, about 20 class names, the data dictionary (19 tables, where the schema has about 30), triggers, routes, authorization matrix and report sheets are out of date, and its detection app is the old Docker, EasyOCR and burst-alert design. The diagrams are draw.io drawings, not PlantUML from `03_implement`. Still right: the stack table, deployment and authentication | None |
| 11:21 | Check the new Report 3 against the code, title by title; the licensing is out of the detection app | Licensing confirmed gone. Diffed against the previous Report 3 from git: only 3.1.5 (the ERD, now 24 tables with columns), CR-23, the site-wide 5-minute late tolerance and two wordings changed, so every other title is as traced in `04_testing/SRS-TRACEABILITY.md`. The ERD doesn't match the schema: tables the SRS removes still existed, `zone.code` and the bonus percentages were missing, and many names and shapes differ. The new file's cover says September and its Record of Changes lost the 23/11 row | None |
| 11:30 | Write V26 and test it, then rewrite `docs/database-design.md` | V26: drops `ppe_vest`, `ppe_linking`, both `ppe_linking_id` columns, `safety_alert` and `team.late_after_minutes`; a fixed `zone.code` (GATE-IN, GATE-PPE, AREA-A …) given by a trigger and never changed. The alert endpoint and the vest and alert classes removed; the Zones page shows the code. Schema checks, a new zone-code test, the detection app's integration check updated. `docs/database-design.md` rewritten from the migrated schema (27 tables, 7 domains, a section on differences from the SRS); contract, deployment and traceability docs updated | Not committed |
| 11:51 | Write today's transcript and chat log, and one design document per title | `transcript/25-11-2026.md` (generated from the session file) and this update. New `docs/system-design.md`, `docs/detailed-design.md`, `docs/integration-design.md` and `docs/security-and-report-design.md`, following Report 4's titles and the code; `docs/database-design.md` was already rewritten. The detailed design points to the `03_implement` diagrams and says which are out of date (ATT, SCH, PPE, TKT, NTF, RPT). `docs/tech-stack.md` corrected (V26, 27 tables, no alert API). Found that team leaders can't see their own violations | Not committed |
| 12:34 | Make the design documents into Report 4 as Markdown, and check the `02_design`, `03_implement` and `04_testing` files brought from the detection repo | `Report4_Software_Design_Document.md`, assembled from the design documents in the docx's structure (Record of Changes, sections 1 to 5, renumbered, with a deployment diagram, the local outbox and file storage sections). The 38 detection files are identical to the detection repo's and no ERP file was replaced. The detection ADRs are current (vest decisions superseded by ADR-123), but the `STEELSIGHT-DET-*` TDS and Test-Specs date from 9 to 17 November and still describe vest numbers, the Telegram notifier and burst alerts; the Test-Specs name files that no longer exist (`steelsight/gate.py`, `records/notify.py`, `tests/test_vest_fix.py`, `tests/test_config.py`) | Not committed |
| 12:50 | Write the test documentation from the Report 5 template, using another project's Report 5 as reference, and keep it as close to the codebase as possible | `Report5_Test_Documentation.md` with every figure from today's runs: the ERP suite with JaCoCo attached from the command line (402 of 402: 80 unit, 315 integration, 7 automated SRS workflows; 89.2% of lines, 70.6% of branches) and the detection app's pytest suite twice (166 of 167, then 167 of 167; the failure is a flaky test in the unused vest OCR code). `04_testing/Report5_Unit_Test.xlsx` and `04_testing/Report5_Test_Report.xlsx` generated from the results by the new `scripts/report5_test_cases.py`, with 15 manual system scenarios marked not run. Seven defects listed, two open: team leaders can't see their own violations (a test asserts the old behaviour) and the clock-dependent workflow test | Not committed |
| 13:35 | Fix the team leader permission | V27 gives the MANAGER role `VIOLATION_READ_OWN`, so team leaders see their own violations and score and can appeal (SRS 3.1.3); reads stay own-only. `WorkerAccessPagesTest.managerStillCannotOpenViolationPages` replaced by a test of the SRS behaviour, including refusal of someone else's violation and the review pages. Full suite 402 of 402. Report 5 (DEF-01 closed, M11), the case sheets, the authorization matrix, database and system design, tech stack, traceability, Report 4 and the user guide updated; the user guide's late check-in rule (still the old per-team 15 minutes with make-up time) corrected to the fixed 5 minutes | Not committed |
| 14:07 | A step-by-step tutorial of every SRS workflow, with edge cases; and a button that generates a free employee code, drawing again if it's taken | **Generate** on New account (htmx, Admin only): `EmployeeCodes` gives the role's prefix and five random digits (WRK-48213), checks every account including deleted ones, draws again up to 50 times, then asks for a code by hand. `EmployeeCodesTest` (4, with scripted draws to force collisions) and a page test; full suite 407 of 407. `docs/workflow-tutorial.md`: setup, the 14 workflows with exact buttons, fields and messages, a "Try this" table of edge cases per workflow, and a rules reference. The user guide's account section rewritten (it allowed dots and underscores, an optional phone and WebP, and had no consent tick), and three unused late-rule labels removed | Not committed |
| 14:17 | A version to test run locally | `compose.local.yaml` (PostgreSQL 17 + pgvector on 55434 and the ERP built from the folder, local profile and storage, containers named `steelsight-local-*` so the developer's stopped `steelsight-db` on 55433 is left alone), `scripts/local.sh` (up, seed, logs, psql, stop, reset) and `docs/local-run.md`. `scripts/dev-sample-data.sql` rewritten for V27: it no longer ran (old category names, violations written as decided, steel-mill zones); now the tutorial's site with 13 accounts, visits with each attendance result, violations in every review state, an appeal with HSE asked, one ticket per category and pending overtime. Run for real: built, started, seeded; 5 roles signed in over HTTP and 17 pages checked; Generate answered in the running app. The stack is left running | Not committed |
| 17:44 | Make Report 4 diagram-first, like the reference report | `scripts/report4_diagrams.py` writes 61 PlantUML diagrams to `docs/diagrams/src` (architecture, layers, both package diagrams, deployment, a domain overview and an ERD per domain from `docs/database-design.md`, and per feature a class diagram whose operations and fields come from the Java source, sequence diagrams and state diagrams); `scripts/render-diagrams.sh` renders them; `scripts/report4_build.py` rebuilds `Report4_Software_Design_Document.md` with numbered figures, Image Detail links, class specification tables and numbered tables. PlantUML was downloaded with the developer's one-off permission and a test diagram rendered; the 61 figures still have to be rendered | Not committed |

## Decisions made by the developer

### 2026-11-04

- Product name is Steelsight. GitLab is the main repository and GitHub is temporary.
- Safety concern is a ticket category handled by HSE, with an optional zone and photo.
- Arriving up to 15 minutes late isn't tagged late, and managers or admins can edit that threshold.
- Late time is made up at the end of the shift, capped at the threshold: 17:15 on a 08:00 to 17:00 shift.
- Late and early rules live in the service layer, not stored in the database.
- Check-in corrections stay and get a version column.
- The missing camera image in the ERP is acceptable for now.
- Sample data is for local testing and is committed with the logs.

### 2026-11-05

- Keep server-rendered pages with `htmx` and add Tailwind, rather than moving to Next.js.
- Odoo is the design reference, with dark and light mode and Vietnamese and English.
- Safety scores apply to workers only. Each violation loses points, with a warning below one threshold and a pay deduction heads-up below a lower one. The app does no payroll.
- Required PPE in the reminder is a hard hat and a vest.
- Managers can view every worker, but only schedule and resolve tickets for their own team.
- HSE can see every worker's safety score.
- A "shift" in the HSE view is a time slot across the site, because one overview camera covers the working area.
- The detection software counts violations per minute, decides when it's critical, and calls the ERP. This avoids repeatedly querying the database.
- Remove the password default from `application.yaml` and commit the logs.
- Move to Supabase and build a GitHub to Render pipeline, after asking Claude whether to do either.
- GitLab CI/CD is the pipeline in the docs, and GitHub Actions only runs the tests while the code is on GitHub.
- Only admins upload or replace face photos, because they're biometric data.
- Account management extends the Team page rather than adding a separate section.
- New accounts, and accounts whose password an admin resets, must choose their own password at the next sign-in.
- Managers can't edit anyone's information. They only organise shifts for the teams they run, and keep the late check-in rule.
- Each team has one leader, a manager leads at most one team, and admin decides who is in each team.
- A team leader exports a monthly Excel report of their own team for accounting, and admin can export every worker.
- PPE disputes and safety concerns go to HSE, and every other ticket, including a new leave request category, goes to admin only. Team leaders no longer see or handle tickets.
- The report counts no-shows: past shifts with no check-in.

### 2026-11-09

- Add rate limiting and the fix for silenced alerts now, and leave request signing for later because it needs a change in the detection codebase.
- Fix the session, attendance history and Swagger findings first, and leave the other four low findings for later.
- Show the attendance change history on the visit edit page.

### 2026-11-17

- A worker can have more than one face photo, so a turned face at the gate can still be recognised (ADR-026).
- The ERP is changed for this, because the detection app can't get more photos any other way.

### 2026-11-21

- Only auto-close and the ticket categories change to match the SRS. The SRS itself stays as it is, so the code can be compared against it.
- Both ticket category lists are merged.
- No recorded-video analysis: the footage stays on the switch, for security.
- Workers get Telegram messages about their check-ins and their monthly schedule. Not every worker uses Telegram, so it's optional.
- The zone-to-team fallback for violations without a vest number is left out for now.
- At check-in, the gate says the worker's name, or what PPE they're missing.

### 2026-11-23

- Telegram is removed; Zalo is the only chat app, as in the SRS.
- Match the SRS as closely as possible. When the developer remembered UC-24 as splitting tickets between HSE and the Admin, the SRS text (every ticket to the Admin) was followed.
- Zones have several cameras and belong to one team. Telling people apart by vest number was unreliable.
- Shifts complete automatically. Manual steps are only for things the Admin has to decide.
- The phone number is required for new accounts; the database column stays nullable.
- The base Zalo notices are check-in and check-out.

### 2026-11-24

- The bot gets commands to read your own schedule and attendance, a team addition beyond the SRS.
- A check-out completes its shift at once, not only when the shift ends (25 November, 00:05).

### 2026-11-25

- The design documents go in `docs/`, one per title, and point to `03_implement` for the class and sequence diagrams.

## Decisions suggested by Claude and accepted by the developer

### 2026-11-04

- Close a visit with no check-out after the day its shift ends, not 2 hours after the shift.
- Record the shift's end as the estimated check-out, or midnight when the visit has no shift.

### 2026-11-05

- The score returns to 100 on the 1st of each month, in site time.
- The same worker and violation type loses points at most once per 30-minute block, because it wasn't known whether detection already limits repeats.

The rest of this list was in the plan the developer approved:

- The score is calculated from violation rows rather than stored, so the reset needs no scheduled job and an overturned violation gives its points back.
- Each counted violation costs 10 points. Warning is at 70 or below and danger at 40 or below, all editable in `safety_policy`.
- A database trigger creates worker notifications, because detection writes directly to Postgres.
- Notifications are in-app only, and their text is rendered in the reader's language.
- The HSE view ranks time slots by violations per 100 worker-hours, so large and small slots compare fairly.
- The detection API uses a key in `X-Api-Key`, refuses every call if no key is set, and treats a repeated `eventId` as a retry.
- HSE gets at most one alert notification per 15 minutes.
- Tickets from people with no team go to admin only. Managers lose access to other teams' tickets.

From the deployment plan the developer approved on 6 November:

- Treat the site as ready for a capstone demo with sample people only, not real workers' data.
- Run the tests in GitHub Actions and deploy through a Render deploy hook, leaving scanning and the image registry until the move to GitLab.
- Keep ticket photos in a private Supabase bucket, served through the ticket page so its permission checks apply.
- Lock sign-in for 15 minutes after 5 failures from one address on one employee code.

### 2026-11-09

- Not adding JWT. The developer asked for a suggestion; the advice was that it adds nothing for one machine calling one endpoint, and that server sessions suit a server-rendered app better. The developer moved on to the security review instead.
- Removing the Swagger library rather than switching it off, because nothing used it.

### 2026-11-17

- Each angle is its own `face_embedding` row with a `pose`, rather than extra vector columns or an averaged vector, so each photo keeps its own status and error (ADR-026).
- The CodeRabbit findings were checked against the code first; all three code findings were real.

### 2026-11-21

- Each worker links their own chat, rather than one group chat per team.
- The detection app sends gate check-in and check-out messages, because the ERP on Render may be asleep; the ERP sends visits entered by hand and schedules.
- A chat is linked with a one-time code from the home page.
- A month's schedule is sent with a button; after that, only changes in that month are sent, so building a month doesn't send a message on every save.

### 2026-11-23

- For a violation in a work zone, HSE still confirms one person; that zone's team is listed first. Scores stay individual.
- Each zone has one owning team (`zone.team_id`), set on the Zones page.
- At the gate, the SRS rule stays: the suggestion is whoever CAM-01 recorded within 30 seconds, and the gate zone has no team.
- The ERP changes stay compatible with the detection app: nothing it writes was dropped or renamed. This was later superseded when the detection app's folder was found and updated too.
- Keep the 6-digit link code and add a QR code, rather than linking by phone number.

### 2026-11-24

- The bot answers `/lich` and `/chamcong` with one line per shift instead of a grid, since Zalo shows plain text.

### 2026-11-25

- V26 removes what the SRS removes (vest tables, burst alerts, the per-team late tolerance) and adds `zone.code`.

## Decisions made by Claude and reported to the developer

### 2026-11-04

- The late threshold is stored per team, so a manager only affects their own people.
- No generic `BaseService` or CRUD controller, because every action has its own permission rules.
- A worker tagged late who also leaves before the required time gets both tags.

### 2026-11-05

- No log lines inside each service, because the request line already shows who did what.
- Passwords, form contents and query strings stay out of the logs.
- Added a limit of 20 failed sign-ins per employee code across all addresses, beyond the plan, because a client can fake its address. The trade-off is that a stranger can lock a worker out for 15 minutes.
- In the diagram, removed the arrow for browsers loading photos straight from Supabase through signed URLs, because the ERP now serves photos itself so its permission checks apply.
- Face photos get their own private bucket and local folder, apart from ticket photos.
- Old face photo files are removed only after the change commits, so a failed change never loses the photo in use.
- Passwords need at least 8 characters and at most 72 bytes, the most BCrypt uses.
- An admin can't change their own role, deactivate or delete themselves, so those sections are hidden on their own account.
- Admin's ticket queue shows admin's own categories, with an "All tickets" view for HSE's too.
- The report defaults to last month, uses site time, and counts a check-in for a shift when it's linked or overlaps it, because detection doesn't link them yet.
- fastexcel writes the report, because it streams and suits Render's 512 MB.
- A team can only be deleted once nobody is in it.
- GitHub's container registry holds the image for now, pushed with the workflow's own token, so no registry password is stored.
- Recommended making the image package public, because Render's support for private `ghcr.io` images wasn't confirmed. The code is public and the image has no secrets.
- Commits use the developer's name and email from the repository's history, because no git identity is set in WSL.

### 2026-11-06 (design documents)

- Split the code into 9 modules: IAM, Scheduling, Attendance, PPE & Safety, Ticketing, Notification & Detection API, Reporting, Photo Storage and Platform. The page controllers in `web/` went to the module whose service they call.
- Put the documents in `02_design/`, `03_implement/` and `04_testing/`, the folders the templates refer to, with one ADR file per decision.
- Kept the templates' headings as written, Vietnamese and English, and wrote the content in English like the other docs.
- Adapted the parts that assume TypeScript, Prisma and JWT: Flyway SQL for schemas, Java for interfaces, and session and CSRF form endpoints for the API tables.
- The app returns no error codes, so the error tables use codes as reference IDs only, with the real Vietnamese messages from `messages_vi.properties`.
- Performance targets are marked *proposed*, because nothing has been measured.
- There is no SRS, so the business rules come from what the code enforces and the user stories from `docs/user-guide.md`. Both are marked unconfirmed.
- The ADRs are recorded after the fact. Deciders are the commit author and Claude, and DPO sign-off is left pending everywhere.
- Laws are cited by name only, without article numbers, rather than guessing them.
- The Test-Specs say the tests were written alongside the code, not test-first, so the Red Gate was never run. Gaps get 🔴 test cases that aren't written yet.

### 2026-11-09

- Changed the first suggestion of putting the rate limit before the key check. `X-Forwarded-For` can be faked, so that order could have locked out the real detection server. The key is checked first, and wrong keys are counted separately.
- Limits of 60 calls a minute with the right key and 10 wrong keys a minute per address.
- Each registered camera has its own quiet period, and every other camera code shares one, so inventing codes can't flood HSE.
- An alert detected over 1 hour ago or over 5 minutes ahead is kept without notifying anyone. A wrong clock on the detection machine would silence alerts, which is logged as a warning.
- `violationCount` above 100,000 and `windowSeconds` above 86,400 return 400.
- A password change ends the person's other sessions, and an admin reset ends all of them, including the admin's own if they reset themselves.
- The attendance history can be seen only by people who can correct visits (`CHECKIN_EDIT`). It starts with this change, because older corrections had already overwritten their values.

### 2026-11-16

These came from the request to check every user error, and the developer hasn't reviewed them yet.

- The 5 MB photo rule is checked by the app, so the form shows the problem with what was typed. The container limit became a 20 MB ceiling (25 MB per request), and Tomcat discards up to 100 MB of a refused body instead of dropping the connection.
- The browser checks the photo size as soon as one is chosen, using the same limit in a `data-max-bytes` attribute.
- Uploads over the ceiling get the "Photo too large" error page, not the form. Sending them back to the form would need flash handling outside Spring MVC, and the browser check already catches them.
- Spring Boot's error controller is replaced, so no page can show JSON again; only addresses under `/api/` get JSON.
- A form whose security token no longer matches says "This page has expired" instead of "Not allowed", because that almost always means the session ended while the page was open.
- A failed POST goes back to the page it came from, using only the path and query of the Referer. Without a Referer it shows the error page.
- A form value that can't be read is a mistake (400), not a missing page. A bad id in the address, or in a GET filter such as `?week=nonsense`, is still 404.
- A stale edit keeps the form and what was typed. This amends ADR-006, which had accepted losing it.
- The report download sends a month that can't be exported back to the Team page with the reason, because Firefox desktop shows the month field as plain text.
- Line breaks count once when checking text length, as the browser counts them for `maxlength`.
- The language switch now keeps the page's query, such as the week shown, because it shares the new back-to-page helper.
- Recorded these as a new `ADR-025` rather than rewriting ADR-006, and recorded old values in each document's changelog, following the documents' immutable history rule.
- Not changed, and reported instead: ids that don't exist (only reachable by editing the HTML) still show "Page not found"; shifts have no maximum length; any database conflict when saving a check-in is reported as "already checked in".

### 2026-11-17

- The poses are fixed slots, FRONT, LEFT and RIGHT, rather than a free list of photos, so admins are guided to the angles that help.
- LEFT and RIGHT need a FRONT photo first, and the team list shows the FRONT photo's status.
- The row lock was also added to `softDelete`, which CodeRabbit didn't mention: without it, a delete could still miss a photo uploaded at the same moment.
- `requireForUpdate` skips soft-deleted users like `require` does, so deleting an already deleted account still gives 404.
- The report page fix is in an uncommitted change that wasn't part of this session; it only matters if the app gets a context path.

### 2026-11-21

- `V15` keeps the function's signature but drops and recreates it with its grants, as ADR-013 requires. A check-in after the shift has already ended gets 2 hours from check-in. Visits with no shift still close at midnight, which the SRS doesn't cover.
- The 2-hour rule brings back the cut-off for overtime that V7 removed; noted as an amendment in ADR-013.
- `V16` maps three SRS categories to existing names (PPE dispute, Safety concern, Leave request) and adds the other three under Admin, with "Other" last.
- Telegram messages are in Vietnamese (SRS CR-01), go out only after the change commits, on one background thread; a failed message is logged and dropped.
- The chat is written with plain SQL, so linking Telegram doesn't change `app_user.version` and make an admin's open form go stale.
- The webhook checks Telegram's secret header, is exempt from CSRF, and answers in the response body, so the ERP makes no call of its own. Only private chats are handled.
- Codes last 30 minutes and work once; linking a chat unlinks it from any other account.
- The Telegram card appears for people who have their own shifts; the schedule button for people who plan shifts, for the month most of the shown week is in.
- The schedule button went on the Shifts page, the planning page, not the worker timetable page named in the plan at 13:31.
- Telegram isn't in the SRS (it was removed there); it was built because the developer asked.

### 2026-11-23

- Telegram is removed with a new `V19`, not by editing V17, because a database may already have run V17.
- The detection app writes straight to the database and the ERP may be asleep, so the review, alert and check-in rules are database triggers: a person the detection app names becomes a suggestion, each violation alerts the HSE Officers on duty (or every HSE Officer and Admin), and a check-in is attached to a shift starting within 60 minutes.
- The burst-alert endpoint still accepts calls but notifies nobody (CR-02), so the old detection app's queue doesn't retry forever.
- Every notice is saved in the ERP first, then sent on Zalo in Vietnamese, with retries after 1, 5 and 15 minutes.
- The scheduled jobs record each run in `job_run`, so each runs once per scheduled time and a missed run is caught up once. They're off in tests.
- The categories outside the SRS's five, including "Safety concern", are hidden from new tickets, not deleted.
- The team late-rule setting is removed, because BR-31 fixes the grace period at 5 minutes.
- Automatic completion bumps the shift's version, so a form opened earlier is refused as stale, and it leaves locked months alone (BR-70).
- The QR is inline SVG drawn on the server, with no external script or image request. It's hidden when `ZALO_BOT_LINK` isn't set.
- Check-in and check-out notices are sent only for the detection app's writes. Visits entered by hand have their own notice, and corrections, approved missing check-outs and automatic closes send nothing.
- `MONTH_SCHEDULE` and `APPEAL_DECIDED` are defined but never sent; they were left alone.

### 2026-11-24

- The commands are `/lich`, `/lich tuantoi`, `/chamcong` and `/chamcong thangtruoc`, with English aliases. Anything else from a linked chat gets the command list; an unlinked chat only gets how to link.
- The statuses use the attendance pages' own words (`flag.*`) and calculation. Shifts that haven't started are left out of `/chamcong`.
- Replies go straight back to the chat and aren't saved as notices.
- `/chamcong` puts the totals, score, provisional note and link before the per-shift lines, so a split month has them in its first message.
- A check-out notice whose hours can't be worked out is sent with "-" and a warning in the log, rather than waiting for ever or being dropped.
- `CheckOutHours.fill` has no shared transaction, because a failed query would abort it for every notice after.

### 2026-11-25

- Where the schema and the SRS ERD differ only in names or shape, the ERD should change, since the code is tested. The list is in section 4 of `docs/database-design.md`; the developer hasn't decided yet.
- The database gives the zone code, in a trigger, so the ERP, the site setup script and the tests that insert zones get the same codes. Hibernate reads it back (`@Generated`).
- The `/api/**` key check stays with no endpoint behind it, in case an API comes back; the key is marked optional in `docs/deployment.md`.
- The bonus reductions stay fixed at 2/4/6% in `SafetyLevel`, since the developer didn't choose; the SRS puts them in `safety_policy`.

## How the work was checked

### 2026-11-04

- The full suite last ran on `14e030d` before one database check was fixed: 143 tests passed against a real PostgreSQL with pgvector. That check was then rerun on its own and passed.
- 39 checks of the HSE and manager flows ran against the running app before commit `9d5f3d2`.
- Sample accounts for each role signed in, and their pages showed the sample data, including the late and early tags and paging. The inactive account was refused.
- The full suite passed with 149 tests while building the grid; only a CSS change for phones came after it.
- Main HSE, manager, admin and worker pages were screenshotted in a real browser on desktop and phone, and the grid in dark mode too. No page scrolled sideways. The check found two phone problems in the grid, both fixed.

### 2026-11-05

- The full suite passed with 192 tests and no failures at 16:18. No source file changed between that run and commit `0f3611f`.
- The logging was also checked in the real app, on a separate port. The error reference page and stack trace were only covered by tests.
- **Not checked in a browser by Claude:** both sessions ended before the screenshots were taken. That covers the redesign, dark mode, Vietnamese, phone layouts, the worker grid, and the new worker, safety and notification pages. These have automated page tests only.
- The detection API was covered by automated tests. The planned manual check against the running app wasn't recorded as finished.

### 2026-11-06

- The deployment code compiles, and 21 tests that need no Docker pass: the sign-in limit, Supabase storage against a mock server, storage selection and translations.
- The config files parse, and the old password isn't in any tracked or new file.
- **Not run:** the full test suite with the new sign-in and health tests, `docker build`, migrations on a real Supabase project, a real photo upload, and the browser check. Docker Desktop wasn't running.
- Account management compiles, and the 26 tests that need no Docker pass: storage for both albums, sign-in limit, storage selection and translations.
- Once Docker was running, the full suite passed with 244 tests at 15:31, on a real PostgreSQL with migrations `V10` and `V11`. It covers account management, photo cleanup after commit, the forced password change, team setup, ticket routing, and the Excel report read back with its figures checked.
- The first full run found 11 failures, all fixed before the passing run. Among them were a missing admin permission for the team report, and tests that still expected managers to handle tickets or edit people.
- **Not checked in a browser:** the account, team setup and report pages, and opening the `.xlsx` in Excel. They have automated page tests only.
- The image built locally at 508 MB. With the `prod` profile, a 512 MB memory limit and half a CPU, it started in 20 seconds, used 242 MB, and ran all 11 migrations on an empty database. Health showed `UP`, other actuator pages stayed closed, the session cookie was `Secure`, the first admin was created and signed in, and the detection API refused a call without a key. The test container and its database were then removed.
- **Not run yet:** the GitHub workflow itself, because it hasn't been pushed.
- For the design documents, the 39 tests that need no database ran again on `f005861` and passed: attendance rules, sign-in limit, the three storage classes and translations. `JAVA_HOME` had to be set to the Linux JDK first.
- **Not run for the documents:** the 205 tests that need a database, because Docker isn't installed in this WSL. The Test-Specs give their status from the 15:31 run, since no source file changed after `94a2c3c`.
- A script checked the documents: no broken links, every BR, US and ADR reference is defined, the code blocks are closed, and all 244 test methods map to a test case.
- Expected results in the Test-Specs were checked against the real test code for the main cases, and several were corrected.

### 2026-11-09

- The full suite passed with 266 tests at the end of the day, on a real PostgreSQL with migration `V12`. It ran with the developer's uncommitted attendance and report changes in the working tree.
- New tests cover the rate limits, the quiet period per camera, delayed alerts, ended sessions after a password change or reset, the correction history, the history section and the removed Swagger routes.
- The security review read every page controller with its service and the SQL permission functions. A temporary test requested 15 routes as a worker: 13 returned 403 and the 2 Swagger routes returned 200. That test was then deleted.
- **Not checked in a browser:** the history section.
- **Not reviewed:** dependency vulnerabilities, the real Render and Supabase settings, and the detection codebase. No live attack test was done.

### 2026-11-16

- The app couldn't start against the database in `.env`, which points at the remote Supabase project, and the local Postgres needed credentials Claude didn't have. Instead the app was started against an empty throwaway Postgres in a temporary folder, with Flyway off, to test the paths that run before sign-in on a real Tomcat. 30 MB and 60 MB uploads got the 413 "Photo too large" page as HTML, a form without its CSRF token got 403 "This page has expired", a broken upload got the 400 page, and a failing `/api/` call got JSON. The first version of the fix gave a 500 on a broken upload, which was fixed and checked again.
- Once the developer started Docker, the full suite ran on the uncommitted working tree: 282 tests in 33 classes, 0 failures, 0 errors. The first run had 2 failures, both wrong test expectations (see below).
- The case of a 5–20 MB photo on the form needs a signed-in user and the full schema, so it was checked by `ErrorPagesTest` only, not on a real Tomcat.
- **Not checked in a browser:** the browser's size message on the photo inputs, the redirect-back notices, and the expired-form page.

### 2026-11-17

- `./mvnw test-compile` while Docker was off; `TranslationsTest` on its own.
- V14 applied to a throwaway Postgres in WSL with every migration, through the detection app's `tests/erp_integration_check.py` as the `steelsight_detection` role: FRONT and LEFT rows both load, the role can't change a pose, and one row per worker and angle is enforced.
- The full suite with Docker: 284 tests, 0 failures.
- The new edit page section was rendered by the page tests but not looked at in a browser.

### 2026-11-21

- The database design document was compared with every migration, V1 to V14.
- The SRS review read the code and the SRS only; neither app was run.
- Full suite with Docker: 285 tests after V15 and V16, then 296 after Telegram, 0 failures.
- `TelegramNoticesTest` (10 tests) uses a mock bot. The tests roll back, so they run the after-commit step by hand, and check the exact Vietnamese message text.
- The detection app's `tests/erp_integration_check.py` applied V1–V17 to a real Postgres (pgserver) and ran as `steelsight_detection`: 35 of 35 checks, including the new V17 ones.
- Not checked: the new pages in a browser, a real Telegram bot, webhook registration, or the live gate.

### 2026-11-23

- Each migration was first run in order against a throwaway pgvector container, then through the full suite with Testcontainers.
- Full suite: 299 after Telegram and tickets, 293 after the overhaul's test updates, then 380 with the SRS test classes, 381 after automatic completion, 385 after the QR and 388 after V23, with 0 failures. `mvn verify` passed at the merge check.
- `schema_checks.sql` was rewritten for the new rules and run as the detection role, which also showed that role can't read notifications.
- A generated QR was rendered to an image and decoded with OpenCV; it gave back the exact bot link.
- The detection app's `tests/erp_integration_check.py` ran against V1–V22, and then V1–V23, in Docker as `steelsight_detection`: 21 of 21 checks each time.
- Not checked: the real Zalo bot and webhook, the Supabase Flyway history (unreachable from WSL), the new pages in a browser, a QR scanned with a phone, or the desktop app with real cameras.

### 2026-11-24

- The Render failure was traced through the code and GitHub's public API for the CI runs. Render's own dashboard couldn't be checked.
- `ZaloNoticesTest`: 26 of 26 with Docker. Full suite: 402 tests, 0 failures, after one test fix.
- `ZaloCommandsTest` (splitting and command matching) needs no database.
- Not checked: the commands with the real bot, or Render after the image fix.
- V24: `ZaloNoticesTest` 28 of 28, `ZaloCommandsTest` 4, `TranslationsTest` 3. Full suite (23:58 to 00:01): 407 tests, 1 failure, `SrsWorkflowTest.workflow124`, because it ran across midnight: its check-in "3 hours ago" was on the 24th and Team Attendance showed the 25th. Not caused by V24; it passed at 13:51.
- V25: full suite (00:05 to 00:09) 409 tests, 2 failures: `workflow124` again (midnight), and `ManagerPagesIntegrationTest.aFinishedShiftIsCompletedByTheSystem…`, which checked the old shift page wording. After updating that assertion, `ManagerPagesIntegrationTest` 27, `ZaloNoticesTest` 30, `SrsShiftsAndJobsTest` 17 and `TranslationsTest` 3 all pass.
- Not checked: the new wording with the real Zalo bot.

### 2026-11-25

- Report 3 was compared with the copy in git (`b6bbb9a`) by extracting the text of both and diffing; its ERD figures were read as images.
- Full suite: 402 tests, 0 failures, after fixing the new zone-code test.
- V26 on existing data: a throwaway pgvector database migrated to V25 with zones of every kind, a vest link, a visit and a violation using it, a team late setting and a burst alert, then V26. Codes came out GATE-IN, GATE-PPE, AREA-A, AREA-B and GATE-IN-2, every row survived, and the detection role could still read zones, write violations and visits, check out and close stale visits.
- The detection app's `tests/erp_integration_check.py` against V1–V26: 22 of 22.
- `docs/database-design.md` was compared with the migrated schema by a script: all 27 tables match column for column, with 52 foreign keys.
- Not checked: V26 against Supabase, the Zones page in a browser, or the desktop app's zone dropdown.
- The design documents were written from the code: roles and permissions from a migrated throwaway database, routes from the controllers, operations from the public methods, jobs from `JobScheduler` and `SiteJobs`, report sheets from `MonthlyReportWorkbook`. The package diagram was redrawn from the imports after the first draft was too tidy. Every relative link was checked.

## Problems during the work

### 2026-11-04

| Problem | Outcome |
|---|---|
| Flyway deadlocked while enabling row level security | Moved to `afterMigrate__enable_rls.sql`, with a test that every table has it |
| Long test runs in the foreground blocked the developer's shell | Later runs went in the background |
| A headless browser install failed | Used scripted HTTP checks, then a later install without system packages worked |
| Port 5433 was already taken on Windows, so the database container could not start | Moved to port 55433 |
| An older database check depended on the time of day after the auto-close change | Fixed before the commit was finalised |

### 2026-11-05

| Problem | Outcome |
|---|---|
| `./mvnw` failed in WSL because `JAVA_HOME` points at the Windows JDK | Ran with the Linux JDK 17 |
| Port 8080 was already in use | Checked the logging on a separate copy on port 8099 |
| `Locale.of` doesn't exist on Java 17, which stopped the build | Replaced with `Locale.forLanguageTag` |
| A layout fragment named `head` also matched the page's `<head>` tag, causing 63 test errors | Renamed it `pageHead` |
| After that rename, 4 tests failed on the wording of shift times | Restored the original wording |
| The first full run of the safety work had 2 failures and 1 error out of 192 | Fixed the tests and code, including tests whose manager didn't run the worker's team, which the new rule requires |
| The headless browser wasn't ready in either session | The sessions ended first, so the browser check wasn't done |

### 2026-11-06

| Problem | Outcome |
|---|---|
| `0f3611f` had put the local database password in `application.yaml` and on GitHub | Removed from the file in `bc53de5`. It's still in git history, and history wasn't rewritten |
| Docker Desktop wasn't running | Wrote the Docker-dependent tests and the image without running them, then ran them all once Docker was back that afternoon |
| The first full run of the account, team and report work had 11 failures | Fixed: a missing admin permission in `V11`, tests reading the database before changes were written, and tests still expecting managers to handle tickets or edit people |
| The script that builds the transcript was in a temporary folder that was cleared | Rewritten for `6-11-2026.md` |
| The Supabase database URL, pasted as shown in Supabase, stopped the app at start-up | Split into a JDBC URL, username and password; the guide now warns about it |
| `git push` from WSL failed with no GitHub sign-in | The commit is local; the developer pushes from Windows, where they're signed in |
| The first test run for the documents failed at once because `JAVA_HOME` wasn't set | Ran again with `JAVA_HOME` set to the Linux JDK 17 |
| Docker isn't installed in this WSL | Only the 39 tests that need no database ran; the rest use the 15:31 results |
| The first list of test names missed three methods with digits in their names, such as `comingAt0815…` | Listed them again; all 244 are in the Test-Specs |
| Some expected results were first written from memory, such as the safety score times and the result of two officers deciding at once | Checked against the tests and corrected |
| A message key and its Vietnamese text in the IAM error table were guessed | Replaced with the real strings from `messages_vi.properties` |
| Several documents changed on disk while they were being written, probably in the editor | Left as they were and not overwritten |

### 2026-11-09

| Problem | Outcome |
|---|---|
| `./mvnw` failed in WSL because `JAVA_HOME` points at the Windows JDK | Ran with the Linux JDK 17 |
| Docker wasn't running for the first alert test run | Only the 12 tests that need no database ran; the alert tests passed once the developer started Docker |
| A test helper used the wrong `zone` column | Replaced with the helpers in `PageTestSupport` |
| A missing `AppUser` import stopped the first build of the fixes | Added the import |

### 2026-11-16

| Problem | Outcome |
|---|---|
| `./mvnw` failed in WSL because `JAVA_HOME` wasn't set | Ran with the Linux JDK 17 |
| `.env` points at the remote Supabase database, and its URL didn't resolve | Not used. Ran against an empty throwaway Postgres instead |
| The local Postgres needed credentials Claude didn't have, and pgvector isn't installed for the system Postgres, so migrations can't run locally | Tested only what runs before sign-in on a real Tomcat; the rest by the test suite in Docker |
| Docker wasn't reachable at first | Ran only the tests that need no database, then the full suite once the developer started Docker |
| The filter sent 413, but the `DispatcherServlet` read the broken upload again while showing the error page, which gave a 500 for a broken upload | The filter records the failure, so Spring doesn't read it again |
| Spring Security's default handler doesn't record why it refused, so the expired-form message never showed | `SecurityConfig` sets a handler that records the reason |
| Stopping the app with `pkill -f` matched Claude's own shell and ended it | Stopped it by process id instead |
| A new test expected `https://evil.example//x` to be refused, but Spring 7 reads it as the local path `/x` | Safe; the test now checks for `/x` |
| An existing test expected `decision=BOGUS` to give 404 | Updated to the new 400 on purpose |
| A start-up error printed the `.env` database URL, including its password, in a command's output during the session | Command output isn't part of the transcript. The transcript and this log were searched for the password and the Supabase host, with no matches. The password has been seen in this session's output, so consider changing it |

### 2026-11-17

| Problem | Outcome |
|---|---|
| Docker wasn't running at first | Compiled only, then ran the full suite once the developer started it |
| `TranslationsTest` failed: 4 new messages had no Vietnamese translation | Added the 4 keys; the test passes |
| `SoftDeletableRepository.require` treats a deleted user as not found, but the first lock query didn't | The lock query filters `deleted_at IS NULL`, so behaviour matches |
| The stylesheet is built by Tailwind from the templates, and wasn't rebuilt after the Extra angles section was added, so its `mt-2` class has no CSS | Not fixed yet; run `npm run build:css` |

### 2026-11-21

| Problem | Outcome |
|---|---|
| `./mvnw` failed because `JAVA_HOME` wasn't set | Ran with the Linux JDK 17 |
| Docker wasn't reachable from WSL at first, so the database tests couldn't start | Compiled only, then ran the full suite once the developer started Docker Desktop |
| `TranslationsTest` failed: English must not have `msg.*` keys, because English shows the text written in the code | Removed the 5 English `msg.*` keys; the Vietnamese ones stay |
| Telegram messages wait for a commit, and the tests roll back | The tests run only the Telegram after-commit steps by hand, leaving Spring's own alone |

### 2026-11-23

| Problem | Outcome |
|---|---|
| Docker Desktop wasn't connected to WSL at first, and the local PostgreSQL needed an unknown password | Compiled and ran the tests that need no database; the developer turned on Docker at 13:25 |
| The Supabase Flyway history query failed: the host is IPv6-only and WSL can't reach it | Not run. V19 is safe either way; the check is still needed before merging |
| After the overhaul, 103 tests failed across 17 classes | Mostly outdated tests, updated. Real bugs fixed: Violation Detail crashed with no reviewer, a duplicated `pattern` attribute on the account form, time fields shown as "7:00 AM", a damaged image causing a server error, an `Unpaged` crash, grace-period pay counted from the check-in, a duplicate zone name breaking the page, and team leaders able to open Pending Overtime |
| The usage limit was reached at 14:51 | Resumed at 19:23; the full suite then failed to start until Docker was back |
| The compiled stylesheet was missing classes the new templates use | Rebuilt during the merge check |
| `List.getFirst()` doesn't exist in Java 17 | Used `get(0)` |
| The usage limit was reached again at 23:14, before the logs were written | Written on 24 November |

### 2026-11-24

| Problem | Outcome |
|---|---|
| Docker Desktop wasn't connected to WSL at first | Compiled and ran the tests that need no database; the developer turned on Docker at 13:43 |
| The new hold-back test reset the retry time on every delivery row; in the full suite, SKIPPED rows from other tests broke a check constraint | Uses the existing `makeDue(user)` helper, which only touches that worker's PENDING rows |
| The local `.env` has no Zalo settings, so the value behind the bad QR couldn't be seen | Explained how to find and set the right link |
| `JAVA_HOME` wasn't set in the shell, so the first test run didn't start and only old reports were left | Ran with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` |
| `SrsWorkflowTest.workflow124` depends on the clock and fails when the suite runs across midnight | Reported as an open item; not changed |

### 2026-11-25

| Problem | Outcome |
|---|---|
| The local database needed a password, and reading it as the `postgres` system user was refused | Read the schema from the migrations, then from throwaway Docker databases |
| `JAVA_HOME` pointed at a Windows JDK | Ran Maven with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` |
| Many tests and the sample data insert zones without a code, so a `NOT NULL` code broke them | The code is given by a database trigger instead of the ERP |
| The new zone-code test assumed only work areas hold AREA codes, but other tests change a zone's kind after adding it | The test skips every code already taken |

## Open items

- Nothing from 24 or 25 November is committed: the bot commands, the two review fixes, V24, V25, V26 (vest tables, burst alerts and the team late tolerance removed, zone codes), V27 (team leaders see their own violations), the rewritten `docs/database-design.md`, the new design documents and the logs, along with V23 from 23 November.
- Update Report 4 (the Software Design Document): it still describes the 4 November design. The new documents in `docs/` (system, database, detailed, integration, security and report design) follow the code and can be its source. Redraw its diagrams, or take them from `03_implement`.
- Update Report 3's ERD (3.1.5) from section 4 of `docs/database-design.md`, or decide to change the code instead. Also fix its cover date (September) and restore the 23/11 row of its Record of Changes.
- Decide whether the bonus reductions (2/4/6%) move into `safety_policy`, as the SRS says, or stay fixed in the ERP.
- Decide whether to remove the `/api/**` key check, now that no endpoint is left behind it.
- Make the desktop app's camera settings name the zone by `zone.code` (SRS 3.2.30), and check the Zones page in a browser in both languages.
- Run the 15 manual system scenarios of `04_testing/Report5_Test_Report.xlsx` (sheet "System manual") on the deployed build with the real cameras and Zalo bot, then the acceptance session; fill in the team's testing roles in Report 5, section 3.1.
- ~~Give the MANAGER role `VIOLATION_READ_OWN`.~~ Done in V27 on 25 November.
- Update the TDS diagrams in `03_implement` that the detailed design marks out of date (ATT, SCH, PPE, TKT, NTF, RPT), and the detection TDS and Test-Specs (`STEELSIGHT-DET-*`, now also in this repo), which still describe vest numbers, the Telegram notifier and burst alerts and name deleted files. ADR-105 doesn't mention the face scanner that SRS BR-27 to BR-29 use.
- Make `SrsWorkflowTest.workflow124` independent of the clock; it fails when the suite runs across midnight.
- Note in the SRS (3.2.10) that a shift is completed at check-out or when it ends, with no manual step.
- Fix Render: set the image URL to `ghcr.io/owochiii/steelsight_erp:latest` and deploy. Don't roll back to an image older than `febb2bf`, since the database is at V21 or later.
- Set `ZALO_BOT_LINK` to the bot's full `https://` link, then scan the QR again. If the bot token was ever in that variable, regenerate it.
- Try `/lich` and `/chamcong` with the real bot once deployed.
- Keep the local `.env` away from production while V23 is uncommitted, or a local run applies it to Supabase.
- Before merging to `master`: take a Supabase backup and run `select version from flyway_schema_history order by installed_rank;` from somewhere that can reach the IPv6-only host. If production is at V14, the merge runs V15–V23 against real data. Deploy the ERP before the updated desktop app.
- Test Zalo against the real bot: create it, set `ZALO_BOT_TOKEN`, `ZALO_BOT_LINK`, `ZALO_WEBHOOK_SECRET`, `ZALO_WEBHOOK_URL` and `APP_BASE_URL` on Render, link an account by scanning the QR with a phone, change a shift, and block the bot to see the retries end in FAILED. Set up the uptime ping (CR-13), or Render's sleep delays messages.
- Decide what should happen when someone with no shift checks in: today the visit is recorded with no pay and nobody is told.
- Update the SRS: zones with several cameras owned by a team (1.1.3, 1.4, BR-49), automatic shift completion (3.2.10 #10), BR-42's camera roles, and notes that BR-46 and BR-47 often give no suggestion. The list is in `04_testing/SRS-TRACEABILITY.md`.
- Decide whether to retire the "Safety concern" ticket category for good; it's hidden from new tickets.
- HSE no longer sees a worker's appeal reason unless the Admin uses "Ask HSE Officer".
- Rewrite `03_implement/STEELSIGHT-PPE-IMP-001_TDS.md`, which still describes the vest-link design (it has a note at the top).
- ~~Drop the `ppe_vest` and `ppe_linking` tables in a later migration, now that the desktop app no longer uses them.~~ Done in V26 on 25 November, with `safety_alert`.
- Check the new pages in a browser, in both languages: Review Queue, My and Team Attendance, Pending Overtime, Zones, Company Settings, Monthly Report, and the QR.
- Decide whether `Report3_Software_Requirement_Specification.docx` should stay in git; `b6bbb9a` added it.
- ~~The 21 November work isn't committed, in either repository.~~ The ERP part was committed in `b6bbb9a` on 23 November.
- ~~Set up Telegram~~ Telegram was removed on 23 November (V19). It was going to need: create a bot with @BotFather; on Render set `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET` and `TELEGRAM_WEBHOOK_URL`; put the same token in the detection app's `.env`.
- ~~Check the Telegram card on the home page and the schedule button on the Shifts page in a browser.~~ Telegram was removed on 23 November; the schedule button now sends on Zalo, and the stylesheet was rebuilt.
- ~~Decide whether Telegram goes back into the SRS, or stays an extra.~~ Removed on 23 November.
- ~~With V15, someone working more than 2 hours past their shift gets an estimated check-out that HSE must correct.~~ V22 follows BR-35 instead: the visit is flagged Incomplete and closed 4 hours after the shift, with no guessed check-out time.
- ~~The other SRS gaps from the 21 November review are still open (transcript, 13:10). The zone-to-team fallback is parked.~~ The 23 November work covers the SRS rules the ERP can enforce, and zones are now owned by teams; `04_testing/SRS-TRACEABILITY.md` lists what's left.
- The 17 November work isn't committed: V14, the extra face angles, the CodeRabbit fixes and the documents.
- Deploy V14 to Supabase (Flyway runs it on the next start), then upload LEFT and RIGHT photos for workers, ideally photos taken by the gate camera.
- Run `npm run build:css` so the Extra angles section's spacing (`mt-2`) gets its CSS, and check the section in a browser in both languages.
- The ERP's `camera` table is empty, so every violation the detection app sends is refused. Add the cameras (the SQL is in the 17 November transcript).
- Name the extra face photos in the consent record for biometric processing (gap G11).
- ~~Today's security work isn't committed.~~ Committed as `23e5929` on 12 November. `V12` and `V13` run on the next deploy.
- The 16 November error handling work (ADR-025) isn't committed.
- Check in a browser: the photo size message when choosing a large photo, a notice after a failed button, a stale edit keeping the form, and the expired-form page, in both languages.
- Decide whether a shift needs a maximum length.
- The `.env` database URL looks malformed (it didn't resolve as a host); check it if the app is meant to run locally against Supabase.
- The detection service must retry on 429, keep its clock accurate, and send camera codes that exactly match the `camera` table.
- Sign the detection API's requests with HMAC, which needs a matching change in the detection codebase.
- Remaining low findings from the review: no Content-Security-Policy, no second login factor for admins and the first admin isn't forced to change the password, sign-in lockouts can be triggered by anyone and reset on restart, and ~~no limit on photo uploads~~ (photos are limited to 5 MB by the app since 16 November, with a 20 MB ceiling).
- Run a dependency vulnerability scan; the app is on Spring Boot 4.0.0.
- Check the attendance change history in a browser, in both languages.
- The database password default was removed from `application.yaml`, but it stays in git history on GitHub through `0f3611f` and older commits. Change it anywhere it's used, and don't reuse it for Supabase.
- The deployment work isn't fully tested. The new tests that need no Docker pass (21, including translations), but the full suite and `docker build` need Docker Desktop running. Nothing has run against a real Supabase project or Render yet.
- `94a2c3c` and `f005861` are pushed and merged to `master` (PRs #1 and #2). Check the workflow ran, make the image package public, then create the Render service from the image, following `docs/deployment.md`.
- The design documents need DPO sign-off, the article numbers of Luật 91/2025 and NĐ 356/2025, and a review of the ADR deciders.
- Decide on the 12 gaps in `02_design/MODULE-MAP.md` §6, especially manual check-ins with no shift (G1), no consent record for face enrolment (G11), and forgotten check-outs staying open when detection is off (G7).
- Run the Red Gate from the Test-Specs on the critical test cases, and add JaCoCo if coverage should be measured.
- Answer whether the detection service notes move from `.env.example` into `docs/deployment.md`.
- Check the account, team and report pages in a browser, and open an exported report in Excel.
- A leave request doesn't cancel shifts, so an approved leave day still counts as a no-show unless admin cancels that shift.
- The detection service needs the face bucket name, and must process `PENDING` face photos from it.
- Check the redesign and new pages in a browser, on desktop and phone, in both themes and both languages.
- ~~The detection software needs its own change to count violations and call the alert API, and it needs `STEELSIGHT_DETECTION_API_KEY` set on both sides.~~ Burst alerts were removed (CR-02); the API went in V26 on 25 November.
- Show the camera image once Supabase Storage is added. A worker warned about their pay can't currently see the photo when disputing a violation.
- The detection service must call `close_stale_check_ins()` and set `shift_id` on check-ins.
- Managers who were already signed in need to sign in again for their menu to update.
- No-shows appear on the dashboard's roster for today, but not on the Attendance page.
- A worker who leaves and returns mid-shift gets "early" on the first visit and "late" on the second.
