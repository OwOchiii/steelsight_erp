-- A visit nobody checked out of closes 2 hours after its shift ends, as the requirements say (SRS
-- BR-33). V7 kept it open until the day ended so overtime got a real check-out; that is given up so
-- a forgotten check-out stops blocking the gate sooner and shows as Missing check-out the same day.
-- The recorded check-out is still an estimate: the shift's end, flagged auto_closed.
--
-- A worker who checks in after the shift already ended gets 2 hours from check-in, so the gate
-- doesn't close a visit that just started. A visit with no shift (or a cancelled one) still closes
-- at site midnight once its day has passed, as in V7.
--
-- ADR-013: same signature, but dropped and recreated so the grants are re-applied in one place.
DROP FUNCTION close_stale_check_ins(text);

CREATE FUNCTION close_stale_check_ins(p_time_zone text DEFAULT 'Asia/Ho_Chi_Minh') RETURNS integer AS $$
WITH closed AS (
    UPDATE check_in c
    SET check_out_at = d.close_at, auto_closed = true
    FROM (
        SELECT o.id,
               GREATEST(COALESCE(s.end_at,
                                 (((o.check_in_at AT TIME ZONE p_time_zone)::date + 1)::timestamp) AT TIME ZONE p_time_zone),
                        o.check_in_at + interval '1 minute') AS close_at,   -- still after check_in_at for a late arrival
               CASE
                   WHEN s.id IS NOT NULL THEN GREATEST(s.end_at, o.check_in_at) + interval '2 hours'
                   ELSE (((o.check_in_at AT TIME ZONE p_time_zone)::date + 1)::timestamp) AT TIME ZONE p_time_zone
               END AS deadline
        FROM check_in o
                 LEFT JOIN shift s ON s.id = o.shift_id AND s.status <> 'CANCELLED'
        WHERE o.check_out_at IS NULL
    ) d
    WHERE c.id = d.id AND d.deadline <= now()
    RETURNING c.id
)
SELECT count(*)::integer FROM closed;
$$ LANGUAGE sql;

REVOKE EXECUTE ON FUNCTION close_stale_check_ins(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION close_stale_check_ins(text) TO steelsight_detection;
