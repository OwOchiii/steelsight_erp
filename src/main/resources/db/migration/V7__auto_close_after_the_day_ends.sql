-- A visit nobody checked out of now stays open until the day ends, so someone working late still gets
-- a real check-out. The day is the one the shift ends on, which keeps a night shift open past midnight.
-- The recorded check-out is an estimate: the shift's end, or midnight when the visit has no shift.
-- The arguments change from grace periods to the site's time zone, so the old function is replaced.
DROP FUNCTION close_stale_check_ins(interval, interval);

CREATE FUNCTION close_stale_check_ins(p_time_zone text DEFAULT 'Asia/Ho_Chi_Minh') RETURNS integer AS $$
WITH closed AS (
    UPDATE check_in c
    SET check_out_at = d.close_at, auto_closed = true
    FROM (
        SELECT o.id,
               GREATEST(COALESCE(s.end_at,
                                 (((o.check_in_at AT TIME ZONE p_time_zone)::date + 1)::timestamp) AT TIME ZONE p_time_zone),
                        o.check_in_at + interval '1 minute') AS close_at,   -- still after check_in_at for a late arrival
               (COALESCE(s.end_at, o.check_in_at) AT TIME ZONE p_time_zone)::date AS visit_day
        FROM check_in o
                 LEFT JOIN shift s ON s.id = o.shift_id AND s.status <> 'CANCELLED'
        WHERE o.check_out_at IS NULL
    ) d
    WHERE c.id = d.id AND d.visit_day < (now() AT TIME ZONE p_time_zone)::date
    RETURNING c.id
)
SELECT count(*)::integer FROM closed;
$$ LANGUAGE sql;

REVOKE EXECUTE ON FUNCTION close_stale_check_ins(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION close_stale_check_ins(text) TO steelsight_detection;
