package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.storage.PhotoAlbum;
import orochi.steelsight.storage.PhotoStorage;
import orochi.steelsight.ticketing.model.CategoryCode;
import orochi.steelsight.ticketing.model.Direction;
import orochi.steelsight.ticketing.model.LeaveType;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketCategory;
import orochi.steelsight.ticketing.model.TicketOutcome;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.service.TicketService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** SRS 3.2.20 to 3.2.22: New Ticket, My Tickets, the Ticket Queue and Ticket Detail. */
@Controller
@RequiredArgsConstructor
public class TicketController extends PageController {

    private final TicketService tickets;
    private final ShiftService shifts;
    private final CheckInService checkIns;
    private final PhotoStorage photos;
    private final SiteTime siteTime;

    @GetMapping("/tickets")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) TicketStatus status,
                       @RequestParam(defaultValue = "false") boolean team,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        boolean leader = user.can(Permissions.TICKET_READ_TEAM) && !user.can(Permissions.TICKET_READ_ALL);
        model.addAttribute("leader", leader);
        model.addAttribute("team", team && leader);
        model.addAttribute("status", status);
        model.addAttribute("tickets", team && leader
                ? tickets.listTeam(user.getUserId(), pageOf(page))
                : tickets.listOwn(user.getUserId(), status, pageOf(page)));
        if (leader && !team) {
            model.addAttribute("teamTickets", tickets.listTeam(user.getUserId(), pageOf(0)));
        }
        return "tickets/list";
    }

    /** The Admin queue: every ticket, by category and status, with its age in working days (BR-69). */
    @GetMapping("/tickets/queue")
    public String queue(@AuthenticationPrincipal AuthenticatedUser user,
                        @RequestParam(required = false) Long categoryId,
                        @RequestParam(required = false) TicketStatus status,
                        @RequestParam(defaultValue = "false") boolean resolved,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        TicketStatus shown = status != null ? status : resolved ? TicketStatus.RESOLVED : null;
        model.addAttribute("tickets", tickets.listQueue(user.getUserId(), new TicketService.QueueFilter(categoryId, shown), pageOf(page)));
        model.addAttribute("categories", tickets.listCategoriesForNewTicket(user.getUserId()));
        model.addAttribute("appealCategory", tickets.category(CategoryCode.PPE_APPEAL).orElse(null));
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("status", shown);
        model.addAttribute("resolved", shown == TicketStatus.RESOLVED);
        model.addAttribute("ticketService", tickets);
        return "tickets/queue";
    }

    /** New Ticket; another page can fix the category and the shift or visit (SRS 3.2.20 #1). */
    @GetMapping("/tickets/new")
    public String newTicket(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestParam(required = false) CategoryCode category,
                            @RequestParam(required = false) Long shiftId,
                            @RequestParam(required = false) Long checkInId, Model model) {
        TicketForm form = new TicketForm();
        if (category != null) {
            tickets.category(category).ifPresent(found -> form.setCategoryId(found.getId()));
        }
        form.setShiftId(shiftId);
        form.setCheckInId(checkInId);
        model.addAttribute("form", form);
        return showForm(user, model);
    }

    @PostMapping("/tickets")
    public String create(@AuthenticationPrincipal AuthenticatedUser user,
                         @ModelAttribute("form") TicketForm form, BindingResult binding,
                         @RequestParam(required = false) MultipartFile photo,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            String storedPhoto = photos.store(PhotoAlbum.TICKET, photo);
            try {
                Ticket ticket = tickets.create(user.getUserId(), details(user, form, storedPhoto));
                redirect.addFlashAttribute("success", "Your ticket was sent.");
                return "redirect:/tickets/" + ticket.getId();
            } catch (RuntimeException e) {
                photos.delete(PhotoAlbum.TICKET, storedPhoto);
                throw e;
            }
        }, () -> showForm(user, model));
    }

    @GetMapping("/tickets/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        Ticket ticket = tickets.get(user.getUserId(), id);
        boolean canHandle = tickets.canHandle(user.getUserId(), id);
        boolean own = ticket.getCreatedBy().getId().equals(user.getUserId());
        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", tickets.listComments(user.getUserId(), id));
        model.addAttribute("participants", tickets.listParticipants(user.getUserId(), id));
        model.addAttribute("canHandle", canHandle && ticket.isUnresolved());
        model.addAttribute("canComment", tickets.canComment(user.getUserId(), id));
        model.addAttribute("ownTicket", own);
        model.addAttribute("canCancel", own && ticket.getStatus() == TicketStatus.OPEN);
        model.addAttribute("ownAsAdmin", own && user.can(Permissions.TICKET_UPDATE_ALL) && ticket.isUnresolved());
        if (canHandle && ticket.isUnresolved()) {
            model.addAttribute("assignees", tickets.listPossibleAssignees(user.getUserId(), id));
            model.addAttribute("hseToAsk", tickets.listHseToAsk(user.getUserId(), id));
        }
        return "tickets/detail";
    }

    @PostMapping("/tickets/{id}/comments")
    public String comment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) String body, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "Your comment was added.",
                () -> tickets.addComment(user.getUserId(), id, body));
    }

    @PostMapping("/tickets/{id}/assignee")
    public String assign(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @RequestParam(required = false) Long assigneeId, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "The ticket was assigned.",
                () -> tickets.assign(user.getUserId(), id, assigneeId));
    }

    /** "Take": IN_PROGRESS with this Admin. */
    @PostMapping("/tickets/{id}/take")
    public String take(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "The ticket was assigned.",
                () -> tickets.assign(user.getUserId(), id, user.getUserId()));
    }

    @PostMapping("/tickets/{id}/ask")
    public String askHse(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @RequestParam(required = false) Long officerId, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "The HSE Officer was asked to comment.",
                () -> tickets.askHse(user.getUserId(), id, officerId));
    }

    @PostMapping("/tickets/{id}/facts")
    public String facts(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                        @RequestParam(required = false) String hseFacts, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "Saved successfully.",
                () -> tickets.saveHseFacts(user.getUserId(), id, hseFacts));
    }

    /** Approve or Reject a request. An attendance error is approved through the Attendance Form. */
    @PostMapping("/tickets/{id}/decide")
    public String decide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @RequestParam(required = false) TicketOutcome outcome,
                         @RequestParam(required = false) String note, RedirectAttributes redirect) {
        Ticket ticket = tickets.get(user.getUserId(), id);
        if (outcome == TicketOutcome.APPROVED && ticket.getCategory().is(CategoryCode.ATTENDANCE_ERROR)) {
            try {
                tickets.prepareAttendanceApproval(user.getUserId(), id, note);
            } catch (BusinessRuleException e) {
                redirect.addFlashAttribute("error", e.getMessage());
                return "redirect:/tickets/" + id;
            }
            return "redirect:/attendance/new?ticket=" + id;
        }
        return attempt(redirect, "/tickets/" + id, "The ticket was resolved.",
                () -> tickets.decide(user.getUserId(), id, outcome, note));
    }

    /** Kept for the Other category's simple resolve form. */
    @PostMapping("/tickets/{id}/resolve")
    public String resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "The ticket was resolved.",
                () -> tickets.resolve(user.getUserId(), id, note));
    }

    @PostMapping("/tickets/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "The ticket was cancelled.",
                () -> tickets.cancel(user.getUserId(), id));
    }

    /** Goes through the ticket's read check, because the photo is as private as the ticket. */
    @GetMapping("/tickets/{id}/photo")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        Ticket ticket = tickets.get(user.getUserId(), id);
        if (ticket.getPhotoPath() == null) {
            throw new NotFoundException("Photo of ticket", id);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photos.contentType(ticket.getPhotoPath())))
                .cacheControl(CacheControl.noStore())
                .body(photos.load(PhotoAlbum.TICKET, ticket.getPhotoPath()));
    }

    private TicketService.NewTicket details(AuthenticatedUser user, TicketForm form, String photo) {
        OffsetDateTime gate = null;
        OffsetDateTime from = null;
        OffsetDateTime to = null;
        if (form.getShiftId() != null && (form.getGateTime() != null || form.getLeaveFrom() != null || form.getLeaveTo() != null)) {
            Shift shift = shifts.getOwn(user.getUserId(), form.getShiftId());
            LocalDate day = siteTime.dateOf(shift.getStartAt());
            gate = at(day, form.getGateTime());
            from = at(day, form.getLeaveFrom());
            to = at(day, form.getLeaveTo());
        }
        return new TicketService.NewTicket(form.getCategoryId(), form.getSubject(), form.getDescription(), photo,
                form.getShiftId(), form.getCheckInId(), form.getDirection(), gate, form.getLeaveType(), from, to);
    }

    private OffsetDateTime at(LocalDate day, LocalTime time) {
        return time == null ? null : siteTime.toInstant(day.atTime(time));
    }

    /** The own shifts a request can be about: from 2 days ago (attendance error) to 2 weeks ahead (leave). */
    private String showForm(AuthenticatedUser user, Model model) {
        List<TicketCategory> categories = tickets.listCategoriesForNewTicket(user.getUserId());
        model.addAttribute("categories", categories);
        OffsetDateTime now = OffsetDateTime.now();
        model.addAttribute("shifts", shifts.listOwnInRange(user.getUserId(), now.minus(Duration.ofDays(3)), now.plusDays(15)).stream()
                .filter(shift -> !shift.getStatus().name().equals("CANCELLED")).toList());
        model.addAttribute("incompleteVisits", checkIns.listOwnIncomplete(user.getUserId()));
        model.addAttribute("directions", Direction.values());
        model.addAttribute("leaveTypes", LeaveType.values());
        return "tickets/new";
    }
}
