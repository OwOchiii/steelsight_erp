# Steelsight: Integration Design

This is section 4 of the design (Report 4). It lists every interface between the parts of the system:
the routes of the web ERP, how the detection app works with Supabase, photo storage, the Zalo bot and
the cameras. Ports and protocols are in [tech-stack.md](tech-stack.md), section 2.

## 1. ERP Routes

The ERP serves HTML pages and htmx fragments to browsers. Every page route needs a signed-in session,
and every POST needs the CSRF token, which htmx sends in a header. Spring Security checks that the user
is signed in; the service then checks the permission and the scope (own record, own team, or all)
through `AccessPolicy`. A route a role may not use answers 403 and shows the error page.

| # | Method | Route | Who | Scope and notes |
| --- | --- | --- | --- | --- |
| 1 | GET, POST | `/login`, `/logout` | Everyone | Sign-in by employee code; locked for 15 minutes after 5 failures per client or 20 per code |
| 2 | GET, POST | `/account/password` | Signed in | Forced after an Admin chose the password |
| 3 | POST | `/language` | Everyone | English or Vietnamese, kept in a cookie |
| 4 | GET | `/` | Signed in | Home page for the user's role |
| 5 | GET, POST | `/team`, `/team/new`, `/team/{id}/edit`, `/team/{id}`, `/team/{id}/role`, `/team/{id}/team`, `/team/{id}/group`, `/team/{id}/password`, `/team/{id}/deactivate`, `/team/{id}/reactivate`, `/team/{id}/delete` | Admin | Accounts. A team leader sees only their own team's list |
| 6 | GET, POST | `/team/{id}/face`, `/team/{id}/face/{pose}`, `/team/{id}/face/{pose}/delete` | Admin | Face photos FRONT, LEFT and RIGHT, with consent |
| 7 | POST | `/team/{id}/zalo-code`, `/team/{id}/zalo-unlink` | Admin | Make a Zalo link code for someone, or unlink them |
| 8 | GET | `/workers`, `/workers/{id}` | Admin, HSE Officer, Team Leader | Read-only employee list and profile |
| 9 | POST | `/teams`, `/teams/{id}/leader`, `/teams/{id}/delete` | Admin | Teams and their leader |
| 10 | GET, POST | `/zones`, `/zones/new`, `/zones/{id}`, `/zones/{id}/team`, `/zones/cameras`, `/zones/cameras/{id}/remove` | Admin | Zones (name, team) and cameras |
| 11 | GET, POST | `/settings` | Admin | Company settings (half day for an unexplained missing check-out) |
| 12 | GET, POST | `/shifts`, `/shifts/new`, `/shifts/copy`, `/shifts/{id}/edit`, `/shifts/{id}`, `/shifts/{id}/cancel` | Team Leader, Admin | Leader: regular shifts of their own team, before Saturday 18:00; Admin: anyone, and overtime |
| 13 | GET | `/timetable` | Everyone with shifts | Own shifts |
| 14 | GET | `/attendance/mine` | Everyone | Own visits and their status |
| 15 | GET | `/attendance/team` | Team Leader, Admin | The team's visits and pending overtime |
| 16 | GET, POST | `/attendance`, `/attendance/new`, `/attendance/{id}/edit`, `/attendance/{id}` | HSE Officer, Admin | Record or correct a visit, with a reason; the history is kept |
| 17 | GET, POST | `/overtime`, `/overtime/cancel`, `/overtime/{id}/note` | Admin; leader for the note | Pending overtime |
| 18 | GET, POST | `/violations`, `/violations/{id}`, `/violations/{id}/photo`, `/violations/{id}/dispute` | The person concerned | Own violations, the evidence photo, and an appeal within the appeal period |
| 19 | GET, POST | `/violations/review`, `/violations/all`, `/violations/{id}/take`, `/violations/{id}/confirm`, `/violations/{id}/dismiss` | HSE Officer, Admin | The Review Queue and the violation list |
| 20 | POST | `/violations/{id}/decision` | Admin | Decide an appeal, with the HSE facts |
| 21 | GET | `/safety/shifts` | HSE Officer, Admin | Unsafe time slots |
| 22 | GET, POST | `/tickets`, `/tickets/new`, `/tickets/{id}`, `/tickets/{id}/comments`, `/tickets/{id}/cancel`, `/tickets/{id}/photo` | Everyone | Own tickets; a team leader also reads and comments on the team's; an HSE Officer who was asked reads and comments |
| 23 | GET, POST | `/tickets/queue`, `/tickets/{id}/take`, `/tickets/{id}/assignee`, `/tickets/{id}/ask`, `/tickets/{id}/decide`, `/tickets/{id}/resolve` | Admin | The ticket queue; an Admin can't handle a ticket they opened |
| 24 | POST | `/tickets/{id}/facts` | HSE Officer who was asked | The facts of the shift |
| 25 | GET | `/reports`, `/team/report` | Team Leader, Admin | Monthly report: leader for their own team, Admin for any team or the site |
| 26 | GET, POST | `/notifications`, `/notifications/{id}/read`, `/notifications/read-all` | Everyone | Own notices |
| 27 | POST | `/account/zalo/link`, `/account/zalo/unlink` | Everyone | Own Zalo link code and QR, or unlink |
| 28 | POST | `/zalo/webhook` | Zalo Bot Platform | No session or CSRF; refused unless the `X-Bot-Api-Secret-Token` header matches |
| 29 | GET | `/actuator/health` | Public | For Render and the uptime monitor |
| 30 | any | `/api/**` | API key | No endpoint since V26 (the burst alert API was removed, CR-02). The chain still refuses any call without the `X-Api-Key` |

