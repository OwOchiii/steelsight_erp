# ADR-025 — A mistake is shown on the page where it was made, and never as raw JSON

**Status:** `Accepted`
**Date:** 2026-11-16
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #usability #error-handling #uploads
**US References:** `US-WRK-004`, `US-ADM-002`, `US-ADM-003`, `US-MGR-001`, `US-HSE-004` ⭐ *CASE 2.0*
**BR References:** `BR-PLT-004`, `BR-PLT-008`, `BR-PLT-009`, `BR-STO-001`, `BR-RPT-002` ⭐ *CASE 2.0*

> Decided on 2026-11-16 after the developer reported that uploading an oversized photo opened a page of raw JSON instead of an error on the form. Amends [ADR-006](ADR-006_optimistic-locking-through-forms.md) (a stale edit no longer loses what was typed) and [ADR-002](ADR-002_server-rendered-monolith.md) (how forms report failures).

---

## Context — Bối cảnh
The 5 MB photo limit was set as the servlet container's limit (`spring.servlet.multipart.max-file-size: 5MB`). Tomcat refused a larger upload before any controller ran. Spring Security's CSRF filter then couldn't read the token from the unreadable form and refused the request as 403. Tomcat sent that to `/error`, where Spring Boot's `BasicErrorController` answers JSON unless the request asks for `text/html`. htmx sends `Accept: */*`, so the JSON was swapped into the page. `PageExceptionHandler.tooLarge()` was never reached.

The same path caught every failure that happens before a controller: a form left open until the session ended (CSRF), a broken upload, and any Tomcat-level refusal. Separately, several user mistakes that did reach a controller were reported on a full error page that left the form, or reported wrongly:

- a form value that can't be converted (`decision=BOGUS`, a number too large) answered **404 Page not found**;
- a stale edit answered a **409 page**, and what the person typed was lost (ADR-006's accepted trade-off);
- the report download answered a 400 page for a month typed by hand (Firefox desktop shows `<input type="month">` as text);
- a textarea within its browser `maxlength` could fail the server's limit, because browsers count a line break once but send `\r\n`.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Keep the container limit at 5 MB and only replace Spring Boot's error controller | Small change; no more JSON | The person still leaves the form and loses what they typed |
| B | Raise the container limit to a ceiling (20 MB file, 25 MB request), enforce 5 MB in the app, check the size in the browser first, and route every pre-controller failure to the HTML error page | The form shows the problem with what was typed; most people never upload at all; nothing renders JSON | Photos of 5–20 MB are uploaded before being refused when the browser check is bypassed |
| C | Option B, and also redirect over-ceiling uploads back to the form from the filter | Even a 30 MB upload stays on the form | The filter runs before Spring MVC, so it would need its own flash map handling; only reachable with JavaScript off |

## Decision — Quyết định
*We chose option B.*

1. **The 5 MB rule is the app's.** `PhotoStorage.MAX_BYTES` (5 MB) is checked in `PhotoFormat.extensionOf()`, which both stores call before writing. A larger photo throws `BusinessRuleException("The photo must be 5 MB or smaller")`, so `PageController.submit()` shows the form again with what was typed. The container limit is only a ceiling (`max-file-size: 20MB`, `max-request-size: 25MB`), and `server.tomcat.max-swallow-size: 100MB` makes Tomcat read and discard a refused body instead of dropping the connection.
2. **The browser checks first.** Photo inputs carry `data-max-bytes="5242880"` and a translated `data-too-large` message. `app.js` sets the input's custom validity on `change`, so the browser blocks the form and shows the message by the field.
3. **Uploads over the ceiling are read before Spring Security.** `MultipartLimitFilter` (order `HIGHEST_PRECEDENCE + 10`, just after character encoding) parses multipart requests with `StandardServletMultipartResolver`. Over the ceiling → 413, unreadable → 400. It records the failure as `jakarta.servlet.error.exception`, so the `DispatcherServlet` doesn't parse the body again while showing the error page.
4. **`PageErrorController` replaces `BasicErrorController`.** Pages always get `error.html`; only an original URI under `/api/` gets JSON (`{"error": "..."}`). 413 → "Photo too large"; 403 with a `CsrfException` → "This page has expired, reload and try again"; 404, other 4xx and 5xx (with the request ID) as before. `SecurityConfig` sets an access-denied handler that records the exception as `WebAttributes.ACCESS_DENIED_403`, which Spring's default handler only does when an error page is configured.
5. **A failed POST goes back to its page.** `PageExceptionHandler` handles `BusinessRuleException`, `OptimisticLockingFailureException`, an unconvertible form value and a missing form value: for a POST with a usable Referer it puts the message in the flash map and redirects to the Referer's path and query (`SamePage.of`). Without one, it shows the error page. An unconvertible `@PathVariable`, or any GET, is still 404.
6. **A stale edit keeps the form.** `PageController.submit()` catches `OptimisticLockingFailureException` and shows the form again with status 409 and "Someone else changed this while you were editing. Reload the page, check their changes and try again." `attempt()` turns it into a flash error.
7. **Smaller fixes:** `ReportController` sends a month that can't be exported back to `/team` with the reason (303 and a flash error); `Text.optional()` turns `\r\n` into `\n` before measuring.

## Rationale — Lý do
A person who made a mistake should see it next to what they did, with their work still there. Raising the ceiling costs some bandwidth only when the browser check is bypassed; the browser check covers everyone else. Replacing the error controller means no future filter-level failure can show JSON on a page.

## Consequences — Hệ quả
**Positive:**
- `ErrorPagesTest` covers the inline photo error, the redirect back, Referer safety, the CSRF reason and the error controller; `MultipartLimitFilterTest` and `TextTest` cover the filter and line breaks.
- Checked on a real Tomcat on 2026-11-16: 30 MB and 60 MB uploads → 413 page, missing CSRF token → 403 "This page has expired", broken upload → 400 page, `/api/` → JSON.

**Negative (trade-offs accepted):**
- Uploads of 5–20 MB are transferred before being refused when JavaScript is off.
- Above the ceiling the person gets the error page, not the form.
- `data-max-bytes` repeats `PhotoStorage.MAX_BYTES` in three templates.
- A redirect back keeps the query of the Referer but not what was typed in a button's form (comments, notes); those forms are short.

**Risks:**
- `MultipartLimitFilter` relies on Spring's detection of Tomcat's size message (`StandardMultipartHttpServletRequest.handleParseFailure`). A container change must keep `MultipartLimitFilterTest` passing.
- The redirect back trusts only the Referer's path; a browser that sends no Referer gets the error page.

**Compliance Impact:** ⭐ *CASE 2.0*
- None on personal data. Error messages and logs still carry no form values (ADR-024).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Returning error codes to pages and the API (gap G4).

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-025:
1. A size or format rule for uploads MUST be checked in the app (PhotoFormat/PhotoStorage) and thrown as
   BusinessRuleException; spring.servlet.multipart limits are a ceiling only, kept well above the rule.
2. A file input with a size rule MUST carry data-max-bytes and th:data-too-large so app.js checks it first.
3. Do NOT add a controller or bean that returns JSON for /error; PageErrorController owns /error, and only
   original URIs under /api/ get JSON.
4. A form that saves MUST use PageController.submit(...) so rule and stale-edit failures re-show the form with
   what was typed; a button MUST use attempt(...). Do NOT render the error page for a mistake the person can fix.
5. A redirect to "the page before" MUST go through SamePage.of(request); never redirect to a Referer as given.
6. Text typed into forms MUST go through Text.required/optional, which normalise line breaks before measuring.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
