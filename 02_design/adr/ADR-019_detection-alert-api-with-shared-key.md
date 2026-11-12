# ADR-019 — Detection alert REST API with a shared key and a quiet period

**Status:** `Accepted`
**Date:** 2026-11-05
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #integration #api #auth
**US References:** `US-SYS-001`, `US-HSE-006` ⭐ *CASE 2.0*
**BR References:** `BR-NTF-002`, `BR-NTF-003`, `BR-NTF-004`, `BR-PLT-003` ⭐ *CASE 2.0*

> Recorded retrospectively from `api/DetectionAlertController.java`, `api/ApiExceptionHandler.java`, `security/ApiKeyFilter.java`, `SecurityConfig.apiFilterChain`, `SafetyAlertService`.

---

## Context — Bối cảnh
Detection decides for itself when violations are "too many" (count in a time window) and wants HSE told now. Writing a notification row directly isn't allowed for the detection role (ADR-004), and the ERP needs to choose recipients (active HSE officers) and throttle. Detection is software, not a browser: no session, no CSRF token.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Grant detection INSERT on `notification` | No API | Detection must know HSE users; no throttle; wider grants |
| B | `POST /api/v1/detection/alerts`, own stateless filter chain, `X-Api-Key` shared secret compared in constant time, JSON errors, idempotent by `eventId`, 15-minute quiet period | Small surface; ERP owns recipients and throttling | Shared secret to rotate by hand; ERP must be awake (Render wakes on request) |
| C | OAuth2 client credentials | Standard, rotatable | An identity provider to run |

## Decision — Quyết định
*We chose option B.* `@Order(1)` chain matches `/api/**`: `ApiKeyFilter` before authorization, CSRF off, `STATELESS`, 401 JSON on failure. With `STEELSIGHT_DETECTION_API_KEY` empty, every call is refused. Body: `eventId` (UUID, required), `violationCount ≥ 1`, `windowSeconds ≥ 1`, `detectedAt` (required), `cameraCode` (optional, ≤ 100, not a foreign key so a typo can't lose an alert). 201 created, 200 duplicate, 400 invalid, 401 bad key, 500 bug (no stack trace to the caller).

## Rationale — Lý do
The smallest API that keeps recipient choice and throttling in the ERP, with no extra infrastructure.

## Consequences — Hệ quả
**Positive:**
- `DetectionAlertApiTest` (8 cases) and `DetectionApiWithoutKeyTest`; `pagesStillRequireACsrfToken` shows the page chain is unaffected.

**Negative (trade-offs accepted):**
- A leaked key can still add up to 60 `safety_alert` rows a minute (`ApiLimiter`), and can silence a camera whose code it knows. Before 2026-11-09 one fake alert silenced every camera; the quiet period is now per registered camera, and all other codes share one.
- The limiter checks the key first and counts wrong keys separately, because `X-Forwarded-For` can be faked and a limit applied before the key check could lock out the real caller.
- The shared key is still the only proof of who is calling; requests aren't signed.
- No OpenAPI document (gap G10).

**Risks:**
- The quiet-period check isn't locked; two simultaneous alerts can both notify (gap G5).

**Compliance Impact:** ⭐ *CASE 2.0*
- The alert carries no personal data (counts, time, camera code).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-022.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-019:
1. Machine endpoints live under /api/v1/** in package orochi.steelsight.api and are covered by the
   apiFilterChain (ApiKeyFilter, stateless, no CSRF). Never put a page under /api/ or an API outside it.
2. The caller identity is the fixed principal "detection-software" with no authorities; do NOT add user
   permissions to API calls.
3. Validate the body in the controller and return 400 {"error": "<field> ..."}; errors go through
   ApiExceptionHandler as JSON, never the HTML error page, never a stack trace.
4. Compare secrets with MessageDigest.isEqual; never log the key.
5. Every write accepted from detection MUST be idempotent by eventId (ADR-005).
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
