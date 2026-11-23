# Detection app contract (after V19–V22)

The desktop/detection app writes into the same Supabase database as the web ERP, through the
`steelsight_detection` role. Migrations V19 to V22 change the ERP to the SRS of 23/11/2026 **without
dropping or renaming anything the current detection app writes**, so it keeps working. This file
lists what the detection app should change to match the SRS, in order of importance.

**Status, 23/11/2026:** the detection app (`steelsight-demo`) now does all of items 1 to 9. Its
`tests/erp_integration_check.py` applies V1 to V22 to a real Postgres and drives its sync code as the
`steelsight_detection` role: 21 of 21 checks pass (one `NO_HARD_HAT_AND_VEST` row, `PENDING_REVIEW` with a
suggestion and no person, one alert per violation to the HSE Officer on duty, the 60-minute shift window,
no vest tables, no `telegram_chat_id`). The tolerant paths below stay, so an event queued by an older
copy of the app is still accepted.

## Must change

1. **Stop reading `app_user.telegram_chat_id`.** V19 drops the column; Telegram is gone (Zalo only).
   Any query that selects it now fails.
2. **Stop sending check-in messages itself.** The ERP sends every notice (in-app first, then Zalo,
   BR-15). Read `zalo_chat_id` only if you still need it for something else.

## Should change (the ERP already accepts the new way)

3. **Violations: send a suggestion, not a person.** Write `suggested_user_id` and
   `suggestion_similarity` (0–1, BR-47). If you still write `user_id` or `ppe_linking_id`, the trigger
   moves that person into `suggested_user_id`; the violation always starts `PENDING_REVIEW` with no
   person (BR-48). Don't write `review_status`, `subject` or `zone_id`; the zone comes from the camera.
4. **One violation when both items are missing:** type `NO_HARD_HAT_AND_VEST` (BR-45, CR-06), not
   two rows. `NO_GLOVES` and `NO_HARNESS` are out of scope (OR-04).
5. **Stop calling `POST /api/v1/detection/alerts`** (burst alerts, CR-02). It still answers 201 so
   your outbox stops retrying, but it notifies nobody; each violation alerts the HSE Officers on duty
   by a database trigger (BR-54).
6. **Stop using `ppe_vest` / `ppe_linking`** (CR-01). The tables stay until you do.
7. **Evidence photos:** upload to the private bucket `evidence-photos` (env `SUPABASE_EVIDENCE_BUCKET`)
   and write its path in `snapshot_path`. Paths may contain folders (`2026/11/23/cam03-….jpg`), never
   `..`. The ERP shows them only after a permission check (CM-07).
8. **Check-ins:** leave `shift_id` empty if you like; the ERP attaches the visit to the person's shift
   when it starts at most 60 minutes before the shift (BR-30), not 2 hours. Only cameras with
   `camera.role = 'ATTENDANCE'` should record attendance (BR-27).
9. **Closing stale visits:** keep calling `close_stale_check_ins()`. It now flags a visit Incomplete
   20 minutes after its shift ends and closes it 4 hours after (BR-35); the time it writes is never
   paid. A new check-in also closes a visit left open from an earlier day.

## Zones and cameras

- `zone.kind` is `GATE_ATTENDANCE`, `GATE_PPE` or `WORK_AREA`; `zone.name` is what alerts say and the
  Admin can rename it (CR-03).
- A zone can have **several cameras** (team decision; the SRS still says one). Each camera reports in
  its own zone.
- A `WORK_AREA` zone can belong to a team (`zone.team_id`); HSE picks the violator from it first.

## Still the detection app's job (not testable from the ERP)

BR-12 duplicate face, BR-27–BR-29 face confirmation and IN/OUT, BR-40–BR-45 PPE timers, reminders and
cooldown, BR-72–BR-76 Vietnamese texts, desktop login, offline queue, camera offline, evidence cleanup,
CR-04 CAM-02 in both directions at any time, CR-22 clock on the gate screen.
