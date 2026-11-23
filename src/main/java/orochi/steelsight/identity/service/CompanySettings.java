package orochi.steelsight.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS 3.2.25: the company option the attendance rules use (BR-36). */
@Service
@Transactional
@RequiredArgsConstructor
public class CompanySettings {

    private final AccessPolicy access;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public boolean halfDayForUnexplained(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT half_day_unexplained FROM company_setting", Boolean.class));
    }

    public void setHalfDayForUnexplained(Long actorId, boolean on) {
        access.require(actorId, Permissions.USER_MANAGE);
        jdbc.update("UPDATE company_setting SET half_day_unexplained = ?, version = version + 1", on);
    }
}
