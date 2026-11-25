package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.jobs.SiteJobs;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.ppe.service.ViolationService.CandidateGroup;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

/**
 * SRS workflows 1.2.5 to 1.2.7 on the ERP side: suggestions, the person list, confirm, stranger, dismiss,
 * overdue, alerts and the score notice (BR-46 to BR-55, BR-59), with the team's decision that a work zone
 * has any number of cameras and belongs to one team.
 */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class SrsViolationReviewTest extends PageTestSupport {

    @Autowired ViolationService violations;
    @Autowired ViolationRepository repository;
    @Autowired SiteJobs jobs;

    Long hse;
    Long otherHse;
    Long admin;
    Long leader;
    Long teamWorker;
    Long otherTeamWorker;
    Long officeWorker;
    Long team;
    Long gateZone;
    Long gateCamera;
    Long attendanceCamera;
    Long workZone;
    Long workCamera;
    Long secondWorkCamera;

    @BeforeEach
    void seed() {
        hse = userWithRole("SV-H1", "Hoa Officer", "HSE_OFFICER");
        otherHse = userWithRole("SV-H2", "Hung Officer", "HSE_OFFICER");
        admin = userWithRole("SV-A", "An Admin", "ADMIN");
        leader = userWithRole("SV-M", "Minh Leader", "MANAGER");
        teamWorker = userWithRole("SV-W1", "Tam Teamworker", "WORKER");
        otherTeamWorker = userWithRole("SV-W2", "Oanh Otherteam", "WORKER");
        officeWorker = userWithRole("SV-O", "Oscar Office", "WORKER");
        jdbc.update("UPDATE app_user SET staff_group = 'OFFICE' WHERE id = ?", officeWorker);
        team = team("Steel team", leader);
        joinTeam(teamWorker, team);
        joinTeam(otherTeamWorker, team("Sand team", userWithRole("SV-M2", "Mai Leader", "MANAGER")));

        Long gateIn = zone("Gate - attendance");
        jdbc.update("UPDATE zone SET kind = 'GATE_ATTENDANCE' WHERE id = ?", gateIn);
        attendanceCamera = camera("SV-CAM01", gateIn);
        jdbc.update("UPDATE camera SET role = 'ATTENDANCE' WHERE id = ?", attendanceCamera);
        gateZone = zone("Gate - PPE check");
        jdbc.update("UPDATE zone SET kind = 'GATE_PPE' WHERE id = ?", gateZone);
        gateCamera = camera("SV-CAM02", gateZone);
        workZone = zone("Area A - steel yard");
        jdbc.update("UPDATE zone SET kind = 'WORK_AREA', team_id = ? WHERE id = ?", team, workZone);
        workCamera = camera("SV-CAM03", workZone);
        secondWorkCamera = camera("SV-CAM05", workZone);   // a zone may have several cameras
    }

    /** BR-48: a new violation is PENDING_REVIEW with no person and appears in the queue. */
    @Test
    void br48_aNewViolationWaitsForReviewWithNoPerson() throws Exception {
        Long id = detect(workCamera, null);

        assertThat(status(id)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("SELECT user_id FROM violation WHERE id = ?", Long.class, id)).isNull();
        mvc.perform(get("/violations/review").with(as(hse))).andExpect(content().string(containsString("Area A - steel yard")));
    }

    /** The team's decision: a zone has several cameras; every one of them reports in that zone. */
    @Test
    void aZoneMayHaveSeveralCamerasAndEachReportsInIt() {
        Long first = detect(workCamera, null);
        Long second = detect(secondWorkCamera, null);

        assertThat(jdbc.queryForList("SELECT zone_id FROM violation WHERE id IN (?, ?)", Long.class, first, second))
                .containsOnly(workZone);
    }

    /** BR-46: at the gate, the suggestion is the only employee CAM-01 recorded within 30 seconds. */
    @Test
    void br46_theGateSuggestsWhoCheckedInWithinThirtySeconds() {
        jdbc.update("INSERT INTO check_in (user_id, camera_id, check_in_at, face_similarity) VALUES (?, ?, now() - interval '20 seconds', 88)",
                teamWorker, attendanceCamera);
        Long id = detect(gateCamera, null);

        assertThat(violations.suggestion(load(id))).hasValueSatisfying(person -> assertThat(person.getId()).isEqualTo(teamWorker));
    }

    @Test
    void br46_twoPeopleAtTheGateOrNobodyGivesNoSuggestion() {
        Long nobody = detect(gateCamera, null);
        assertThat(violations.suggestion(load(nobody))).isEmpty();

        jdbc.update("INSERT INTO check_in (user_id, camera_id, check_in_at, face_similarity) VALUES (?, ?, now() - interval '10 seconds', 88)",
                teamWorker, attendanceCamera);
        jdbc.update("INSERT INTO check_in (user_id, camera_id, check_in_at, face_similarity) VALUES (?, ?, now() + interval '10 seconds', 88)",
                otherTeamWorker, attendanceCamera);
        Long two = detect(gateCamera, null);
        assertThat(violations.suggestion(load(two))).isEmpty();
    }

    @Test
    void br46_aCheckInMoreThanThirtySecondsAwayIsNoSuggestion() {
        jdbc.update("INSERT INTO check_in (user_id, camera_id, check_in_at, face_similarity) VALUES (?, ?, now() - interval '45 seconds', 88)",
                teamWorker, attendanceCamera);
        Long id = detect(gateCamera, null);

        assertThat(violations.suggestion(load(id))).isEmpty();
    }

    /** BR-47: in a zone the camera's face match is a suggestion with its similarity, never a decision. */
    @Test
    void br47_theCamerasFaceMatchIsOnlyASuggestion() throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path, suggested_user_id, suggestion_similarity)
                VALUES (?, 'NO_HARD_HAT', now(), 'e.jpg', ?, 0.62) RETURNING id""", Long.class, workCamera, otherTeamWorker);

        assertThat(status(id)).isEqualTo("PENDING_REVIEW");
        mvc.perform(get("/violations/" + id).with(as(hse)))
                .andExpect(content().string(containsString("Oanh Otherteam (SV-W2), 0.620")));
    }

    /**
     * BR-49 and the team's decision: the suggestion first, then the zone's team (people with a shift then
     * first), then the people on site, then everyone.
     */
    @Test
    void br49_thePersonListStartsWithTheSuggestionThenTheZonesTeam() {
        jdbc.update("INSERT INTO check_in (user_id, check_in_at, method, recorded_by) VALUES (?, now() - interval '1 hour', 'MANUAL', ?)",
                officeWorker, hse);
        Long id = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path, suggested_user_id)
                VALUES (?, 'NO_VEST', now(), 'e.jpg', ?) RETURNING id""", Long.class, workCamera, otherTeamWorker);

        List<ViolationService.Candidate> list = violations.candidates(hse, load(id));

        assertThat(list.get(0).person().getId()).isEqualTo(otherTeamWorker);
        assertThat(list.get(0).group()).isEqualTo(CandidateGroup.SUGGESTED);
        assertThat(list.stream().filter(c -> c.group() == CandidateGroup.ZONE_TEAM).map(c -> c.person().getId()))
                .containsExactly(teamWorker);
        assertThat(list.stream().filter(c -> c.group() == CandidateGroup.ON_SITE).map(c -> c.person().getId()))
                .containsExactly(officeWorker);
        assertThat(list.stream().map(c -> c.person().getId())).contains(admin, leader).doesNotHaveDuplicates();
    }

    /** BR-48, BR-57: confirming a site worker costs 5 points (BR-59: and tells them). */
    @Test
    void br59_confirmingASiteWorkerCostsFivePointsAndTellsThem() throws Exception {
        Long id = detect(workCamera, null);

        confirm(id, teamWorker).andExpect(flash().attribute("success", "The violation was confirmed."));

        assertThat(status(id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT score FROM safety_scores(now()) WHERE user_id = ?", Integer.class, teamWorker)).isEqualTo(95);
        assertThat(jdbc.queryForObject("SELECT params->>'score' FROM notification WHERE user_id = ? AND kind = 'VIOLATION_CONFIRMED'",
                String.class, teamWorker)).isEqualTo("95");
        assertThat(jdbc.queryForObject("SELECT params->>'appealUntil' FROM notification WHERE user_id = ? AND kind = 'VIOLATION_CONFIRMED'",
                String.class, teamWorker)).isNotBlank();
    }

    /** BR-51: an office employee's violation is a reminder with no score. */
    @Test
    void br51_anOfficeEmployeeGetsAReminderAndNoScore() throws Exception {
        Long id = detect(workCamera, null);

        confirm(id, officeWorker);

        assertThat(status(id)).isEqualTo("REMINDER");
        assertThat(jdbc.queryForList("SELECT score FROM safety_scores(now()) WHERE user_id = ?", Integer.class, officeWorker)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND kind = 'REMINDER'", Integer.class, officeWorker))
                .isEqualTo(1);
    }

    /** BR-52: a stranger has no person and no score; the note can name the visitor's company. */
    @Test
    void br52_aStrangerHasNoPersonAndNoScore() throws Exception {
        Long id = detect(workCamera, null);

        mvc.perform(post("/violations/" + id + "/confirm").param("stranger", "true").param("note", "Concrete supplier driver")
                        .param("version", "0").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("success", "The violation was confirmed."));

        assertThat(jdbc.queryForObject("SELECT subject || '/' || review_status || '/' || review_note FROM violation WHERE id = ?", String.class, id))
                .isEqualTo("STRANGER/CONFIRMED/Concrete supplier driver");
    }

    /** BR-50, MSG36: a dismissal needs a reason; Other needs 10 to 500 characters. */
    @Test
    void br50_aDismissalNeedsAReasonAndOtherNeedsText() throws Exception {
        Long id = detect(workCamera, null);
        String msg36 = "Choose the reason. For Other, write the reason (10 to 500 characters).";

        mvc.perform(post("/violations/" + id + "/dismiss").param("version", "0").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", msg36));
        mvc.perform(post("/violations/" + id + "/dismiss").param("reason", "OTHER").param("otherReason", "short")
                        .param("version", "0").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", msg36));
        mvc.perform(post("/violations/" + id + "/dismiss").param("reason", "OTHER").param("otherReason", "A shadow, not a person")
                        .param("version", "0").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("success", "The violation was dismissed."));

        assertThat(jdbc.queryForObject("SELECT review_status || '/' || dismiss_reason FROM violation WHERE id = ?", String.class, id))
                .isEqualTo("DISMISSED/OTHER");
    }

    @Test
    void br50_eachListedReasonDismisses() {
        for (String reason : new String[]{"FALSE_DETECTION", "OUTSIDE_PERSON", "ALLOWED_REMOVAL"}) {
            Long id = detect(workCamera, null);
            violations.dismiss(hse, id, orochi.steelsight.ppe.model.DismissReason.valueOf(reason), null, 0);
            assertThat(status(id)).isEqualTo("DISMISSED");
        }
    }

    /** MSG37: the second officer to decide is refused. */
    @Test
    void msg37_aSecondDecisionIsRefused() throws Exception {
        Long id = detect(workCamera, null);
        confirm(id, teamWorker);

        mvc.perform(post("/violations/" + id + "/dismiss").param("reason", "FALSE_DETECTION").param("version", "1").with(as(otherHse)).with(csrf()))
                .andExpect(flash().attribute("error", "This violation was already decided by another officer. Reload the page to see the decision."));
    }

    /** SRS 3.2.17 "Take": the others see who is going; it doesn't lock the violation. */
    @Test
    void takeShowsWhoIsGoingButDoesNotLock() throws Exception {
        Long id = detect(workCamera, null);

        mvc.perform(post("/violations/" + id + "/take").with(as(hse)).with(csrf()));
        mvc.perform(get("/violations/review").with(as(otherHse))).andExpect(content().string(containsString("Hoa Officer is going")));
        confirm(id, teamWorker, otherHse, 1).andExpect(flash().attribute("success", "The violation was confirmed."));
    }

    /** BR-53: at 23:00 a pending violation becomes OVERDUE; it can still be decided until the lock. */
    @Test
    void br53_atElevenAPendingViolationBecomesOverdueAndCanStillBeDecided() throws Exception {
        Long id = detect(workCamera, null);

        jobs.dayEnd(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(1));

        assertThat(status(id)).isEqualTo("OVERDUE");
        confirm(id, teamWorker).andExpect(flash().attribute("success", "The violation was confirmed."));
    }

    /** MSG33: a violation of a locked month can't be reviewed. */
    @Test
    void msg33_aLockedMonthCannotBeReviewed() throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, 'NO_VEST', timestamptz '2026-09-10 09:00+07', 'e.jpg') RETURNING id""", Long.class, workCamera);
        jdbc.update("INSERT INTO month_lock (month) VALUES ('2026-09-01')");

        confirm(id, teamWorker).andExpect(flash().attribute("error", "The month 09/2026 is locked. No change is possible."));
        assertThat(status(id)).isEqualTo("PENDING_REVIEW");
    }

    /** BR-26, BR-54: each new violation alerts the HSE Officers on duty, and nobody else. */
    @Test
    void br54_eachViolationAlertsTheHseOfficersOnDuty() {
        jdbc.update("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '1 hour', now() + interval '3 hours', ?)""", hse, workZone, admin);

        Long first = detect(workCamera, null);
        detect(workCamera, null);

        assertThat(alerts(hse)).as("one alert per violation, no burst").isEqualTo(2);
        assertThat(alerts(otherHse)).isZero();
        assertThat(alerts(admin)).isZero();
        assertThat(jdbc.queryForObject("SELECT link FROM notification WHERE user_id = ? ORDER BY id LIMIT 1", String.class, hse))
                .isEqualTo("/violations/" + first);
    }

    @Test
    void br54_withNobodyOnDutyEveryHseOfficerAndAdminIsAlerted() {
        detect(workCamera, null);

        assertThat(alerts(hse)).isEqualTo(1);
        assertThat(alerts(otherHse)).isEqualTo(1);
        assertThat(alerts(admin)).isEqualTo(1);
        assertThat(alerts(teamWorker)).isZero();
    }

    /** BR-55, CR-03: the alert says what is missing where, with the zone's current name. */
    @Test
    void br55_theAlertNamesTheZoneAndTheMissingItemInVietnamese() throws Exception {
        mvc.perform(post("/zones/" + workZone).param("name", "Khu vực A").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "Saved successfully."));
        detect(workCamera, null);

        mvc.perform(get("/notifications").with(as(hse))
                        .cookie(new jakarta.servlet.http.Cookie("steelsight-language", "vi")))
                .andExpect(content().string(containsString("Phát hiện thiếu Mũ bảo hộ tại Khu vực A")));
    }

    /** CR-03, MSG48: zone names are unique; only work areas take a team. */
    @Test
    void cr03_zoneNamesAreUniqueAndOnlyWorkAreasHaveATeam() throws Exception {
        mvc.perform(post("/zones/" + workZone).param("name", "Gate - PPE check").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "A zone with this name already exists."));
        mvc.perform(post("/zones/" + gateZone + "/team").param("teamId", team.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "Only a work area can belong to a team"));
        mvc.perform(post("/zones/" + workZone).param("name", "X").with(as(hse)).with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    /** The Admin adds a zone from the Zones page, with the same name rules as a rename. */
    @Test
    void theAdminAddsAZone() throws Exception {
        mvc.perform(get("/zones").with(as(admin)))
                .andExpect(content().string(containsString("/zones/new")));
        mvc.perform(get("/zones/new").with(as(admin)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        mvc.perform(post("/zones/new").param("name", "  Casting line ").param("kind", "WORK_AREA")
                        .param("teamId", team.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The zone was added."));
        assertThat(jdbc.queryForMap("SELECT kind, team_id FROM zone WHERE name = 'Casting line'"))
                .containsEntry("kind", "WORK_AREA").containsEntry("team_id", team);

        mvc.perform(post("/zones/new").param("name", "casting LINE").param("kind", "WORK_AREA").with(as(admin)).with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(content().string(containsString("A zone with this name already exists.")));
        mvc.perform(post("/zones/new").param("name", "Gate 2").param("kind", "GATE_PPE")
                        .param("teamId", team.toString()).with(as(admin)).with(csrf()))
                .andExpect(content().string(containsString("Only a work area can belong to a team")));
        mvc.perform(post("/zones/new").param("name", "X").param("kind", "WORK_AREA").with(as(admin)).with(csrf()))
                .andExpect(content().string(containsString("A zone name has 2 to 50 characters")));
        mvc.perform(get("/zones/new").with(as(hse)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(post("/zones/new").param("name", "Sneaky").param("kind", "WORK_AREA").with(as(hse)).with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM zone WHERE name IN ('Gate 2', 'X', 'Sneaky')", Long.class)).isZero();
    }

    /** SRS 3.2.23, V26: each zone gets the next free code of its kind, shown on Zones and never changed. */
    @Test
    void aNewZoneGetsTheNextFreeCodeOfItsKind() throws Exception {
        java.util.List<String> areasBefore = jdbc.queryForList("SELECT code FROM zone", String.class);
        String nextArea = java.util.stream.IntStream.rangeClosed(0, 25)
                .mapToObj(i -> "AREA-" + (char) ('A' + i)).filter(code -> !areasBefore.contains(code)).findFirst().orElseThrow();

        mvc.perform(post("/zones/new").param("name", "Rebar yard").param("kind", "WORK_AREA").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The zone was added."));
        assertThat(jdbc.queryForObject("SELECT code FROM zone WHERE name = 'Rebar yard'", String.class)).isEqualTo(nextArea);
        mvc.perform(get("/zones").with(as(admin))).andExpect(content().string(containsString(nextArea)));

        // Renaming keeps the code; changing the code itself is refused by the database.
        Long id = jdbc.queryForObject("SELECT id FROM zone WHERE name = 'Rebar yard'", Long.class);
        mvc.perform(post("/zones/" + id).param("name", "Rebar and formwork yard").with(as(admin)).with(csrf()));
        assertThat(jdbc.queryForObject("SELECT code FROM zone WHERE id = ?", String.class, id)).isEqualTo(nextArea);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE zone SET code = 'AREA-Q9' WHERE id = ?", id))
                .hasMessageContaining("cannot change");
    }

    /** The Admin links a detection app's camera code to a zone; the zone's kind sets what it records. */
    @Test
    void theAdminLinksMovesAndRemovesCameras() throws Exception {
        Long attendanceGate = jdbc.queryForObject(
                "INSERT INTO zone (name, kind) VALUES ('Main gate', 'GATE_ATTENDANCE') RETURNING id", Long.class);

        mvc.perform(post("/zones/cameras").param("code", " CAM-7968 ").param("zoneId", workZone.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The camera was linked to the zone."));
        assertThat(jdbc.queryForMap("SELECT zone_id, role FROM camera WHERE camera_code = 'CAM-7968'"))
                .containsEntry("zone_id", workZone).containsEntry("role", "PPE");
        mvc.perform(get("/zones").with(as(admin))).andExpect(content().string(containsString("CAM-7968")));

        // The same code again moves it; the gate attendance zone makes it an attendance camera.
        mvc.perform(post("/zones/cameras").param("code", "CAM-7968").param("zoneId", attendanceGate.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The camera was linked to the zone."));
        assertThat(jdbc.queryForMap("SELECT zone_id, role FROM camera WHERE camera_code = 'CAM-7968'"))
                .containsEntry("zone_id", attendanceGate).containsEntry("role", "ATTENDANCE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM camera WHERE camera_code = 'CAM-7968'", Long.class)).isEqualTo(1);

        // A camera that recorded a violation moves, but its violation keeps the zone it was seen in.
        Long seen = detect(workCamera, null);
        mvc.perform(post("/zones/cameras").param("code", "SV-CAM03").param("zoneId", gateZone.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The camera was linked to the zone."));
        assertThat(jdbc.queryForObject("SELECT zone_id FROM violation WHERE id = ?", Long.class, seen)).isEqualTo(workZone);
        mvc.perform(post("/zones/cameras/" + workCamera + "/remove").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "This camera has records, so it can't be removed. Link it to another zone instead."));

        Long unused = jdbc.queryForObject("SELECT id FROM camera WHERE camera_code = 'CAM-7968'", Long.class);
        mvc.perform(post("/zones/cameras/" + unused + "/remove").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The camera was removed."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM camera WHERE id = ?", Long.class, unused)).isZero();

        mvc.perform(post("/zones/cameras").param("code", "CAM 1").param("zoneId", workZone.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "A camera code has 2 to 50 letters, digits, dots, dashes or underscores"));
        mvc.perform(post("/zones/cameras").param("code", "CAM-X").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "Choose the zone the camera watches"));
        mvc.perform(post("/zones/cameras").param("code", "CAM-HSE").param("zoneId", workZone.toString()).with(as(hse)).with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(post("/zones/cameras/" + secondWorkCamera + "/remove").with(as(hse)).with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    /** SRS 3.1.3: a Manager has no review rights; a worker sees only their own decided violations. */
    @Test
    void onlyHseAndTheAdminReview() throws Exception {
        Long id = detect(workCamera, null);

        confirm(id, teamWorker, leader, 0).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        confirm(id, teamWorker, teamWorker, 0).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        confirm(id, teamWorker, admin, 0).andExpect(flash().attribute("success", "The violation was confirmed."));
    }

    private Long detect(Long camera, Long namedPerson) {
        return jdbc.queryForObject("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, 'NO_HARD_HAT', now(), 'evidence.jpg') RETURNING id""", Long.class, namedPerson, camera);
    }

    private org.springframework.test.web.servlet.ResultActions confirm(Long id, Long person) throws Exception {
        return confirm(id, person, hse, 0);
    }

    private org.springframework.test.web.servlet.ResultActions confirm(Long id, Long person, Long officer, long version) throws Exception {
        return mvc.perform(post("/violations/" + id + "/confirm").param("personId", person.toString())
                .param("version", String.valueOf(version)).with(as(officer)).with(csrf()));
    }

    private Violation load(Long id) {
        flush();
        em.clear();
        return repository.findById(id).orElseThrow();
    }

    private String status(Long id) {
        return jdbc.queryForObject("SELECT review_status FROM violation WHERE id = ?", String.class, id);
    }

    private int alerts(Long user) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND kind = 'VIOLATION_ALERT'", Integer.class, user);
    }
}
