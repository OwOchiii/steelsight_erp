# ADR-024 — One log line per request, with a request ID and no personal data

**Status:** `Accepted`
**Date:** 2026-11-05
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer (`chatlog/ai-log.md` 2026-11-05 13:30)
**Context tags:** #observability #privacy
**US References:** — ⭐ *CASE 2.0*
**BR References:** `BR-PLT-002`, `BR-PLT-004` ⭐ *CASE 2.0*

> Recorded retrospectively from `web/RequestLogFilter.java`, `security/SignInLog.java`, `PageExceptionHandler`, `application.yaml` (`logging.pattern.correlation`).

---

## Context — Bối cảnh
Render keeps only stdout logs. When something breaks, the developer must find the request and the user without a tracing system. Query strings and form bodies can hold names, phone numbers and passwords.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Default Spring logs only | Nothing to build | Can't follow one request; no user |
| B | OpenTelemetry tracing | Rich | A collector to host |
| C | `RequestLogFilter` inside each security chain: 8-character request ID in MDC (`logging.pattern.correlation`), one line per request with method, **path only**, status, employee code and ms; ERROR for 5xx; 500 page shows the ID as a reference | Cheap; followable; private | Plain-text logs |

## Decision — Quyết định
*We chose option C.* Static assets are skipped. Form validation failures log field names only. Sign-in success and failure are logged with the typed code sanitised against log forging; passwords never. `PageExceptionHandler` logs bugs with stack traces and shows `reference` = request ID.

## Rationale — Lý do
Enough to trace any request on a free host, with nothing personal beyond the employee code.

## Consequences — Hệ quả
**Positive:**
- `RequestLoggingTest` (4 cases) checks the stack trace, reference, refused requests and no password.

**Negative (trade-offs accepted):**
- Employee codes are in logs (pseudonymous identifier).

**Risks:**
- Render's log retention is short and not under the project's control.

**Compliance Impact:** ⭐ *CASE 2.0*
- Logs contain no form data, query strings or passwords (data minimisation). Employee code in logs is personal data: retention to be set by the DPO.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-024:
1. Use SLF4J loggers; NEVER log passwords, form values, query strings, request bodies, photo bytes, face data
   or the API key. Log IDs and field names instead.
2. Anything typed by a user that goes into a log line MUST have \r \n \t replaced first.
3. Expected failures (BusinessRuleException, not found) log at INFO, access denied at WARN, bugs at ERROR with
   the exception, so the request ID links them.
4. Do NOT add System.out or a second logging framework.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
