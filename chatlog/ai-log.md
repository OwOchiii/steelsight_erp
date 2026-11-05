# AI usage log

How AI was used on the Steelsight ERP. The full conversations are in the transcripts for [4 November](../transcript/4-11-2026.md) and [5 November](../transcript/5-11-2026.md).

| | |
|---|---|
| Tool | Claude Code (Opus 5.5) |
| Dates | 2026-11-04 and 2026-11-05 |
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
| 00:09 (6 Nov) | Update this log and write the transcript | This update and `transcript/5-11-2026.md` | The log commit |
| Next message (6 Nov) | Remove the password default and commit the logs; asked whether to deploy, build CI/CD and move to Supabase | Password default removed, and an approved plan: fix deployment gaps, move to Supabase, add a small GitHub to Render pipeline | The log commit |

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

## Decisions made by Claude and reported to the developer

### 2026-11-04

- The late threshold is stored per team, so a manager only affects their own people.
- No generic `BaseService` or CRUD controller, because every action has its own permission rules.
- A worker tagged late who also leaves before the required time gets both tags.

### 2026-11-05

- No log lines inside each service, because the request line already shows who did what.
- Passwords, form contents and query strings stay out of the logs.

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

## Open items

- The database password default was removed from `application.yaml`, but it stays in git history on GitHub through `0f3611f` and older commits. Change it anywhere it's used, and don't reuse it for Supabase.
- Check the redesign and new pages in a browser, on desktop and phone, in both themes and both languages.
- The detection software needs its own change to count violations and call the alert API, and it needs `STEELSIGHT_DETECTION_API_KEY` set on both sides.
- Show the camera image once Supabase Storage is added. A worker warned about their pay can't currently see the photo when disputing a violation.
- The detection service must call `close_stale_check_ins()` and set `shift_id` on check-ins.
- A temporary `steelsight_check` database may still exist in the local Docker container, left from the unfinished browser check.
- Managers who were already signed in need to sign in again for their menu to update.
- No-shows appear on the dashboard's roster for today, but not on the Attendance page.
- A worker who leaves and returns mid-shift gets "early" on the first visit and "late" on the second.
