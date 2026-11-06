# ADR-002 — One Spring Boot app with server-rendered pages

**Status:** `Accepted`
**Date:** 2026-11-05
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer (`chatlog/ai-log.md`, 2026-11-05 13:30 and 13:50)
**Context tags:** #frontend #deployment #simplicity
**US References:** `US-ALL-001` ⭐ *CASE 2.0*
**BR References:** `BR-PLT-003`, `BR-PLT-006` ⭐ *CASE 2.0*

> Recorded retrospectively from `docs/tech-stack.md` and `chatlog/ai-log.md` (the developer asked about moving to Next.js/React on Vercel; the advice was to stay).

---

## Context — Bối cảnh
The ERP is built by one developer in days, for one construction site, with a demo deadline. It must work on phones at the site, in Vietnamese and English, in light and dark themes, and look like an ERP (Odoo was the reference). The only machine-to-machine caller is the detection service. Hosting is Render's free tier (512 MB, sleeps when idle).

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Spring Boot REST API + separate React/Next.js SPA on Vercel | Rich UI; common pattern | Two apps, two deploys, CORS, token auth, a DTO layer, double the work |
| B | Spring Boot with Thymeleaf templates, htmx for partial updates, Tailwind built once and committed | One build, one deploy; session cookies and CSRF for free; no DTOs; no Node at build time | Less interactive than an SPA; server does all rendering |
| C | Spring Boot with Thymeleaf, full page reloads only | Simplest | Page blink on every click, which the developer rejected |

## Decision — Quyết định
*We chose option B.* Pages are Thymeleaf templates (`src/main/resources/templates/**`), forms post back to `@Controller`s that extend `PageController`, htmx swaps the page body, and the stylesheet and icon sprite are prebuilt and committed (`static/css/app.css`, `static/img/icons.svg`).

## Rationale — Lý do
Option B gives one artefact to build, test (MockMvc) and deploy, with Spring Security's session, CSRF and form login doing the security work. Entities go straight to templates, so there's no DTO layer to maintain (a decision logged on 2026-11-04 22:02). htmx removes the full-page blink that ruled out option C, without a JavaScript framework.

## Consequences — Hệ quả
**Positive:**
- One Docker image; the Dockerfile needs no Node.
- `PageController.submit()` and `attempt()` give every form the same error handling.
- Security tests use MockMvc against real pages (`PagesIntegrationTest`, `HsePagesIntegrationTest`).

**Negative (trade-offs accepted):**
- Templates read lazy JPA relations, so open-session-in-view must stay on, and a template can trigger extra queries.
- Restyling needs Node and the Tailwind CLI locally (`package.json`), and the built CSS must be committed.

**Risks:**
- A future mobile app or third party would need a REST layer that doesn't exist yet (only `/api/v1/detection/alerts`).

**Compliance Impact:** ⭐ *CASE 2.0*
- Photos and personal data are never exposed through a public API; they are rendered server-side after permission checks.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- ADR-019: The detection alert API, as a separate stateless filter chain.
- ADR-023: Hosting one container on Render.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-002:
1. New pages MUST be a @Controller extending PageController, returning a Thymeleaf view name; forms use
   PageController.submit(binding, model, response, action, showForm) and buttons use attempt(redirect, target, msg, action).
2. Do NOT add a SPA framework, a client-side router, or JSON endpoints for pages. Do NOT add REST endpoints
   outside /api/** (which is reserved for machine callers, ADR-019).
3. Every visible string MUST be a message key present in BOTH messages.properties and messages_vi.properties
   (TranslationsTest enforces this). Messages thrown from code stay in English and are translated by Display.message().
4. Do NOT edit static/css/app.css by hand; change Tailwind classes in templates and rebuild with the Tailwind CLI.
5. Forms MUST include the CSRF token (Thymeleaf th:action adds it). Never disable CSRF on the page filter chain.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
