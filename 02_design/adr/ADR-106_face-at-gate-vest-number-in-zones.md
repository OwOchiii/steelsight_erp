# ADR-106 — Identify by face at the gate and by vest number in the zones

**Status:** `Superseded by ADR-123`
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #identity #biometrics
**US References:** `US-WRK-001`, `US-HSE-001` ⭐ *CASE 2.0*
**BR References:** `BR-IDG-006`, `BR-IDG-007` ⭐ *CASE 2.0*

> Scope: SteelSight **detection app**. Recorded retrospectively from the code and the developer's 2026-11-06 gate decision. IDs are defined in [DETECTION-MODULE-MAP](../DETECTION-MODULE-MAP.md).

---

## Context — Bối cảnh
Zone cameras are far away and workers face away. Faces are unreliable there, but large printed vest numbers can be read.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Face recognition on every camera | No vest dependency | Slow; poor at distance; more biometric processing |
| B | Face only at the gate, which links the vest to the worker; zones identify by the linked vest number | Less biometric processing; works at distance | Swapped vests misattribute violations |

## Decision — Quyết định
*We chose option B.* Only cameras with a `GateMonitor` run face recognition. `LinkBook` holds active links locally, one vest per worker and one worker per vest.

## Rationale — Lý do
It limits biometric processing to the point where identity is established.

## Consequences — Hệ quả
**Positive:**
- Violations can name workers from a distance.

**Negative (trade-offs accepted):**
- A vest swap on site cannot be detected; accepted as a known limitation on 2026-11-09.

**Risks:**
- If the number is not read on entry, zone violations stay unnamed until the next entry.

**Compliance Impact:** ⭐ *CASE 2.0*
- Data minimisation: biometric matching happens only at the gate (Luật 91/2025/QH15).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-109.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-106:
1. Do NOT enable face recognition on zone cameras.
2. Only the gate creates or ends a vest link; `VestService` MUST only read and look up.
3. Vest numbers are compared through `erp.mapping.vest_key` (digits, no leading zeros).
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
