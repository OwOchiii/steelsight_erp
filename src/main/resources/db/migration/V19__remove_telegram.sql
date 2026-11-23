-- Telegram is gone: the SRS sends every notice through Zalo only (SRS 1.1.2, OR-05). This undoes the
-- Telegram half of V17. schedule_notice from V17 stays, because the Zalo schedule notices use it.
--
-- A new migration rather than an edited V17, so a database that already ran V17 still validates.
-- Dropping the column also drops its unique index and the detection role's column grant.
DROP TABLE IF EXISTS telegram_link_code;
ALTER TABLE app_user DROP COLUMN IF EXISTS telegram_chat_id;
