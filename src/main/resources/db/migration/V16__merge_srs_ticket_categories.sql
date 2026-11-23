-- Merges the ticket categories of the requirements (SRS BR-46) with the ones already in use. Three of
-- the SRS's six already exist under other names: PPE recognition appeal is "PPE dispute", safety
-- report is "Safety concern", and leave request is "Leave request". The other three are added here,
-- handled by Admin like every category that isn't a safety matter. Existing categories and tickets
-- are unchanged. "Other" goes in last, so it's at the end of the new-ticket list.
INSERT INTO ticket_category (category_name, handler_role_id)
SELECT v.name, r.id
FROM (VALUES (1, 'Attendance correction'),
             (2, 'Personal information update'),
             (3, 'Other')) AS v(position, name)
         JOIN role r ON r.role_name = 'ADMIN'
ORDER BY v.position
ON CONFLICT (category_name) DO NOTHING;
