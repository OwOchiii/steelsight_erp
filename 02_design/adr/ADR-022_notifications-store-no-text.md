# ADR-022 — Notifications store no text

**Status:** `Accepted`
**Date:** 2026-11-05
**Deciders:** Nguyen Vu An — Developer; Claude Code (Opus 5.5) — AI pair programmer
**Context tags:** #i18n #notifications
**US References:** `US-WRK-005`, `US-HSE-006` ⭐ *CASE 2.0*
**BR References:** `BR-NTF-005`, `BR-NTF-006`, `BR-PLT-006` ⭐ *CASE 2.0*

> Recorded retrospectively from `V8` (`notification` table), `Notification.java`, `templates/notifications/list.html`.

---

## Context — Bối cảnh
Notifications are written by a database trigger (score levels) and by the ERP (HSE alerts). Readers choose English or Vietnamese, and can switch at any time.

## Options Considered — Các lựa chọn đã xem xét

| Option | Mô tả | Pros | Cons |
|--------|-------|------|------|
| A | Store the rendered message | Simple reads | The trigger can't localise; text frozen in one language |
| B | Store `kind` plus parameters (`score`, `period`, `safety_alert_id`); the template renders text from message keys | Every reader sees their language | Adding a kind needs a key in both files |

## Decision — Quyết định
*We chose option B.* `kind ∈ {SAFETY_WARNING, SAFETY_DANGER, HSE_VIOLATION_ALERT}`; CHECKs require `score` and `period` exactly for safety kinds and `safety_alert_id` exactly for HSE alerts. Read state is `read_at`; only the owner can mark it.

## Rationale — Lý do
The trigger can't know the reader's language; parameters keep the database language-neutral.

## Consequences — Hệ quả
**Positive:**
- `WorkerAccessPagesTest.notificationsAreYourOwnAndCanBeMarkedRead`; the bell count comes from `NavCounts.unread`.

**Negative (trade-offs accepted):**
- No free-text notifications.

**Risks:**
- Notifications are never deleted or expired.

**Compliance Impact:** ⭐ *CASE 2.0*
- Holds a worker's score per month; visible only to that worker.

**Decisions unlocked (ADR tiếp theo có thể viết):**
- —

---

## §AI Prompt Constraint ⭐ CASE 2.0 — BẮT BUỘC

```
Theo ADR-022:
1. A new notification kind MUST be added to NotificationKind, to the notification.kind CHECK by migration, and
   to both messages files; store parameters, never message text.
2. NotificationService methods act only on the actor's own notifications; markRead MUST refuse another user's.
3. HSE alert notifications are created only through Notification.hseAlert(...) in SafetyAlertService.
```

---
*Template version 2.0 — PrivacyOps Architecture Team — Tích hợp CASE 2.0*
*Section đánh dấu ⭐ là bổ sung mới từ CASE 2.0 methodology.*
