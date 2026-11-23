-- Telegram notices for workers who want them: their own check-ins and check-outs, and their monthly
-- work schedule. Not every worker uses Telegram, so the chat is optional; a worker without one
-- simply gets no Telegram messages.
--
-- The chat is written with plain UPDATEs, not through the AppUser entity, so linking Telegram never
-- bumps app_user.version and makes an admin's open edit form go stale.
ALTER TABLE app_user ADD COLUMN telegram_chat_id bigint;
CREATE UNIQUE INDEX uq_app_user_telegram_chat ON app_user(telegram_chat_id) WHERE telegram_chat_id IS NOT NULL;

-- A worker links Telegram by opening the bot with a one-time code from their ERP home page. One
-- code per worker at a time; a new one replaces the old.
CREATE TABLE telegram_link_code (
    user_id    bigint      PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    code       text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);

-- Which workers have been sent which month's schedule. After that, a change to one of their shifts in
-- that month sends them just the change, instead of a message on every save while a manager is still
-- building the month.
CREATE TABLE schedule_notice (
    user_id bigint      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    month   date        NOT NULL CHECK (extract(day FROM month) = 1),
    sent_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, month)
);

-- The detection service sends the gate check-in and check-out messages itself, because it is awake
-- when workers pass the gate and the ERP may be asleep. It needs to read the chat, nothing else new.
-- The detection_read policy on app_user from V1 already covers the row.
GRANT SELECT (telegram_chat_id) ON app_user TO steelsight_detection;
