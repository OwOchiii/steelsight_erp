package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import orochi.steelsight.ppe.model.SafetyLevel;
import orochi.steelsight.reporting.service.MonthlyReportService.NoShowRow;
import orochi.steelsight.reporting.service.MonthlyReportService.Report;
import orochi.steelsight.reporting.service.MonthlyReportService.ViolationRow;
import orochi.steelsight.reporting.service.MonthlyReportService.VisitRow;
import orochi.steelsight.reporting.service.MonthlyReportService.WorkerRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/** Writes the monthly report as an .xlsx file, in the language of whoever downloads it. */
@Component
@RequiredArgsConstructor
public class MonthlyReportWorkbook {

    private static final String DATE_TIME = "yyyy-mm-dd hh:mm";
    private static final int HEADER_ROW = 5;

    private final MessageSource messages;
    private final Display display;
    private final SiteTime siteTime;

    public byte[] write(Report report) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(out, "Steelsight", "1.0");
        summary(workbook.newWorksheet(text("report.sheet.summary")), report);
        violations(workbook.newWorksheet(text("report.sheet.violations")), report);
        attendance(workbook.newWorksheet(text("report.sheet.attendance")), report);
        noShows(workbook.newWorksheet(text("report.sheet.noShows")), report);
        workbook.finish();
        return out.toByteArray();
    }

    private void summary(Worksheet sheet, Report report) {
        sheet.value(0, 0, text("report.title", report.period().month().format(DateTimeFormatter.ofPattern("MM/yyyy"))));
        sheet.style(0, 0).bold().fontSize(14).set();
        sheet.value(1, 0, report.team() == null ? text("report.allWorkers") : text("report.team", report.team()));
        sheet.value(2, 0, text("report.generated", display.dateTime(report.generatedAt())));
        sheet.value(3, 0, text("report.note"));
        header(sheet, "report.col.code", "report.col.name", "report.col.team", "report.col.score", "report.col.level",
                "report.col.violations", "report.col.noHardHat", "report.col.noVest", "report.col.otherViolations",
                "report.col.awaitingDecision", "report.col.shiftsScheduled", "report.col.shiftsWorked",
                "report.col.noShows", "report.col.hoursWorked", "report.col.timesLate", "report.col.minutesLate",
                "report.col.timesEarly", "report.col.minutesEarly", "report.col.missingCheckOuts",
                "report.col.needsReview");
        int r = HEADER_ROW + 1;
        for (WorkerRow row : report.workers()) {
            int c = 0;
            sheet.value(r, c++, row.code());
            sheet.value(r, c++, row.name());
            sheet.value(r, c++, row.team());
            sheet.value(r, c++, row.score());
            sheet.value(r, c++, row.level() == null ? null : display.label(SafetyLevel.valueOf(row.level())));
            sheet.value(r, c++, row.violations());
            sheet.value(r, c++, row.noHardHat());
            sheet.value(r, c++, row.noVest());
            sheet.value(r, c++, row.otherViolations());
            sheet.value(r, c++, row.awaitingDecision());
            sheet.value(r, c++, row.shiftsScheduled());
            sheet.value(r, c++, row.shiftsWorked());
            sheet.value(r, c++, row.noShows());
            sheet.value(r, c++, row.hoursWorked());
            sheet.value(r, c++, row.timesLate());
            sheet.value(r, c++, row.minutesLate());
            sheet.value(r, c++, row.timesEarly());
            sheet.value(r, c++, row.minutesEarly());
            sheet.value(r, c++, row.missingCheckOuts());
            sheet.value(r, c, row.needsReview());
            r++;
        }
        sheet.width(1, 26);
        sheet.width(2, 16);
        sheet.freezePane(2, HEADER_ROW + 1);
    }

    private void violations(Worksheet sheet, Report report) {
        header(sheet, 0, "report.col.detected", "report.col.code", "report.col.name", "report.col.type",
                "report.col.camera", "report.col.dispute");
        int r = 1;
        for (ViolationRow row : report.violations()) {
            dateTime(sheet, r, 0, row.detectedAt());
            sheet.value(r, 1, row.code());
            sheet.value(r, 2, row.name());
            sheet.value(r, 3, display.label(row.type()));
            sheet.value(r, 4, row.camera());
            sheet.value(r, 5, display.label(row.dispute()));
            r++;
        }
        sheet.width(0, 18);
        sheet.width(2, 26);
        sheet.width(3, 16);
        sheet.freezePane(0, 1);
    }

    private void attendance(Worksheet sheet, Report report) {
        header(sheet, 0, "report.col.checkIn", "report.col.checkOut", "report.col.code", "report.col.name",
                "report.col.shiftStart", "report.col.shiftEnd", "report.col.minutesLate", "report.col.minutesEarly",
                "report.col.checkOutEstimated", "report.col.needsReview");
        int r = 1;
        for (VisitRow row : report.visits()) {
            dateTime(sheet, r, 0, row.checkInAt());
            dateTime(sheet, r, 1, row.checkOutAt());
            sheet.value(r, 2, row.code());
            sheet.value(r, 3, row.name());
            dateTime(sheet, r, 4, row.shiftStart());
            dateTime(sheet, r, 5, row.shiftEnd());
            sheet.value(r, 6, row.minutesLate());
            sheet.value(r, 7, row.minutesEarly());
            sheet.value(r, 8, row.checkOutEstimated() ? text("report.yes") : text("report.no"));
            sheet.value(r, 9, row.needsReview() ? text("report.yes") : text("report.no"));
            r++;
        }
        for (int c : new int[]{0, 1, 4, 5}) {
            sheet.width(c, 18);
        }
        sheet.width(3, 26);
        sheet.freezePane(0, 1);
    }

    private void noShows(Worksheet sheet, Report report) {
        header(sheet, 0, "report.col.shiftStart", "report.col.shiftEnd", "report.col.code", "report.col.name");
        int r = 1;
        for (NoShowRow row : report.noShows()) {
            dateTime(sheet, r, 0, row.shiftStart());
            dateTime(sheet, r, 1, row.shiftEnd());
            sheet.value(r, 2, row.code());
            sheet.value(r, 3, row.name());
            r++;
        }
        sheet.width(0, 18);
        sheet.width(1, 18);
        sheet.width(3, 26);
        sheet.freezePane(0, 1);
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

    /** Excel dates have no time zone, so the cell holds site time, as pages show it. */
    private void dateTime(Worksheet sheet, int row, int column, OffsetDateTime value) {
        if (value == null) {
            return;
        }
        sheet.value(row, column, siteTime.toLocal(value));
        sheet.style(row, column).format(DATE_TIME).set();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
