# SteelSight Detection — Module Map, Business Rules and User Stories

| Field | Value |
|-------|-------|
| **Document ID** | `STEELSIGHT-DET-ARCH-MAP-001` |
| **Version** | `1.1` |
| **Date** | `2026-11-17` |
| **Status** | `Draft` |
| **Source** | Reverse-engineered from the code (baseline `57283ce` plus package refactor) and `docs/` |
| **Author** | Claude Code (Opus 5.5) |
| **Reviewed by** | `[ ] Pending` |

This is the shared reference for the detection app's ADRs (ADR-101 onward), TDS and Test-Spec documents. ERP documents in the same folders use ADR-001 to ADR-026 and are defined by `MODULE-MAP.md`.

> **Where these IDs come from.** BRs are what the code enforces today; FR, NFR, INT and UC IDs come from `docs/REQUIREMENTS_SPECIFICATION.md` and `docs/USE_CASES_AND_ACTORS.md`. None has been confirmed by a product owner.

## 1. Modules

| Code | Module | Bounded Context | Code location | Tables | TDS | Test-Spec |
|---|---|---|---|---|---|---|
| `DPL` | Detection Platform | Configuration, domain types, local database, entry points and packaging | `steelsight/core/*`, `run.py`, `desktop.py`, `scripts/build_engine.py`, `steelsight.spec`, `setup.*`, `build_exe.ps1` | `violations`, `gate_events`, `active_links`, `outbox`, `face_cache` (schema owner, SQLite) | [DPL](../03_implement/STEELSIGHT-DET-DPL-IMP-001_TDS.md) | [DPL](../04_testing/STEELSIGHT-DET-DPL-TDD-001_Test-Spec.md) |
| `VIS` | Vision & Engine | Camera input, YOLO inference, tracking and the realtime loop | `steelsight/engine.py`, `steelsight/vision/*` | *(none)* | [VIS](../03_implement/STEELSIGHT-DET-VIS-IMP-001_TDS.md) | [VIS](../04_testing/STEELSIGHT-DET-VIS-TDD-001_Test-Spec.md) |
| `SAF` | Safety Rules | PPE association, violation timers, reminders and spoken alerts | `steelsight/safety/*` | *(writes through REC)* | [SAF](../03_implement/STEELSIGHT-DET-SAF-IMP-001_TDS.md) | [SAF](../04_testing/STEELSIGHT-DET-SAF-TDD-001_Test-Spec.md) |
| `IDG` | Identity & Gate | Face recognition (attendance and suggestions) and gate check-in/out | `steelsight/people/*`, `steelsight/gate/*` | `gate_events` (written here, defined in DPL); `active_links` no longer written | [IDG](../03_implement/STEELSIGHT-DET-IDG-IMP-001_TDS.md) | [IDG](../04_testing/STEELSIGHT-DET-IDG-TDD-001_Test-Spec.md) |
| `SYN` | ERP Sync | Outbox, mapping to ERP rows, Supabase writes and enrolment | `steelsight/erp/*` | `outbox` (SQLite); writes ERP `violation` (with a suggestion), `check_in`, `face_embedding` | [SYN](../03_implement/STEELSIGHT-DET-SYN-IMP-001_TDS.md) | [SYN](../04_testing/STEELSIGHT-DET-SYN-TDD-001_Test-Spec.md) |
| `REC` | Records & Notifications | Local violation store, evidence, reports and housekeeping | `steelsight/records/*` | `violations`, `gate_events` (reads and writes) | [REC](../03_implement/STEELSIGHT-DET-REC-IMP-001_TDS.md) | [REC](../04_testing/STEELSIGHT-DET-REC-TDD-001_Test-Spec.md) |
| `WEB` | Web & Access | Dashboard API, application state and sign-in | `steelsight/web/*` | `users.db` (accounts) | [WEB](../03_implement/STEELSIGHT-DET-WEB-IMP-001_TDS.md) | [WEB](../04_testing/STEELSIGHT-DET-WEB-TDD-001_Test-Spec.md) |

## 2. Decisions

