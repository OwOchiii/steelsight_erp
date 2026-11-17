package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.RequestContextUtils;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.reporting.service.MonthlyReportService;
import orochi.steelsight.security.AuthenticatedUser;

import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** The monthly Excel report that team leaders send to accounting. */
@Controller
@RequiredArgsConstructor
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final String TEAM_PAGE = "/team";

    private final MonthlyReportService reports;
    private final MonthlyReportWorkbook workbook;
    private final SiteTime siteTime;

    /** A month that can't be exported goes back to the Team page, where the month was chosen, with the reason. */
    @GetMapping("/team/report")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam(required = false) String month,
                                           HttpServletRequest request, HttpServletResponse response) throws IOException {
        YearMonth chosen;
        MonthlyReportService.Report report;
        try {
            chosen = monthOf(month);
            report = reports.build(user.getUserId(), chosen);
        } catch (BusinessRuleException e) {
            log.info("Rule broken: {}", e.getMessage());
            RequestContextUtils.getOutputFlashMap(request).put("error", e.getMessage());
            String target = request.getContextPath() + TEAM_PAGE;
            RequestContextUtils.saveOutputFlashMap(target, request, response);
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(target)).build();
        }
        byte[] file = workbook.write(report);
        String name = "steelsight-" + slug(report.team() == null ? "all-workers" : report.team()) + "-" + chosen + ".xlsx";
        // Pay data: record who took a copy of which month.
        log.info("Monthly report {} downloaded", name);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file);
    }

    /** Last month by default, because pay is worked out once a month has ended. */
    private YearMonth monthOf(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(siteTime.today()).minusMonths(1);
        }
        try {
            return YearMonth.parse(month.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("Choose a month to export");
        }
    }

    /** "Tổ 1" becomes "to-1", so the file name is plain ASCII on every system. */
    private String slug(String text) {
        String plain = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "team" : slug;
    }
}
