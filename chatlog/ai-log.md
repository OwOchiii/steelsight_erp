# AI usage log

How AI was used on the Steelsight ERP. The full conversations are in the transcripts for [4 November](../transcript/4-11-2026.md), [5 November, including the session that ran past midnight](../transcript/5-11-2026.md) and [6 November](../transcript/6-11-2026.md).

| | |
|---|---|
| Tool | Claude Code (Opus 5.5) |
| Dates | 2026-11-04 to 2026-11-06 (one session ran past midnight) |
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

## Open items

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
- The detection software needs its own change to count violations and call the alert API, and it needs `STEELSIGHT_DETECTION_API_KEY` set on both sides.
- Show the camera image once Supabase Storage is added. A worker warned about their pay can't currently see the photo when disputing a violation.
- The detection service must call `close_stale_check_ins()` and set `shift_id` on check-ins.
- Managers who were already signed in need to sign in again for their menu to update.
- No-shows appear on the dashboard's roster for today, but not on the Attendance page.
- A worker who leaves and returns mid-shift gets "early" on the first visit and "late" on the second.
