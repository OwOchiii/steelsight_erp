# ADR-121 — Dashboard camera previews fetch one frame at a time instead of holding streams open

**Status:** `Accepted`
**Date:** 2026-11-17
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #web #performance
**US References:** `US-HSE-001` ⭐ *CASE 2.0*
**BR References:** `BR-WEB-008` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided on 2026-11-17, after the small previews mostly failed to load while full screen worked. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Each preview was an MJPEG stream that never ends, holding one of the browser's roughly 6 connections per server, shared with the gate screen and every API call. Previews that couldn't get a connection stayed on "Đang kết nối…", and rebuilt grids didn't always close old streams.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Keep MJPEG for every preview | Smooth | Runs out of connections |
| B | Previews fetch `/stream/N/still` in a loop; full screen keeps MJPEG | Never holds a connection; pauses when hidden | About 6 frames per second in previews |
| C | One combined stream for all cameras | One connection | Server-side compositing |

## Decision — Quyết định
*We chose option B.* `GET /stream/{i}/still` returns the latest annotated JPEG (`Cache-Control: no-store`, 204 when none yet). Each preview requests the next frame only after the previous one loads (`PREVIEW_MS` 150), pauses while the tab is hidden or full screen is open, and retries after 1 s on 204 or error.

## Rationale — Lý do
Short requests free their connection at once, and the self-throttling loop never overlaps requests.

## Consequences — Hệ quả
**Positive:**
- All previews load, and API calls no longer stall behind streams.

**Negative (trade-offs accepted):**
- Previews update at about 6 fps instead of the stream rate.

**Risks:**
- More requests per second to the local server (about 24 for 4 cameras).

**Compliance Impact:** ⭐ *CASE 2.0*
- None.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-121:
1. Previews use /stream/N/still; only the full-screen view and the gate screen use /stream/N.
2. Request the next still only from onload/onerror, never on a fixed timer.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
