# ADR-123 — No vest numbers; a matched face is only a suggestion for the HSE Officer

**Status:** `Accepted`
**Date:** 2026-11-23
**Deciders:** Project team (SRS of 23/11/2026); Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #identity #vest #safety #srs
**US References:** `US-WRK-001`, `US-HSE-001` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-001`, `BR-IDG-007`, `BR-SAF-003`, `BR-SAF-005`, SRS `BR-28`, `BR-44`, `BR-45`, `BR-47`, `BR-48`, `CR-01`, `CR-02` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Supersedes [ADR-106](ADR-106_face-at-gate-vest-number-in-zones.md),
> [ADR-118](ADR-118_vest-ocr-crop-and-reading-rules.md), [ADR-120](ADR-120_manual-vest-number-correction.md) and
> [ADR-122](ADR-122_vest-number-from-aruco-markers.md); amends [ADR-104](ADR-104_identity-models-off-the-detection-loop.md)
> (only the face model remains) and [ADR-107](ADR-107_timer-rules-with-shared-cooldown.md) (cooldown per camera).
> The ERP side is in the ERP's `docs/detection-app-contract.md`.

---

## Context — Bối cảnh
Vest numbers were meant to name a worker in the zones, where faces are too far to read. In practice
EasyOCR misread numbers, ArUco markers needed printed, flat patches on every vest, and a vest can be
swapped or borrowed, so the number never proved who wore it. The SRS of 23/11/2026 removes vest numbers
(CR-01) and burst alerts (CR-02), and makes every violation wait for an HSE Officer to confirm the person
(BR-48). The team also decided that a zone belongs to a team, so HSE picks the violator from that team.

## Options Considered — Các lựa chọn đã xem xét
1. **Keep vest numbers as a suggestion.** Still needs markers on every vest and OCR or marker reading;
   a suggestion from a borrowed vest is as misleading as a wrong name.
2. **Remove vest numbers; a face matched on any camera is a suggestion with its similarity.** Chosen.
3. **Remove all identification in the zones.** Simplest, but throws away the faces that are close enough.

## Decision — Quyết định
- The app reads no vest numbers and makes no vest links. EasyOCR is removed from `requirements.txt`,
  `setup.ps1` and the PyInstaller spec. `people/vest.py`, `people/marker.py` and `LinkBook` stay in the
  code, unwired, until someone deletes them.
- Faces are compared on PPE cameras too (`FACE_ON_PPE_CAMERAS`). A confirmed face (2 matches at least
  1 s apart, similarity 0.45 or more, BR-28) becomes `suggested_user_id` and `suggestion_similarity` on
  the violation. The app never writes `user_id`; the ERP starts the violation `PENDING_REVIEW`.
- Both items missing is one `NO_HARD_HAT_AND_VEST` violation (BR-45). The cooldown is per tracked person,
  item and camera (BR-44). The speaker names the zone, never a person.
- No burst alert: the ERP's trigger alerts the HSE Officer on duty for each violation (BR-54).

## Rationale — Lý do
A suggestion that can be wrong must look like a suggestion. A face is the only identity the system can
check (consent and enrolment, BR-10 to BR-12), and HSE confirmation keeps a wrong match from costing a
worker points or bonus.

## Consequences — Hệ quả
**Positive:**
- No markers to print, no OCR model to download, one fewer model on the GPU.
- No wrong names on the speaker or in the ERP; every score change is confirmed by a person.

**Negative (trade-offs accepted):**
- Most work-zone violations have no suggestion: faces are identifiable only within about 7 m. HSE picks
  from the zone's team.
- Face matching on PPE cameras costs GPU time; it can be switched off with `FACE_ON_PPE_CAMERAS`.

**Risks:**
- Tracker ID switches can let the same person be recorded again within 60 s on one camera.

**Compliance Impact:** ⭐ *CASE 2.0*
- Face data is now processed on PPE cameras as well as at the gate. It stays covered by the consent
  each employee signs (BR-10, Decree 13/2023/ND-CP); no new data is stored.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Delete the unwired vest code and drop `ppe_vest` and `ppe_linking` in an ERP migration.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-123:
1. Never read, store or send vest numbers; never write ppe_vest or ppe_linking.
2. A face match is only suggested_user_id + suggestion_similarity (0..1); never write violation.user_id,
   review_status or subject.
3. Both items missing -> one NO_HARD_HAT_AND_VEST row.
4. Never name a person on the speaker; name the zone.
5. No burst alerts and no messages from the app; the ERP notifies.
```
