# ADR-102 — Ship detection as a Windows desktop app, not a Docker service

**Status:** `Accepted`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #deployment
**US References:** `US-ADM-001` ⭐ *CASE 2.0*
**BR References:** `BR-DPL-004` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided by the developer on 2026-11-06; the original architecture diagram showed Docker. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Detection needs direct access to an NVIDIA GPU, Windows audio for spoken alerts, and local cameras on the site laptop. The ERP is a separate Spring Boot program sharing one Supabase database.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Docker container | Matches the first diagram; repeatable | GPU and audio passthrough on Windows is awkward; needs Docker on a site laptop |
| B | Windows desktop app: `setup.bat` from source, or a PyInstaller `.exe` with a pywebview window | Direct GPU and speaker access; simple for a site administrator | Windows-focused; large bundle |

## Decision — Quyết định
*We chose option B.* `desktop.py` is the `.exe` entry point; `run.py` opens the dashboard in a browser for development.

## Rationale — Lý do
The hardware and audio needs are local, and the operator is a site administrator, not a platform team.

## Consequences — Hệ quả
**Positive:**
- Spoken alerts and TensorRT work directly on the machine.

**Negative (trade-offs accepted):**
- Audio, the window and the `.exe` are Windows-only; Ubuntu scripts exist for development.

**Risks:**
- The packaged `.exe` downloads face and OCR models on first run and needs internet then.

**Compliance Impact:** ⭐ *CASE 2.0*
- Biometric processing stays on the site machine, except what is written to the ERP.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-108 (offline-tolerant ERP writes).

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-102:
1. Do NOT add a Docker-only dependency to the detection app.
2. Bundled read-only UI files come from `config.BUNDLE_DIR`; writable data lives in `config.DATA_DIR`.
3. Keep `steelsight.spec` datas in step with any move of `steelsight/web/static`.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
