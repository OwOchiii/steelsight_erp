-- Runs after every Flyway migrate, so tables added by later migrations are covered too.
--
-- Supabase exposes every table in the "public" schema through its public Data API. Turning on
-- Row Level Security blocks that API completely, because the only policies are for
-- steelsight_detection. Spring Boot keeps working because it connects as the table owner, and
-- Python keeps working through those policies.
--
-- This cannot live in a versioned migration: flyway_schema_history is in "public" and another
-- Flyway connection holds a lock on it during a migration, so the ALTER TABLE would wait forever.
DO $$
DECLARE t record;
BEGIN
FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND NOT rowsecurity LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
END LOOP;
END $$;
