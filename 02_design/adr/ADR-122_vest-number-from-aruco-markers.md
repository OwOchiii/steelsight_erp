# ADR-122 — Vest numbers are read from printed ArUco markers by default; OCR stays as a setting

**Status:** `Superseded by ADR-123`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #identity #vest #marker
**US References:** `US-WRK-001` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-001`, `BR-IDG-015`, `BR-IDG-016` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided on 2026-11-17, after OCR kept misreading vest numbers on the real camera. Amends [ADR-106](ADR-106_face-at-gate-vest-number-in-zones.md) (vest number in the zones); [ADR-118](ADR-118_vest-ocr-crop-and-reading-rules.md) now applies only when `VEST_ID_METHOD = "ocr"`. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
EasyOCR reads a vest number held straight at the camera, but misses it when the worker is turned, the fabric is folded or the worker is walking. When it is unsure it still guesses: a cut "3" was read as "7" at 99%. Requiring 3 reads in a row at 0.90 or more stopped most wrong numbers, but then a number was often never confirmed. The developer asked whether OCR is the right tool at all.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Keep OCR and tune it further | No change to the vests | Turned or folded digits stay unreadable; it still guesses |
| B | Print an ArUco marker (ID = vest number) on each vest, read with `cv2.aruco` | Made for cameras: reads at 60–70° turn, further than digits of the same size; error-checked, so it returns the right ID or nothing; no new package (OpenCV ≥ 4.7) and no model | Markers must be printed and attached; a folded marker doesn't read |
| C | QR codes | Holds text | Smaller cells for the same size, so a much shorter range |
| D | RFID / BLE tags | Works without line of sight | New hardware at the gate; out of scope for the demo |

## Decision — Quyết định
*We chose option B, keeping OCR as a setting.* `VEST_ID_METHOD = "marker"` (default) or `"ocr"`, changeable in Settings at runtime.

- Dictionary `DICT_4X4_100` (`VEST_MARKER_DICT`): the largest cells, so the longest range. IDs 1–99 are vest numbers (`VEST_MARKER_MAX_ID`); ID 0 and IDs above the max are ignored. Marker 3 = vest 3 = ERP `V-003` (matched by digits, as before).
- `MarkerReader` (`steelsight/people/marker.py`) has the same interface as `VestOCR`, so the worker thread, links, gate screen and manual correction are unchanged. It reads the whole person box plus 10% (`marker_crop`), because the marker may be on the chest or the back, and needs no vest box. When several markers are seen it keeps the largest.
- A number is confirmed after `VEST_MARKER_CONFIRM_HITS` (2) identical reads in a row; a different ID resets the run. OCR keeps 3 reads at 0.90 or more.
- `scripts/make_vest_markers.py` prints one 300 dpi page per vest: the marker with a white margin, the number in large digits for people, and a 10 cm bar to check that it printed at 100%. It refuses a size that doesn't fit the paper with enough margin (20 cm needs A3).
- Recommended sizes: **20 cm on the back, 15 cm on the chest**, read from the camera's main stream (2560×1440).

**Range** (marker pixels ≈ 1113 × side / distance with the 2.8 mm lens, 1526 × side / distance with the 4 mm; reliable at about 36 px, limit about 24 px), straight on, good light:

| Marker side | 2.8 mm: reliable / limit | 4 mm: reliable / limit |
|---|---|---|
| 10 cm | 3.1 m / 4.6 m | 4.2 m / 6.4 m |
| 15 cm | 4.6 m / 7.0 m | 6.4 m / 9.5 m |
| 20 cm | 6.2 m / 9.3 m | 8.5 m / 12.7 m |
| 25 cm | 7.7 m / 11.6 m | 10.6 m / 15.9 m |

At a 60° turn the marker is half as wide, so the range about halves. Blur, low light and H.264 compression take off more: plan on about 70% of these figures until measured on site.

## Rationale — Lý do
The problem was reading, not tuning: digits were never designed to be read by a camera at an angle. A marker is, and its error check removes the confident wrong read that OCR produced. It costs no new dependency and runs on the CPU in milliseconds.

## Consequences — Hệ quả
**Positive:**
- Vest numbers read at angles and in motion; a wrong number is practically impossible.
- No OCR model to download or keep on the GPU in marker mode.

**Negative (trade-offs accepted):**
- Every vest needs a printed, flat, matte marker, front and back.
- Up to 99 vests; more needs `DICT_5X5_250` (about 15% less range) and reprinting.

**Risks:**
- A marker that is folded, glossy, dirty or covered is not read; the manual correction (ADR-120) covers it.
- The range figures are calculated, not yet measured on the real camera.

**Compliance Impact:** ⭐ *CASE 2.0*
- None new: the marker carries only the vest number, which is already printed on the vest.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Marker pose could give the worker's facing direction, if ever needed.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-122:
1. The marker ID IS the vest number. Ignore ID 0 and IDs above VEST_MARKER_MAX_ID.
2. MarkerReader must keep VestOCR's interface (ready, status, load_async, read_detail) so VestService
   switches readers without other changes.
3. In marker mode read the whole person box (+10%); never require a vest box.
4. Confirm only after VEST_MARKER_CONFIRM_HITS identical reads in a row.
5. Never add a package for markers; cv2.aruco is part of opencv-python.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