## 2. Detection App and Supabase

The detection app connects to PostgreSQL through Supabase's session pooler with psycopg, over TLS, as
`steelsight_detection`. That role can only touch the columns below, and row level security limits its
rows. Every event has a UUID `event_id` made on the laptop and waits in the local outbox until it is
stored, so an event sent twice is stored once and nothing is lost while the internet is down. Events go
oldest first, so a check-out never arrives before its check-in. The full contract is
[detection-app-contract.md](detection-app-contract.md).

| # | Event | What the detection app writes | What the database does |
| --- | --- | --- | --- |
| 1 | Gate check-in (CAM-01) | `INSERT check_in` (user, camera, zone, check-in time, `method = FACE`, face similarity, photo, event_id) | Attaches the visit to the person's shift if it starts at most 60 minutes before (BR-30), closes a visit left open from an earlier day, and sends the CHECK_IN notice |
| 2 | Gate check-out | `UPDATE check_in SET check_out_at` on the open visit | Sends the CHECK_OUT notice (the ERP adds the paid hours), and marks the shift COMPLETED (V25) |
| 3 | Violation (CAM-02 to CAM-04) | Photo to `evidence-photos`, then `INSERT violation` (camera, type, time, confidence, person box, photo, `suggested_user_id`, `suggestion_similarity`, event_id) | Fills the zone from the camera, starts it PENDING_REVIEW with no person, and alerts the HSE Officers on duty (BR-54) |
| 4 | Face enrolment | Reads PENDING `face_embedding` rows, downloads the photo, then `UPDATE face_embedding` to READY (vector, model) or FAILED (reason) | READY vectors are used at the gate and for suggestions |
| 5 | Stale visits | `SELECT close_stale_check_ins(time_zone)` on a timer and before each check-in | Flags Incomplete after 20 minutes and closes a visit 4 hours after its shift (BR-35) |
| 6 | Reference data | Reads cameras, zones (code, name, kind), shifts, and each READY embedding with the employee code and full name | |

The role can't read passwords, emails, phone numbers or tickets, and can't change a review, an appeal
or a notice. The ERP connects as the table owner.

## 3. Photo Storage

Photos are kept in three private Supabase Storage buckets and reached over the Storage REST API with the
server key. Tables store only the path. Browsers never get a Storage URL: each photo goes through an ERP
route that checks the permission first.

| # | Bucket | Referenced by | Written by | Read through |
| --- | --- | --- | --- | --- |
| 1 | `face-photos` | `app_user.face_file_path`, `face_embedding.source_file_path` | ERP (Admin only) | `/team/{id}/face`, and the detection app for enrolment. Deleted with the account |
| 2 | `ticket-photos` | `ticket.photo_path` | ERP (the ticket's author) | `/tickets/{id}/photo` |
| 3 | `evidence-photos` | `violation.snapshot_path`, `check_in.snapshot_path` | Detection app | `/violations/{id}/photo`; the detection app clears old local copies (SRS 3.2.41) |

A photo is JPEG or PNG, decided from its bytes, at most 5 MB, and gets a random name.

## 4. Zalo Bot

The ERP talks to the Zalo Bot Platform at `https://bot-api.zaloplatforms.com/bot<token>`.

| # | Direction | Call | Purpose |
| --- | --- | --- | --- |
| 1 | ERP → Zalo | `POST /setWebhook` with `url` and `secret_token` | At start-up, registers `<ZALO_WEBHOOK_URL>` with the shared secret |
| 2 | ERP → Zalo | `POST /sendMessage` with `chat_id` and `text` | Sends each due notice (texts over the limit are cut with a link) and each bot reply, one at a time in order |
| 3 | Zalo → ERP | `POST /zalo/webhook` with header `X-Bot-Api-Secret-Token` | Each `message.text.received` event in a private chat: a 6-digit link code, `/stop`, `/help`, `/lich [tuantoi]` or `/chamcong [thangtruoc]` |

A person links their chat by scanning the QR code on their home page (it opens the bot) and sending the
code shown there. The settings are `ZALO_BOT_TOKEN`, `ZALO_BOT_LINK`, `ZALO_WEBHOOK_SECRET`,
`ZALO_WEBHOOK_URL` and `APP_BASE_URL` (for the links in messages). Without a token and link the bot is
off, and every delivery is SKIPPED.

## 5. Cameras

The detection app opens each camera over RTSP (port 554, path `/Streaming/Channels/101` for the
Hikvision cameras) on the site network. Readers keep only the newest frame, so a slow model never builds
a backlog, and reconnect when a stream drops. Each camera has a code in the desktop settings that must
equal `camera.camera_code` in the ERP, and its zone is chosen by the zone's code (SRS 3.2.30).

| Camera | Zone | Role |
| --- | --- | --- |
| CAM-01 | GATE-IN | Attendance: face check-in and check-out |
| CAM-02 | GATE-PPE | PPE at the gate, in and out, at any time |
| CAM-03, CAM-04 | AREA-A, AREA-B | PPE in the work areas |
