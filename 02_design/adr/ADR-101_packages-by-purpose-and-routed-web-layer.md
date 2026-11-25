# ADR-101 — Group the Python code into packages by purpose and split the web layer into route files

**Status:** `Accepted`
**Date:** 2026-11-09
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #structure #maintainability
**US References:** `US-ADM-001` ⭐ *CASE 2.0*
**BR References:** `BR-DPL-005` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Decided by the developer on 2026-11-09 and carried out on branch `refactor/python-packages`. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
`steelsight/` held 30 flat modules and `web/server.py` was 1038 lines mixing application state, middleware and every endpoint. Related code was hard to find, and nothing showed which modules belonged together. All behaviour had to stay the same.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Packages only; keep `server.py` whole | Smallest diff | Largest file stays as it was |
| B | Packages by purpose, `web/` split into `app.py`, `state.py`, `deps.py` and `routes/*`; `config.py` stays whole | Clear ownership; routes are found by feature; low risk | One more level of imports |
| C | Also split `config.py`, `detector.py`, `sync.py` | Tidiest | Most regression risk; `config` is read by nearly every module |

## Decision — Quyết định
*We chose option B.* Packages are `core`, `vision`, `safety`, `people`, `gate`, `erp`, `records` and `web`; `engine.py` stays at the top because it joins them. `build_engine.py` moved to `scripts/`.

## Rationale — Lý do
It removes the worst structural problem at the lowest risk. Route bodies were copied unchanged by script, so the split can be checked mechanically.

## Consequences — Hệ quả
**Positive:**
- Each feature has one obvious home.
- Each route file is short (the longest is 163 lines).

**Negative (trade-offs accepted):**
- Import paths are longer, and any outside script using the old paths must change.
- `config.py` remains 613 lines.

**Risks:**
- `config.BASE_DIR` depends on the file's depth; moving `config.py` again would silently point data at the wrong folder.

**Compliance Impact:** ⭐ *CASE 2.0*
- None. No personal data handling changed.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Splitting `config.py` by concern, if it keeps growing.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-101:
1. New modules go in the package that owns the feature; do NOT add modules directly under `steelsight/` except the engine.
2. New endpoints go in a `steelsight/web/routes/<feature>.py` APIRouter and are included from `web/app.py`.
3. Routes hold no business logic: they validate input and call `state` (`web/state.py`) or a service.
4. Import with full paths (`from steelsight.core import config`); do NOT re-export modules from package `__init__.py` files.
5. If `core/config.py` moves, update `BASE_DIR` depth in the same change.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
