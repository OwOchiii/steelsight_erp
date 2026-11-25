# ADR-118 — Vest OCR reads below the chin to the feet, blanks the camera's text, and needs a clear majority

**Status:** `Superseded by ADR-123`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #ocr #identity
**US References:** `US-WRK-001` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-001`, `BR-IDG-011` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided on 2026-11-17, while testing with a number held on paper. Amends [ADR-106](ADR-106_face-at-gate-vest-number-in-zones.md). IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md). **Since [ADR-122](ADR-122_vest-number-from-aruco-markers.md) this applies only when `VEST_ID_METHOD = "ocr"`; markers are the default.**

---

## Context — Bối cảnh
Without a vest box the app read nothing, so a number on paper was never read. After that changed, a held "3" was read as "0", then "4", and during check-in as "2". The real EasyOCR model showed the cause: the crop stopped at 80% of the person box and cut the digit, and a cut 3 is read as 7 at 99% confidence. A digits-only reader also reads round shapes (a face) as 0, and the camera burns its clock into the frame edges.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Read only inside a YOLO vest box | Fewest false reads | No reading of paper or of a vest YOLO missed |
| B | Without a vest box, read from below the chin to the bottom of the person; blank the frame's top and bottom bands; ignore zero-only reads; confirm by majority | Reads paper and missed vests; removes the known false sources | More OCR work; any printed number on clothing can be read |
| C | Train a custom digit model | Best accuracy | Needs data and training time |

## Decision — Quyết định
*We chose option B.* `VEST_OCR_REQUIRE_VEST_BOX = False`. `torso_crop` uses the vest box (+8%) when there is one; otherwise from just below the face box (`face_bbox_full`, full-resolution) or 15% of the person's height, down to the bottom of the person box. `VEST_OCR_OSD_BAND` (7%) of the frame's top and bottom is greyed. Readings of only zeros are dropped. A number is confirmed when it was read `VEST_OCR_CONFIRM_HITS` (3) times in a row, each at `VEST_OCR_CONFIRM_CONF` (0.90) or more; a less confident read is skipped and a different number restarts the run (changed later on 2026-11-17, after the majority rule still let fluctuating reads through). `VEST_OCR_DEBUG` shows every candidate and keeps the last 40 crops that read something.

## Rationale — Lý do
Each rule removes a cause measured on the real model; the crop change alone turned a confident 7 into a 3.

## Consequences — Hệ quả
**Positive:**
- A number on paper or a missed vest is read; the clock and faces no longer produce numbers.

**Negative (trade-offs accepted):**
- OCR runs for every tracked person, not only those with a vest box.
- A printed number on clothing can be read and linked.

**Risks:**
- The OSD band can hide a number held at the very edge of the frame.

**Compliance Impact:** ⭐ *CASE 2.0*
- Debug crops of people are kept on disk (40 at most) while `VEST_OCR_DEBUG` is on.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-118:
1. Do NOT cut the no-vest crop above the bottom of the person box.
2. Grey the OSD bands before reading; drop zero-only readings.
3. Confirm a number only after VEST_OCR_CONFIRM_HITS identical reads in a row at >= VEST_OCR_CONFIRM_CONF.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
