# Steelsight: Security and Report Design

This section describes how people sign in, what each role may do, how personal data
is protected, and what the monthly report contains.

## 1. Authentication and Session

| # | Item | Design |
| --- | --- | --- |
| 1 | Sign-in | Employee code and password. Passwords are 8 characters to 72 bytes, stored as BCrypt hashes (BR-02). Every failed sign-in gets the same message (BR-01) |
| 2 | Sign-in limit | `SignInLimiter`, in memory with Caffeine: 5 failures from one client or 20 for one employee code lock sign-in for 15 minutes (BR-04). A restart clears the counts |
| 3 | First sign-in | When the Admin creates an account or resets a password, `must_change_password` is set, and `PasswordChangeRequiredFilter` sends the user to `/account/password` until they choose their own (BR-03) |
| 4 | Session | Server session, 30 minutes idle (BR-05). The cookie is `SameSite=Lax`, and `Secure` in production (`application-prod.yaml`). A deactivated or deleted user can't sign in, and `UserSessions` ends their open sessions; changing a password ends the others |
| 5 | First Admin | `AdminBootstrap` creates one from `ADMIN_EMPLOYEE_CODE` and `ADMIN_PASSWORD` when there is none |
| 6 | CSRF and XSS | A CSRF token on every page POST, sent by htmx in a header. Thymeleaf escapes all output |
| 7 | Zalo webhook | No session and no CSRF; every call must carry the `X-Bot-Api-Secret-Token` chosen at `setWebhook`, or it is refused |
| 8 | API key | The `/api/**` chain needs the `X-Api-Key` and limits calls per minute (`ApiKeyFilter`, `ApiLimiter`). No endpoint is left there since V26 |
| 9 | Secrets | Render environment variables, the GitHub (later GitLab) masked variables, and the laptop's `.env`. The Supabase server key is only in the ERP |
| 10 | Transport | HTTPS for browsers, Zalo and Storage; TLS for PostgreSQL. The laptop opens no ports, and the desktop app's own server listens only on `127.0.0.1` |
| 11 | Logs | Request, error and sign-in logs share a request ID. Passwords, keys and tokens are never logged |
| 12 | Desktop app | Its own local accounts, stored as PBKDF2 hashes with a salt on the laptop, with a lock against guessing (BR-73). The gate screen needs no sign-in |

## 2. Authorization Matrix

Each role holds a set of permissions (`role_permission`), and the Admin holds every one. Spring Security
loads them as authorities, Thymeleaf hides the links and buttons a user can't use, and every request is
checked again in the service by `AccessPolicy`, including the record's scope. The table shows the
permissions after V27.

| Function | Worker | Team Leader (MANAGER) | HSE Officer | Admin | Permission |
| --- | --- | --- | --- | --- | --- |
| Create and change accounts, face photos, roles, teams | - | - | - | Yes | USER_MANAGE |
| View employees | - | Yes | Yes | Yes | USER_READ_ALL |
| Zones, cameras, company settings | - | - | - | Yes | USER_MANAGE |
| View own shifts and timetable | Yes | Yes | Yes | Yes | SHIFT_READ_OWN |
| View and plan the team's regular shifts | - | Own team | - | Yes | SHIFT_READ_TEAM, SHIFT_UPDATE_TEAM |
| Plan anyone, overtime shifts, pending overtime | - | - | - | Yes | SHIFT_READ_ALL, SHIFT_UPDATE_ALL |
| View own attendance | Yes | Yes | Yes | Yes | CHECKIN_READ_OWN |
| View team attendance | - | Own team | - | Yes | CHECKIN_READ_TEAM |
| View all attendance, record or correct a visit | - | - | Yes | Yes | CHECKIN_READ_ALL, CHECKIN_EDIT |
| View own violations and safety score, appeal | Yes | Yes | - | Yes | VIOLATION_READ_OWN |
| Review violations, view all, unsafe time slots | - | - | Yes | Yes | VIOLATION_REVIEW, VIOLATION_READ_ALL |
| Create a ticket, view own tickets | Yes | Yes | Yes | Yes | TICKET_CREATE, TICKET_READ_OWN |
| Read and comment on the team's tickets | - | Own team | - | Yes | TICKET_READ_TEAM |
| Read and comment on a ticket when asked for facts | - | - | When asked | - | (ticket_participant) |
| Handle and decide tickets, decide appeals | - | - | - | Yes, not their own | TICKET_READ_ALL, TICKET_UPDATE_ALL, TICKET_DELETE_ALL |
| Monthly report | - | Own team | - | Any team or the site | REPORT_EXPORT_TEAM, REPORT_EXPORT_ALL |
| Notifications, Zalo link | Yes | Yes | Yes | Yes | (any signed-in user) |

