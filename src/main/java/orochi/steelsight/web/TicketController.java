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
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.storage.PhotoAlbum;
import orochi.steelsight.storage.PhotoStorage;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

@Controller
@RequiredArgsConstructor
public class TicketController extends PageController {

    private final TicketService tickets;
    private final PhotoStorage photos;

    @GetMapping("/tickets")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("tickets", tickets.listOwn(user.getUserId(), pageOf(page)));
        return "tickets/list";
    }

    /** The tickets the user works on, as opposed to the ones they opened. */
    @GetMapping("/tickets/queue")
    public String queue(@AuthenticationPrincipal AuthenticatedUser user,
                        @RequestParam(defaultValue = "false") boolean resolved,
                        @RequestParam(defaultValue = "false") boolean all,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        boolean everyCategory = all && user.can(Permissions.TICKET_READ_ALL);
        model.addAttribute("tickets", tickets.listQueue(user.getUserId(), resolved, everyCategory, pageOf(page)));
        model.addAttribute("resolved", resolved);
        model.addAttribute("all", everyCategory);
        return "tickets/queue";
    }

    @GetMapping("/tickets/new")
    public String newTicket(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("form", new TicketForm());
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
                Ticket ticket = tickets.create(user.getUserId(), new TicketService.NewTicket(
                        form.getCategoryId(), form.getSubject(), form.getDescription(), form.getZoneId(), storedPhoto));
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
        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", tickets.listComments(user.getUserId(), id));
        model.addAttribute("canHandle", canHandle);
        model.addAttribute("ownTicket", ticket.getCreatedBy().getId().equals(user.getUserId()));
        if (canHandle && !ticket.isResolved() && !ticket.isDispute()) {
            model.addAttribute("assignees", tickets.listPossibleAssignees(user.getUserId(), id));
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

    @PostMapping("/tickets/{id}/resolve")
    public String resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return attempt(redirect, "/tickets/" + id, "The ticket was resolved.",
                () -> tickets.resolve(user.getUserId(), id, note));
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

    private String showForm(AuthenticatedUser user, Model model) {
        model.addAttribute("categories", tickets.listCategoriesForNewTicket(user.getUserId()));
        model.addAttribute("zones", tickets.listZonesForNewTicket(user.getUserId()));
        return "tickets/new";
    }
}