| ADR | Decision |
|---|---|
| [ADR-101](adr/ADR-101_packages-by-purpose-and-routed-web-layer.md) | Group the Python code into packages by purpose and split the web layer into route files |
| [ADR-102](adr/ADR-102_windows-desktop-app-instead-of-docker.md) | Ship detection as a Windows desktop app, not a Docker service |
| [ADR-103](adr/ADR-103_one-batched-realtime-loop-on-latest-frames.md) | One detector, one batched realtime loop, latest frame only |
| [ADR-104](adr/ADR-104_identity-models-off-the-detection-loop.md) | Face and OCR models run off the detection loop (only the face model remains, ADR-123) |
| [ADR-105](adr/ADR-105_virtual-gate-line-decides-entry-and-exit.md) | A virtual line decides entering from leaving |
| [ADR-106](adr/ADR-106_face-at-gate-vest-number-in-zones.md) | ~~Identify by face at the gate and by vest number in the zones~~ Superseded by ADR-123 |
| [ADR-107](adr/ADR-107_timer-rules-with-shared-cooldown.md) | Violations are timers on continued absence; the cooldown is now per camera (ADR-123) |
| [ADR-108](adr/ADR-108_sqlite-outbox-with-ordered-lanes.md) | ERP writes go through a local SQLite outbox with ordered lanes |
| [ADR-109](adr/ADR-109_stable-event-ids-from-the-producer.md) | Detection creates stable event IDs for every ERP write |
| [ADR-110](adr/ADR-110_least-privilege-column-grants-for-detection.md) | Detection connects as a limited role and writes only granted columns |
| [ADR-111](adr/ADR-111_detection-records-erp-judges-attendance.md) | Detection records every crossing; the ERP judges attendance |
| [ADR-112](adr/ADR-112_polling-bridge-between-threads-and-asyncio.md) | The web layer polls thread-safe state |
| [ADR-113](adr/ADR-113_module-config-with-json-overrides.md) | Configuration is module constants overridden by settings.json and .env |
| [ADR-114](adr/ADR-114_demo-build-bypasses-login.md) | The demo build bypasses sign-in checks |
| [ADR-115](adr/ADR-115_launch-token-for-the-local-server.md) | Every local request must carry a per-launch token |
| [ADR-116](adr/ADR-116_match-faces-at-any-angle.md) | Every face is matched at any angle; only straight-on misses count as a stranger |
| [ADR-117](adr/ADR-117_offline-face-gallery-cache.md) | Keep an offline copy of the face gallery: vectors only, replaced on every sync |
| [ADR-118](adr/ADR-118_vest-ocr-crop-and-reading-rules.md) | ~~Vest OCR crop and reading rules~~ Superseded by ADR-123 |
| [ADR-119](adr/ADR-119_reference-first-and-camera-gated-violations.md) | Reference data loads before the queue; violations are sent only from known cameras |
| [ADR-120](adr/ADR-120_manual-vest-number-correction.md) | ~~Manual vest number correction~~ Superseded by ADR-123 |
| [ADR-121](adr/ADR-121_previews-poll-still-frames.md) | Dashboard camera previews fetch one frame at a time instead of holding streams open |
| [ADR-122](adr/ADR-122_vest-number-from-aruco-markers.md) | ~~Vest numbers from ArUco markers~~ Superseded by ADR-123 |
| [ADR-123](adr/ADR-123_no-vest-numbers-face-is-only-a-suggestion.md) | No vest numbers; a matched face is only a suggestion; one violation for both items (SRS 23/11/2026) |

## 3. Business rules

### DPL — Detection Platform

| ID | Rule | Enforced in |
|---|---|---|
| BR-DPL-001 | Settings overlay defaults by key, converted to the default's type | `core/config.py` `_OVERRIDABLE` |
| BR-DPL-002 | ERP secrets come only from `.env` or environment variables | `core/config.py`; `web/routes/settings.py` |
| BR-DPL-003 | Local SQLite uses WAL and adds missing columns on start | `core/db.py` `migrate` |
| BR-DPL-004 | A frozen build stores writable data outside the install folder | `core/config.py` `DATA_DIR` |
| BR-DPL-005 | Runtime data is never committed to git | `.gitignore` `data/*` |

### VIS — Vision & Engine

