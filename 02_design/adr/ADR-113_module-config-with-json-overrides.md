# ADR-113 — Configuration is module constants overridden by settings.json and .env

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #configuration
**US References:** `US-ADM-002` ⭐ *CASE 2.0*
**BR References:** `BR-DPL-001`, `BR-DPL-002` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code (`core/config.py`). IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Most settings must change from the dashboard without a restart, and secrets must not be returned to the browser.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Settings object injected everywhere | Explicit | Touches every constructor |
| B | Module globals read as `config.X` at use; `_OVERRIDABLE` maps `settings.json` keys; ERP secrets from `.env` | Live changes; simple | Global state |

## Decision — Quyết định
*We chose option B.*

## Rationale — Lý do
Reading at the point of use makes most changes apply at once, and tests can override values with `cfg(...)`.

## Consequences — Hệ quả
**Positive:**
- The UI can tune rules live.

**Negative (trade-offs accepted):**
- `FACE_MODEL_PACK`, `FACE_DET_SIZE`, `DEVICE`, `HOST` and `PORT` are read once and need a restart.

**Risks:**
- Do not capture config values at import, or they stop being live.

**Compliance Impact:** ⭐ *CASE 2.0*
- `public_settings()` must not expose secrets.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-113:
1. Read `config.X` when needed; do NOT copy it into a module constant.
2. A new overridable setting needs a constant and an `_OVERRIDABLE` entry; to edit it in the UI also add it to `public_settings()` and the `allowed` set in `web/routes/settings.py`.
3. Empty secret fields MUST NOT overwrite saved secrets.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
