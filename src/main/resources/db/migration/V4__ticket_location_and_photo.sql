-- Lets a Safety concern say where the hazard is and show it. Both are optional on any ticket.
ALTER TABLE ticket
    ADD COLUMN zone_id    bigint REFERENCES zone(id),
    ADD COLUMN photo_path text;   -- file in the private Supabase Storage bucket
