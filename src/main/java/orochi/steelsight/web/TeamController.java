package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.service.TeamService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.security.UserSessions;
import orochi.steelsight.storage.PhotoAlbum;
import orochi.steelsight.storage.PhotoStorage;

import java.time.YearMonth;
import java.util.List;

/**
 * Managers see the people they plan shifts for. Only admins change accounts: create, edit, role,
 * team, active status, password, face photo and delete.
 */
@Controller
@RequiredArgsConstructor
public class TeamController extends PageController {

    private final UserService users;
    private final TeamService teams;
    private final PhotoStorage photos;
    private final SiteTime siteTime;
    private final UserSessions sessions;

    @GetMapping("/team")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<AppUser> members = users.listManaged(user.getUserId(), pageOf(page));
        List<Team> managed = teams.listManaged(user.getUserId());
        model.addAttribute("members", members);
        model.addAttribute("teams", managed);
        if (user.can(Permissions.USER_MANAGE)) {
            model.addAttribute("faceStatuses", users.faceStatuses(user.getUserId(),
                    members.map(AppUser::getId).toList()));
            model.addAttribute("leaderCandidates", teams.listLeaderCandidates(user.getUserId()));
            model.addAttribute("memberCounts", teams.countMembers(user.getUserId()));
        }
        // A leader exports the team they lead; an admin exports everyone.
        boolean canExport = user.can(Permissions.REPORT_EXPORT_ALL)
                || (user.can(Permissions.REPORT_EXPORT_TEAM) && !managed.isEmpty());
        model.addAttribute("canExport", canExport);
        YearMonth thisMonth = YearMonth.from(siteTime.today());
        model.addAttribute("reportMonth", thisMonth.minusMonths(1));
        model.addAttribute("latestMonth", thisMonth);
        return "team/list";
    }

    // --- teams, for admins --------------------------------------------------------------------

    @PostMapping("/teams")
    public String createTeam(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required = false) String name,
                             @RequestParam(required = false) Long managerId, RedirectAttributes redirect) {
        return attempt(redirect, "/team", "The team was created.",
                () -> teams.create(user.getUserId(), name, managerId));
    }

    @PostMapping("/teams/{id}/leader")
    public String changeLeader(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                               @RequestParam(required = false) Long managerId, RedirectAttributes redirect) {
        return attempt(redirect, "/team", "The team leader was changed.",
                () -> teams.changeManager(user.getUserId(), id, managerId));
    }

    @PostMapping("/teams/{id}/delete")
    public String deleteTeam(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             RedirectAttributes redirect) {
        return attempt(redirect, "/team", "The team was deleted.", () -> teams.delete(user.getUserId(), id));
    }

    @PostMapping("/teams/{id}/late-after")
    public String setLateAfter(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                               @RequestParam(required = false) Integer minutes, RedirectAttributes redirect) {
        return attempt(redirect, "/team", "The late check-in rule was saved.",
                () -> teams.setLateAfterMinutes(user.getUserId(), id, minutes));
    }

    // --- new account ----------------------------------------------------------------------------

    @GetMapping("/team/new")
    public String newAccount(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("form", new NewAccountForm());
        return showNewAccount(user, model);
    }

    @PostMapping("/team/new")
    public String create(@AuthenticationPrincipal AuthenticatedUser user,
                         @ModelAttribute("form") NewAccountForm form, BindingResult binding,
                         @RequestParam(required = false) MultipartFile photo,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        requireAdmin(user);
        return submit(binding, model, response, () -> {
            String storedPhoto = photos.store(PhotoAlbum.FACE, photo);
            try {
                AppUser created = users.create(user.getUserId(), new UserService.NewUser(
                        form.getEmployeeCode(), form.getFullName(), form.getEmail(), form.getPhoneNumber(),
                        form.getPassword(), form.getRoleName(), form.getTeamId()), storedPhoto);
                redirect.addFlashAttribute("success", "The account was created.");
                return "redirect:/team/" + created.getId() + "/edit";
            } catch (RuntimeException e) {
                photos.delete(PhotoAlbum.FACE, storedPhoto);
                throw e;
            }
        }, () -> {
            form.setPassword(null);
            return showNewAccount(user, model);
        });
    }

    private String showNewAccount(AuthenticatedUser user, Model model) {
        model.addAttribute("roles", users.listRoles(user.getUserId()));
        model.addAttribute("teams", teams.listManaged(user.getUserId()));
        return "team/new";
    }

    // --- edit an account ------------------------------------------------------------------------

    @GetMapping("/team/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        AppUser member = users.getForEdit(user.getUserId(), id);
        ProfileForm form = new ProfileForm();
        form.setFullName(member.getFullName());
        form.setEmail(member.getEmail());
        form.setPhoneNumber(member.getPhoneNumber());
        form.setVersion(member.getVersion());
        model.addAttribute("form", form);
        return showEdit(user, member, model);
    }

    @PostMapping("/team/{id}")
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @ModelAttribute("form") ProfileForm form, BindingResult binding,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            users.updateProfile(user.getUserId(), id, new UserService.Profile(
                    form.getFullName(), form.getEmail(), form.getPhoneNumber()), form.getVersion());
            redirect.addFlashAttribute("success", "The details were saved.");
            return "redirect:/team/" + id + "/edit";
        }, () -> showEdit(user, users.getForEdit(user.getUserId(), id), model));
    }

    @PostMapping("/team/{id}/role")
    public String changeRole(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             @RequestParam String roleName, RedirectAttributes redirect) {
        return attempt(redirect, editPage(id), "The role was changed.",
                () -> users.changeRole(user.getUserId(), id, roleName));
    }

    @PostMapping("/team/{id}/team")
    public String changeTeam(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             @RequestParam(required = false) Long teamId, RedirectAttributes redirect) {
        return attempt(redirect, editPage(id), "The team was changed.",
                () -> users.assignTeam(user.getUserId(), id, teamId));
    }

    @PostMapping("/team/{id}/deactivate")
    public String deactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             RedirectAttributes redirect) {
        return attempt(redirect, editPage(id), "The account was deactivated.",
                () -> users.setActive(user.getUserId(), id, false));
    }

    @PostMapping("/team/{id}/reactivate")
    public String reactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             RedirectAttributes redirect) {
        return attempt(redirect, editPage(id), "The account was reactivated.",
                () -> users.setActive(user.getUserId(), id, true));
    }

    @PostMapping("/team/{id}/password")
    public String resetPassword(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                @RequestParam(required = false) String newPassword, RedirectAttributes redirect) {
        return attempt(redirect, editPage(id), "The password was reset. They must change it at their next sign-in.",
                () -> {
                    users.resetPassword(user.getUserId(), id, newPassword);
                    sessions.endAll(id);
                });
    }

    @PostMapping("/team/{id}/face")
    public String changeFacePhoto(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @RequestParam(required = false) MultipartFile photo, RedirectAttributes redirect) {
        requireAdmin(user);
        return attempt(redirect, editPage(id), "The face photo was saved and queued for enrolment.", () -> {
            if (photo == null || photo.isEmpty()) {
                throw new BusinessRuleException("Choose a photo to upload");
            }
            String stored = photos.store(PhotoAlbum.FACE, photo);
            try {
                users.changeFacePhoto(user.getUserId(), id, stored);
            } catch (RuntimeException e) {
                photos.delete(PhotoAlbum.FACE, stored);
                throw e;
            }
        });
    }

    @GetMapping("/team/{id}/face")
    public ResponseEntity<Resource> facePhoto(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        String path = users.facePhotoPath(user.getUserId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photos.contentType(path)))
                .cacheControl(CacheControl.noStore())
                .body(photos.load(PhotoAlbum.FACE, path));
    }

    @PostMapping("/team/{id}/delete")
    public String delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @RequestParam(defaultValue = "false") boolean confirm, RedirectAttributes redirect) {
        try {
            if (!confirm) {
                throw new BusinessRuleException("Tick the box to confirm the deletion");
            }
            users.softDelete(user.getUserId(), id);
            redirect.addFlashAttribute("success", "The account was deleted.");
            return "redirect:/team";
        } catch (BusinessRuleException e) {
            // Back to the account, so the admin sees what stopped it.
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:" + editPage(id);
        }
    }

    private String showEdit(AuthenticatedUser user, AppUser member, Model model) {
        model.addAttribute("member", member);
        model.addAttribute("roles", users.listRoles(user.getUserId()));
        model.addAttribute("teams", teams.listManaged(user.getUserId()));
        model.addAttribute("enrolment", users.faceEnrolment(user.getUserId(), member.getId()).orElse(null));
        model.addAttribute("self", member.getId().equals(user.getUserId()));
        return "team/edit";
    }

    private String editPage(Long id) {
        return "/team/" + id + "/edit";
    }

    /** Checked before a photo is stored, so nobody else can put files in storage. The service checks again. */
    private void requireAdmin(AuthenticatedUser user) {
        if (!user.can(Permissions.USER_MANAGE)) {
            throw new AccessDeniedException("Only admins manage accounts");
        }
    }
}