Team leaders were given VIOLATION_READ_OWN in V27 (25/11/2026), as the SRS (3.1.3) says: they work on
site and have a safety score, so they see their own violations and can appeal them. The permission is
own-only; reviewing and seeing everyone's violations still needs VIOLATION_REVIEW and VIOLATION_READ_ALL.

The ERP connects to the database as the table owner. The detection app's role, `steelsight_detection`,
is limited by column grants and row level security; its rights are listed in
[integration-design.md](integration-design.md), section 2.

## 3. Data Protection

- **Face data is biometric.** Only the Admin sets face photos, and only after the person's signed consent
  is confirmed (`face_consent_at`, BR-10, BR-11). The photos are in their own private bucket. When an
  account is soft-deleted, a trigger erases its face embeddings, and the ERP deletes its photos and
  unlinks Zalo (BR-07).
- **Photos are never public.** Face, ticket and evidence photos are fetched by the ERP after a
  permission check and streamed to the browser; the browser never gets a Storage URL.
- **The detection role sees only what it needs:** employee code, full name and face vectors, never
  passwords, emails, phone numbers or tickets.
- **Supabase's public Data API returns nothing,** because row level security is on for every table and
  the only policies are for the detection role.
- **Optimistic locking** (`version`) on accounts, visits, shifts, violations, tickets and company
  settings stops a stale form from overwriting a newer change.
- **History is kept:** every hand correction of a visit (`check_in_correction`), every review and appeal
  decision with who and when, every report export (`report_export`), and every scheduled job run
  (`job_run`).
- **Locked months don't change** (BR-70): shifts, visits, violations and scores of a month are frozen on
  the 2nd working day of the next month.

## 4. Report and Excel Design

The monthly report gives accounting the figures for pay and bonuses; Steelsight doesn't calculate pay. A
team leader exports their own team and the Admin exports any team or the whole site (BR-71). The file is
written by streaming with fastexcel, in the downloader's language, in Vietnam time, with hours to 2
decimals. A file made before the month is locked says "Provisional" above the header. Every export is
logged in `report_export`. Tests read the file back with fastexcel-reader.

| # | Sheet | Columns |
| --- | --- | --- |
| 1 | Site summary | Employee code, full name, team, shifts scheduled, shifts worked, absences, paid hours, valid overtime (h), pending overtime (h), times late, minutes late, times left early, minutes left early, Incomplete attendance, paid leave (h), unpaid leave (h), violations, safety score, safety level, bonus reduction % |
| 2 | Office summary | The same as Site summary up to unpaid leave, then reminders instead of the score columns, because OFFICE staff have no safety score (BR-56) |
| 3 | Violations | Detected, zone, violation type, status, employee code, full name, reviewer, appeal |
| 4 | Attendance | One row per visit: check-in, check-out, employee code, full name, shift start, shift end, method, Incomplete |
| 5 | Absences | One row per scheduled shift with no visit: shift start, shift end, employee code, full name, zone |

The safety score, level and reduction come from `safety_scores()`: live for an open month, frozen in
`monthly_score` for a locked one. Paid hours follow the attendance rules (late after 5 minutes, paid by
the shift frame, visits under 10 minutes ignored, half day for an unexplained missing check-out when the
company setting is on).
