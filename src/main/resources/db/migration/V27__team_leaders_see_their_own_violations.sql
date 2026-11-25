-- A team leader (MANAGER) works on site and has a monthly safety score (safety_scores() counts WORKER and
-- MANAGER, BR-56), so, as the SRS screen authorization says (3.1.3), they open My Violations and Safety
-- Score and can appeal a violation confirmed against them (BR-64), like a worker. The permission is
-- own-only: ViolationService.get still refuses anyone else's violation, and the Review Queue and the
-- violation list still need VIOLATION_REVIEW and VIOLATION_READ_ALL, which managers don't hold.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.code = 'VIOLATION_READ_OWN'
WHERE r.role_name = 'MANAGER'
ON CONFLICT DO NOTHING;
