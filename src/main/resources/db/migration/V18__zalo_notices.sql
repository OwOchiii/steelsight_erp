-- Zalo notices, next to Telegram (V17): the same messages, for workers who use Zalo instead. Both
-- are optional, and a worker can link either, both or neither.
--
-- Zalo Bot Platform chat ids are strings, not numbers like Telegram's. Like the Telegram chat, the
-- column is written with plain UPDATEs so linking never bumps app_user.version.
ALTER TABLE app_user ADD COLUMN zalo_chat_id text;
CREATE UNIQUE INDEX uq_app_user_zalo_chat ON app_user(zalo_chat_id) WHERE zalo_chat_id IS NOT NULL;

-- A Zalo bot link can't carry a code the way a t.me link does, so the home page shows a short code
-- and the worker sends it to the bot. One code per worker at a time; a new one replaces the old.
CREATE TABLE zalo_link_code (
    user_id    bigint      PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    code       text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);

-- So the detection service can send gate check-ins on Zalo too, once it learns how.
GRANT SELECT (zalo_chat_id) ON app_user TO steelsight_detection;