| ID | Rule | Enforced in |
|---|---|---|
| BR-VIS-001 | Only the newest frame of each camera is processed | `vision/capture.py`, `engine.py` |
| BR-VIS-002 | Inference batches at most `min(BATCH_MAX, detector.max_batch)` frames | `engine.py` |
| BR-VIS-003 | Model load falls back to the same-name `.pt`, then `FALLBACK_MODEL` | `vision/detector.py` |
| BR-VIS-004 | Tracks match by greedy IoU ≥ 0.3 and are dropped after 30 unmatched frames | `vision/tracker.py` |
| BR-VIS-005 | Live streams reconnect every 5 s with no retry limit (SRS BR-75) | `vision/capture.py` |

### SAF — Safety Rules

| ID | Rule | Enforced in |
|---|---|---|
| BR-SAF-001 | A violation needs an item to be unseen for its full timer: 10 s for a hard hat, 3 s for a vest (SRS BR-43) | `safety/monitor.py` |
| BR-SAF-002 | Seeing the item clears the timer and reminder | `safety/monitor.py` |
| BR-SAF-003 | The same tracked person and item on the same camera is recorded at most once per 60 s (SRS BR-44) | `CooldownBook` |
| BR-SAF-004 | A hard hat in the hand does not count as worn | `safety/rules.py` `associate_ppe` |
| BR-SAF-005 | Both items missing is one `missing_both` violation, sent as `NO_HARD_HAT_AND_VEST` (SRS BR-45) | `safety/monitor.py`, `erp/mapping.py` |
| BR-SAF-006 | A confirmed face is only a suggestion with its similarity; the speaker names the zone, never a person (SRS BR-47, BR-48) | `safety/monitor.py` |

### IDG — Identity & Gate

| ID | Rule | Enforced in |
|---|---|---|
| BR-IDG-001 | Two matching votes at least 1 s apart confirm a face (SRS BR-28) | `people/faceid.py` |
| BR-IDG-002 | Only a side change of the foot point is a crossing | `gate/monitor.py` |
| BR-IDG-003 | Check-in needs a recognised face | `GateMonitor._try_commit` |
| BR-IDG-004 | Check-out needs a recognised face | `GateMonitor._try_commit` |
| BR-IDG-005 | Crossing back before commit cancels; the same direction within 30 s is ignored | `GateMonitor._crossed`, `_repeat` |
| BR-IDG-006 | *(retired with vest links, ADR-123)* | — |
| BR-IDG-007 | Face recognition runs on gate and scanner cameras, and on PPE cameras while `FACE_ON_PPE_CAMERAS` is on | `engine.py` |
| BR-IDG-008 | Every crossing is recorded; missing PPE only adds a note | `GateMonitor._commit_in` |
| BR-IDG-009 | Every face is matched at any angle; a miss counts toward "Unknown" only within `FACE_MAX_YAW` (45°), and "Unknown" needs 3 such misses | `FaceService._worker` |
| BR-IDG-010 | A worker's best-matching photo, of any angle, decides the score | `ErpGallery.match` |
| BR-IDG-011 | *(retired with vest OCR, ADR-123)* | — |
| BR-IDG-012 | On a scanner, a second scan within 5 minutes (`SCANNER_TOGGLE_SECONDS`) is ignored and the screen says when to scan again; after that it switches in and out (SRS BR-29) | `ScannerMonitor.direction_for` |
| BR-IDG-013 | *(retired with vest numbers, ADR-123)* | — |
| BR-IDG-014 | The gate screen shows each visit of today with its times, and always a clock (SRS CR-22) | `gate/board.py`, `gate.html` |
| BR-IDG-015 | *(retired with vest markers, ADR-123)* | — |
| BR-IDG-016 | *(retired with vest numbers, ADR-123)* | — |

### SYN — ERP Sync

