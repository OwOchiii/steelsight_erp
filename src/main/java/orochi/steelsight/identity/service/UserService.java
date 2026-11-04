package orochi.steelsight.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.repository.FaceEmbeddingRepository;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Role;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.validation.Text;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private static final String MANAGER_ROLE = "MANAGER";
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+");
    private static final int PHONE_LENGTH = 30;

    private final AccessPolicy access;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final TeamRepository teams;
    private final FaceEmbeddingRepository embeddings;
    private final PasswordEncoder passwordEncoder;

    public record NewUser(String employeeCode, String fullName, String email, String phoneNumber,
                          String rawPassword, String roleName, Long teamId) {
    }

    public record Profile(String fullName, String email, String phoneNumber) {
    }

    public AppUser create(Long actorId, NewUser details) {
        access.require(actorId, Permissions.USER_MANAGE);
        AppUser user = new AppUser();
        user.setEmployeeCode(details.employeeCode());
        user.setFullName(details.fullName());
        user.setEmail(details.email());
        user.setPhoneNumber(details.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(details.rawPassword()));
        user.setRole(role(details.roleName()));
        user.setTeam(details.teamId() == null ? null : team(details.teamId()));
        return saveUnique(user);
    }

    @Transactional(readOnly = true)
    public AppUser get(Long actorId, Long userId) {
        if (!actorId.equals(userId) && !access.canUpdateUser(actorId, userId)) {
            access.require(actorId, Permissions.USER_MANAGE);
        }
        return users.require(userId);
    }

    @Transactional(readOnly = true)
    public List<AppUser> listAll(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return users.findAllByDeletedAtIsNull();
    }

    /** The people this user manages: their teams, or everyone with USER_MANAGE. */
    @Transactional(readOnly = true)
    public Page<AppUser> listManaged(Long actorId, Pageable page) {
        if (access.has(actorId, Permissions.USER_MANAGE)) {
            return users.findAllLiveByName(page);
        }
        access.require(actorId, Permissions.USER_UPDATE_TEAM);
        return users.findTeamMembers(actorId, page);
    }

    @Transactional(readOnly = true)
    public AppUser getForEdit(Long actorId, Long userId) {
        access.requireCanUpdateUser(actorId, userId);
        return users.require(userId);
    }

    /** Who this user can give a shift to. */
    @Transactional(readOnly = true)
    public List<AppUser> listShiftWorkers(Long actorId) {
        if (access.has(actorId, Permissions.SHIFT_UPDATE_ALL)) {
            return users.findAllActiveByName();
        }
        access.require(actorId, Permissions.SHIFT_UPDATE_TEAM);
        return users.findTeamMembers(actorId).stream().filter(AppUser::isActive).toList();
    }

    /** Who HSE can name on an unidentified violation. Cameras catch anyone, not only the WORKER role. */
    @Transactional(readOnly = true)
    public List<AppUser> listForViolationReview(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_REVIEW);
        return users.findAllActiveByName();
    }

    @Transactional(readOnly = true)
    public List<AppUser> listForManualCheckIn(Long actorId) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        return users.findAllActiveByName();
    }

    /** Managers can edit workers on their own team; the version comes from the form the user loaded. */
    public AppUser updateProfile(Long actorId, Long userId, Profile profile, long expectedVersion) {
        access.requireCanUpdateUser(actorId, userId);
        AppUser user = users.require(userId);
        user.requireVersion(expectedVersion);
        String email = Text.optional("An email", profile.email(), Text.SHORT);
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new BusinessRuleException("That email address doesn't look right");
        }
        user.setFullName(Text.required("A name", profile.fullName(), Text.SHORT));
        user.setEmail(email);
        user.setPhoneNumber(Text.optional("A phone number", profile.phoneNumber(), PHONE_LENGTH));
        return saveUnique(user);
    }

    public AppUser changeRole(Long actorId, Long userId, String roleName) {
        access.require(actorId, Permissions.USER_MANAGE);
        if (actorId.equals(userId)) {
            throw new BusinessRuleException("You cannot change your own role");
        }
        AppUser user = users.require(userId);
        if (!MANAGER_ROLE.equals(roleName) && !teams.findAllByManagerId(userId).isEmpty()) {
            throw new BusinessRuleException("Give this user's teams a new manager first");
        }
        user.setRole(role(roleName));
        return user;
    }

    public AppUser assignTeam(Long actorId, Long userId, Long teamId) {
        access.require(actorId, Permissions.USER_MANAGE);
        AppUser user = users.require(userId);
        user.setTeam(teamId == null ? null : team(teamId));
        return user;
    }

    public AppUser setActive(Long actorId, Long userId, boolean active) {
        access.require(actorId, Permissions.USER_MANAGE);
        if (actorId.equals(userId)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        AppUser user = users.require(userId);
        user.setActive(active);
        return user;
    }

    /** Saves the new photo path and queues it for enrolment in the same transaction. */
    public AppUser changeFacePhoto(Long actorId, Long userId, String photoPath) {
        access.requireCanUpdateUser(actorId, userId);
        AppUser user = users.require(userId);
        user.setFaceFilePath(photoPath);
        users.saveAndFlush(user);
        embeddings.queueForEnrolment(userId, photoPath);
        return users.require(userId);
    }

    /** A database trigger erases the face embedding in this same transaction. */
    public void softDelete(Long actorId, Long userId) {
        access.require(actorId, Permissions.USER_MANAGE);
        if (actorId.equals(userId)) {
            throw new BusinessRuleException("You cannot delete your own account");
        }
        if (!teams.findAllByManagerId(userId).isEmpty()) {
            throw new BusinessRuleException("Give this user's teams a new manager first");
        }
        AppUser user = users.require(userId);
        user.setDeletedAt(OffsetDateTime.now());
        user.setActive(false);
        user.setFaceFilePath(null);
    }

    public void changeOwnPassword(Long actorId, String currentPassword, String newPassword) {
        AppUser user = users.require(actorId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("The current password is wrong");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    public void resetPassword(Long actorId, Long userId, String newPassword) {
        access.require(actorId, Permissions.USER_MANAGE);
        users.require(userId).setPasswordHash(passwordEncoder.encode(newPassword));
    }

    private AppUser saveUnique(AppUser user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("That employee code or email is already in use");
        }
    }


    private Role role(String name) {
        return roles.findByRoleName(name).orElseThrow(() -> new NotFoundException("Role", name));
    }

    private Team team(Long id) {
        return teams.require(id);
    }
}
