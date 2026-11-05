package orochi.steelsight.web;

import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One week of shifts: a row per person and a cell per day. A shift belongs to the day it starts on. */
public final class ShiftGrid {

    public record Cell(LocalDate date, List<Shift> shifts) {
    }

    /** @param hours planned hours in the week, leaving out cancelled shifts */
    public record Row(AppUser worker, List<Cell> cells, BigDecimal hours) {
    }

    private ShiftGrid() {
    }

    /**
     * Everyone who can be planned gets a row, so an empty week still has a place to add a shift.
     * Anyone else with a shift that week gets one too, such as someone deactivated since.
     */
    public static List<Row> rows(List<AppUser> workers, List<Shift> shifts, List<LocalDate> days, SiteTime siteTime) {
        Map<Long, AppUser> people = new LinkedHashMap<>();
        workers.forEach(worker -> people.put(worker.getId(), worker));
        shifts.forEach(shift -> people.putIfAbsent(shift.getUser().getId(), shift.getUser()));
        Map<Long, List<Shift>> byPerson = shifts.stream().collect(Collectors.groupingBy(shift -> shift.getUser().getId()));

        List<AppUser> sorted = new ArrayList<>(people.values());
        sorted.sort(Comparator.comparing(AppUser::getFullName));
        return sorted.stream()
                .map(person -> row(person, byPerson.getOrDefault(person.getId(), List.of()), days, siteTime))
                .toList();
    }

    private static Row row(AppUser person, List<Shift> shifts, List<LocalDate> days, SiteTime siteTime) {
        return new Row(person, cells(shifts, days, siteTime), hours(shifts));
    }

    public static List<Cell> cells(List<Shift> shifts, List<LocalDate> days, SiteTime siteTime) {
        return days.stream()
                .map(day -> new Cell(day, shifts.stream()
                        .filter(shift -> siteTime.dateOf(shift.getStartAt()).equals(day)).toList()))
                .toList();
    }

    public static BigDecimal hours(List<Shift> shifts) {
        return shifts.stream()
                .filter(shift -> shift.getStatus() != ShiftStatus.CANCELLED)
                .map(Shift::getDurationHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