| ID | Rule | Enforced in |
|---|---|---|
| BR-SYN-001 | Events within a lane leave in order | `Outbox.head` |
| BR-SYN-002 | Transient failures retry with backoff up to 60 s | `ErpSync`, `is_transient_db_error` |
| BR-SYN-003 | Permanent failures are set aside and can be re-queued | `Outbox.mark_failed`, `requeue_failed` |
| BR-SYN-004 | Every ERP row has a stable `event_id` | `erp/mapping.py` |
| BR-SYN-005 | Replaying an event changes nothing | `ErpSync` handlers |
| BR-SYN-006 | Writes stay within the `steelsight_detection` grants | `erp/sync.py` |
| BR-SYN-007 | *(retired with vest links, ADR-123)* | — |
| BR-SYN-008 | *(retired: no burst alert, SRS CR-02; the ERP alerts HSE per violation)* | — |
| BR-SYN-009 | Cameras and faces load from the ERP before queued events are sent | `ErpSync._db_loop` |
| BR-SYN-010 | A violation is queued only when its camera has an ERP code the ERP knows (or the list isn't loaded yet) | `AppState._erp_camera_code` |
| BR-SYN-011 | Rejected events and pending violations can be cleared; pending attendance can't | `Outbox.clear`, `/api/erp/clear` |
| BR-SYN-012 | The face gallery is copied to disk after every successful sync and loaded at startup | `GalleryCache`, `AppState._load_cached_gallery` |

### REC — Records & Notifications

| ID | Rule | Enforced in |
|---|---|---|
| BR-REC-001 | Evidence photos and violation rows older than the retention period (default 30 days) are deleted; gate photos are not | `records/maintenance.py` |
| BR-REC-002 | One alert when free disk crosses below 5 GB | `Maintenance.check_disk` |
| BR-REC-003 | Report download accepts only an `.xlsx` base name inside `REPORTS_DIR` | `web/routes/reports.py` |
| BR-REC-004 | *(retired: Telegram removed; the ERP sends notices on Zalo)* | — |
| BR-REC-005 | The daily report runs once after `DAILY_REPORT_HOUR` | `DailyReport` |

### WEB — Web & Access

| ID | Rule | Enforced in |
|---|---|---|
| BR-WEB-001 | UI events are polled from `EventBus`, not pushed across threads | `web/routes/live.py` |
| BR-WEB-002 | Demo build: all requests skip sign-in checks | `web/app.py` `auth_guard` |
| BR-WEB-003 | Passwords are PBKDF2-HMAC-SHA256 with 200,000 iterations | `web/auth.py` |
| BR-WEB-004 | 5 failed sign-ins lock the account for 15 minutes (SRS BR-73) | `AuthStore.locked_for` |
| BR-WEB-005 | The first admin can be created only while no account exists | `web/routes/account.py` `api_setup` |
| BR-WEB-006 | Empty secret fields do not erase saved secrets | `web/routes/settings.py` |
| BR-WEB-007 | Every local HTTP and WebSocket request needs the launch token | `web/access.py`, `auth_guard`, `routes/live.py` |
| BR-WEB-008 | Dashboard previews fetch one frame at a time; only full screen and the gate screen hold a stream | `/stream/{i}/still`, `app.js startPreview` |

## 4. User stories

| ID | As a | I want to | So that | Source |
|---|---|---|---|---|
| US-ADM-001 | Site administrator | install and start the app with one script | the site runs without a developer | UC-1 |
| US-ADM-002 | Site administrator | tune detection and alert settings | the system fits the site | UC-11, UC-12 |
| US-ADM-003 | Site administrator | add cameras, give them ERP codes and draw a gate line | gate and zone cameras report correctly | UC-2 |
| US-WRK-001 | Worker | be checked in by face when I enter | my attendance is recorded | UC-4 |
| US-WRK-002 | Worker | be checked out by face when I leave | my visit is closed | UC-5 |
| US-HSE-001 | HSE officer | see and hear PPE violations as they happen | I can respond at once | UC-6, UC-7 |
| US-HSE-002 | HSE officer | review violation history and check-ins | I can follow up | UC-8, UC-9 |
| US-MGR-001 | Manager | receive the daily report, and notices from the ERP on Zalo | I can follow the site remotely | UC-10 |
| US-SYS-001 | ERP | receive every event exactly once in effect, even after an outage | records are complete and not duplicated | INT-8 |
| US-SYS-002 | ERP | have enrolment photos turned into face vectors | workers can be recognised | UC-3 |
| US-SYS-003 | System | clean old evidence and warn about disk space | the machine keeps running | UC-14 |
