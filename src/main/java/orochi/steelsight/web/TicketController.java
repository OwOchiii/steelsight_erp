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
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.storage.PhotoStorage;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

@Controller
@RequiredArgsConstructor
public class TicketController {

    private final TicketService tickets;
    private final PhotoStorage photos;

    @GetMapping("/tickets")
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("tickets", tickets.listOwn(user.getUserId()));
        return "tickets/list";
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
        String storedPhoto = null;
        try {
            if (binding.hasErrors()) {
                throw new BusinessRuleException("Choose a category and zone from the lists");
            }
            storedPhoto = photos.store(photo);
            Ticket ticket = tickets.create(user.getUserId(), new TicketService.NewTicket(
                    form.getCategoryId(), form.getSubject(), form.getDescription(), form.getZoneId(), storedPhoto));
            redirect.addFlashAttribute("success", "Your ticket was sent.");
            return "redirect:/tickets/" + ticket.getId();
        } catch (BusinessRuleException e) {
            photos.delete(storedPhoto);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", e.getMessage());
            return showForm(user, model);
        } catch (RuntimeException e) {
            photos.delete(storedPhoto);
            throw e;
        }
    }

    @GetMapping("/tickets/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        model.addAttribute("ticket", tickets.get(user.getUserId(), id));
        model.addAttribute("comments", tickets.listComments(user.getUserId(), id));
        return "tickets/detail";
    }

    @PostMapping("/tickets/{id}/comments")
    public String comment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) String body, RedirectAttributes redirect) {
        try {
            tickets.addComment(user.getUserId(), id, body);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tickets/" + id;
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
                .body(photos.load(ticket.getPhotoPath()));
    }

    private String showForm(AuthenticatedUser user, Model model) {
        model.addAttribute("categories", tickets.listCategoriesForNewTicket(user.getUserId()));
        model.addAttribute("zones", tickets.listZonesForNewTicket(user.getUserId()));
        return "tickets/new";
    }
}
