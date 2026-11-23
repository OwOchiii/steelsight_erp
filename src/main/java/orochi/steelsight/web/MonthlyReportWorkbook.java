package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import orochi.steelsight.reporting.service.MonthlyReportService.AbsenceRow;
import orochi.steelsight.reporting.service.MonthlyReportService.EmployeeRow;
import orochi.steelsight.reporting.service.MonthlyReportService.Report;
import orochi.steelsight.reporting.service.MonthlyReportService.ViolationRow;
import orochi.steelsight.reporting.service.MonthlyReportService.VisitRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes the monthly report as .xlsx (SRS BR-71, CM-08): the sheets Site summary, Office summary,
 * Violations, Attendance and Absences, one header row each, Vietnam time, hours with 2 decimals. A file
 * made before the month lock says "Provisional" above the header.
 */
@Component
@RequiredArgsConstructor
public class MonthlyReportWorkbook {

    private static final String DATE_TIME = "dd/mm/yyyy hh:mm";
    private static final int HEADER_ROW = 4;

    private final MessageSource messages;
    private final Display display;
    private final SiteTime siteTime;

    public byte[] write(Report report) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(out, "Steelsight", "1.0");
        summary(workbook.newWorksheet(text("report.sheet.site")), report, report.site(), true);
        summary(workbook.newWorksheet(text("report.sheet.office")), report, report.office(), false);
        violations(workbook.newWorksheet(text("report.sheet.violations")), report);
        attendance(workbook.newWorksheet(text("report.sheet.attendance")), report);
        absences(workbook.newWorksheet(text("report.sheet.absences")), report);
        workbook.finish();
        return out.toByteArray();
    }

    /** The file name of SRS CM-08: SteelSight_<scope>_<yyyy-mm>.xlsx. */
    public static String fileName(Report report, String scopeSlug) {
        return "SteelSight_" + scopeSlug + "_" + report.period().month() + ".xlsx";
    }

    private void title(Worksheet sheet, Report report) {
        sheet.value(0, 0, text("report.title", report.period().month().format(DateTimeFormatter.ofPattern("MM/yyyy"))));
        sheet.style(0, 0).bold().fontSize(14).set();
        sheet.value(1, 0, report.team() == null ? text("report.allWorkers") : text("report.team", report.team()));
        sheet.value(2, 0, text("report.generated", display.dateTime(report.generatedAt()))
                + (report.provisional() ? " - " + text("common.provisional") : ""));
        if (report.provisional()) {
            sheet.style(2, 0).bold().fontColor("B42318").set();
        }
    }

    private void summary(Worksheet sheet, Report report, List<EmployeeRow> rows, boolean site) {
        title(sheet, report);
        sheet.value(3, 0, text("report.note"));
        if (site) {
            header(sheet, "report.col.code", "report.col.name", "report.col.team", "report.col.shiftsScheduled",
                    "report.col.shiftsWorked", "report.col.absences", "report.col.paidHours", "report.col.validOvertime",
                    "report.col.pendingOvertime", "report.col.timesLate", "report.col.minutesLate", "report.col.timesEarly",
                    "report.col.minutesEarly", "report.col.incomplete", "report.col.paidLeave", "report.col.unpaidLeave",
                    "report.col.violations", "report.col.score", "report.col.level", "report.col.reduction");
        } else {
            header(sheet, "report.col.code", "report.col.name", "report.col.team", "report.col.shiftsScheduled",
                    "report.col.shiftsWorked", "report.col.absences", "report.col.paidHours", "report.col.validOvertime",
                    "report.col.pendingOvertime", "report.col.timesLate", "report.col.minutesLate", "report.col.timesEarly",
                    "report.col.minutesEarly", "report.col.incomplete", "report.col.paidLeave", "report.col.unpaidLeave",
                    "report.col.reminders");
        }
        int r = HEADER_ROW + 1;
        for (EmployeeRow row : rows) {
            var a = row.attendance();
            int c = 0;
            sheet.value(r, c++, row.code());
            sheet.value(r, c++, row.name());
            sheet.value(r, c++, row.team());
            sheet.value(r, c++, a.planned());
            sheet.value(r, c++, a.worked());
            sheet.value(r, c++, a.absent());
            sheet.value(r, c++, a.paidHours());
            sheet.value(r, c++, a.validOvertimeHours());
            sheet.value(r, c++, a.pendingOvertimeHours());
            sheet.value(r, c++, a.timesLate());
            sheet.value(r, c++, a.minutesLate());
            sheet.value(r, c++, a.timesEarly());
            sheet.value(r, c++, a.minutesEarly());
            sheet.value(r, c++, a.incomplete());
            sheet.value(r, c++, a.paidLeaveHours());
            sheet.value(r, c++, a.unpaidLeaveHours());
            if (site) {
                sheet.value(r, c++, row.violations());
                sheet.value(r, c++, row.score());
                sheet.value(r, c++, row.level() == null ? null : display.label(row.level()));
                sheet.value(r, c, row.reductionPercent());
            } else {
                sheet.value(r, c, row.reminders());
            }
            r++;
        }
    }

    private void violations(Worksheet sheet, Report report) {
        header(sheet, 0, "report.col.detected", "report.col.zone", "report.col.type", "report.col.status",
                "report.col.code", "report.col.name", "report.col.reviewer", "report.col.appeal");
        int r = 1;
        for (ViolationRow row : report.violations()) {
            dateTime(sheet, r, 0, row.detectedAt());
            sheet.value(r, 1, row.zone());
            sheet.value(r, 2, text("enum." + row.type()));
            sheet.value(r, 3, text("enum." + row.status()));
            sheet.value(r, 4, row.code());
            sheet.value(r, 5, row.person());
            sheet.value(r, 6, row.reviewer());
            sheet.value(r, 7, text("enum." + row.appeal()));
            r++;
        }
    }

    private void attendance(Worksheet sheet, Report report) {
        header(sheet, 0, "report.col.checkIn", "report.col.checkOut", "report.col.code", "report.col.name",
                "report.col.shiftStart", "report.col.shiftEnd", "report.col.method", "report.col.incomplete");
        int r = 1;
        for (VisitRow row : report.visits()) {
            dateTime(sheet, r, 0, row.checkInAt());
            if (!row.closedBySystem()) {
                dateTime(sheet, r, 1, row.checkOutAt());
            }
            sheet.value(r, 2, row.code());
            sheet.value(r, 3, row.name());
            dateTime(sheet, r, 4, row.shiftStart());
            dateTime(sheet, r, 5, row.shiftEnd());
            sheet.value(r, 6, row.method());
            sheet.value(r, 7, row.incomplete() ? text("report.yes") : text("report.no"));
            r++;
        }
    }

    private void absences(Worksheet sheet, Report report) {
        header(sheet, 0, "report.col.shiftStart", "report.col.shiftEnd", "report.col.code", "report.col.name", "report.col.zone");
        int r = 1;
        for (AbsenceRow row : report.absences()) {
            dateTime(sheet, r, 0, row.shiftStart());
            dateTime(sheet, r, 1, row.shiftEnd());
            sheet.value(r, 2, row.code());
            sheet.value(r, 3, row.name());
            sheet.value(r, 4, row.zone());
            r++;
        }
    }

    private void header(Worksheet sheet, String... keys) {
        header(sheet, HEADER_ROW, keys);
    }

    private void header(Worksheet sheet, int row, String... keys) {
        for (int c = 0; c < keys.length; c++) {
            sheet.value(row, c, text(keys[c]));
            sheet.style(row, c).bold().fillColor("E7E5E0").wrapText(true).set();
        }
    }

    /** Excel dates have no time zone, so the cell holds Vietnam time, as pages show it (CM-01). */
    private void dateTime(Worksheet sheet, int row, int column, OffsetDateTime value) {
        if (value == null) {
            return;
        }
        sheet.value(row, column, siteTime.toLocal(value));
        sheet.style(row, column).format(DATE_TIME).set();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
