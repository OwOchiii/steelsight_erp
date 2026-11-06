# ADR-023 — Host on Render (container) and Supabase (database, storage)

**Status:** `Accepted` *(GitHub Actions and ghcr.io are temporary; GitLab CI/CD and GitLab registry are the target)*
**Date:** 2026-11-06
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #ha #deployment #cost
**US References:** `US-ALL-001` ⭐ *CASE 2.0*
**BR References:** `BR-PLT-007`, `BR-STO-006` ⭐ *CASE 2.0*

> Recorded retrospectively from `Dockerfile`, `.github/workflows/ci.yml`, `application-prod.yaml`, `docs/deployment.md`, `docs/tech-stack.md`.

---

## Context — Bối cảnh
A student-scale project needs a public HTTPS site at no cost, reachable from the detection laptop, for a demo. The repository will move from GitHub to GitLab.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | VPS with Docker Compose | Full control, no sleep | Cost; ops work; TLS |
| B | Render free web service from a container image + Supabase free (PostgreSQL via session pooler, private Storage) | Free; HTTPS; managed DB; IPv4 pooler reachable from laptop | Sleeps after 15 min; 512 MB; in-memory sessions lost on sleep; Supabase may pause idle projects |
| C | Run everything on the laptop with a tunnel | No cost | Laptop is the single point; kept as demo backup (`cloudflared`) |

## Decision — Quyết định
*We chose option B, with C as the demo fallback.* Multi-stage Dockerfile (Temurin 17 JDK → JRE, non-root uid 1001, `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k`, `SPRING_PROFILES_ACTIVE=prod`). CI runs `mvnw verify` on every push/PR; from `master` it builds `ghcr.io/owochiii/steelsight_erp:<sha>` and calls Render's deploy hook with that tag. Health check `/actuator/health`. Secrets only in Render env, GitHub secrets and local `.env`.

## Rationale — Lý do
Zero cost and zero servers to run, with the exact tested image deployed.

## Consequences — Hệ quả
**Positive:**
- The `prod` profile can't be forgotten (set in the image); it refuses to start without Supabase settings.

**Negative (trade-offs accepted):**
- Cold start of a minute or more; everyone signed out on sleep; availability is not an SLA.
- Hikari pool capped at 5 for the shared pooler.

**Risks:**
- An old local database password exists in public Git history (`docs/deployment.md` says to use a new one).
- GitLab pipeline (Semgrep, Trivy) is planned but not yet in the repository.

**Compliance Impact:** ⭐ *CASE 2.0*
- Personal and biometric data are hosted by third parties (Render, Supabase); data location and processor agreements: **DPO review required** (cross-border transfer rules in Luật 91/2025/QH15 / NĐ 356/2025).

**Decisions unlocked (ADR tiếp theo có thể viết):**
- Future: GitLab CI/CD with Semgrep and Trivy gates.

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-023:
1. Configuration comes from environment variables with defaults in application.yaml; production overrides go in
   application-prod.yaml; NEVER commit a secret or a real .env.
2. The app MUST listen on ${PORT}, honour forwarded headers (server.forward-headers-strategy: framework), and
   keep memory within a 512 MB container.
3. Expose only /actuator/health; do NOT add actuator endpoints or public admin pages.
4. Do NOT rely on local disk persisting in production; use PhotoStorage (Supabase) for files.
5. Keep the Hikari pool small in prod (<= 5) because the Supabase pooler is shared with detection.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
