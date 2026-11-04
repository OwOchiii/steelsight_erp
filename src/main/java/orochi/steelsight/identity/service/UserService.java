package orochi.steelsight.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.repository.FaceEmbeddingRepository;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.exception.StaleEditException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Role;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.repository.TeamRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private static final String MANAGER_ROLE = "MANAGER";

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
        return user(userId);
    }

    @Transactional(readOnly = true)
    public List<AppUser> listAll(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return users.findAllByDeletedAtIsNull();
    }

    /** Managers can edit workers on their own team; the version comes from the form the user loaded. */
    public AppUser updateProfile(Long actorId, Long userId, Profile profile, long expectedVersion) {
        access.requireCanUpdateUser(actorId, userId);
        AppUser user = user(userId);
        StaleEditException.check("User", userId, user.getVersion(), expectedVersion);
        user.setFullName(profile.fullName());
        user.setEmail(profile.email());
        user.setPhoneNumber(profile.phoneNumber());
        return saveUnique(user);
    }

    public AppUser changeRole(Long actorId, Long userId, String roleName) {
        access.require(actorId, Permissions.USER_MANAGE);
        if (actorId.equals(userId)) {
            throw new BusinessRuleException("You cannot change your own role");
        }
        AppUser user = user(userId);
        if (!MANAGER_ROLE.equals(roleName) && !teams.findAllByManagerId(userId).isEmpty()) {
            throw new BusinessRuleException("Give this user's teams a new manager first");
        }
        user.setRole(role(roleName));
        return user;
    }

    public AppUser assignTeam(Long actorId, Long userId, Long teamId) {
        access.require(actorId, Permissions.USER_MANAGE);
        AppUser user = user(userId);
        user.setTeam(teamId == null ? null : team(teamId));
        return user;
    }

    public AppUser setActive(Long actorId, Long userId, boolean active) {
        access.require(actorId, Permissions.USER_MANAGE);
        if (actorId.equals(userId)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        AppUser user = user(userId);
        user.setActive(active);
        return user;
    }

    /** Saves the new photo path and queues it for enrolment in the same transaction. */
    public AppUser changeFacePhoto(Long actorId, Long userId, String photoPath) {
        access.requireCanUpdateUser(actorId, userId);
        AppUser user = user(userId);
        user.setFaceFilePath(photoPath);
        users.saveAndFlush(user);
        embeddings.queueForEnrolment(userId, photoPath);
        return user(userId);
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
        AppUser user = user(userId);
        user.setDeletedAt(OffsetDateTime.now());
        user.setActive(false);
        user.setFaceFilePath(null);
    }

    public void changeOwnPassword(Long actorId, String currentPassword, String newPassword) {
        AppUser user = user(actorId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("The current password is wrong");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    public void resetPassword(Long actorId, Long userId, String newPassword) {
        access.require(actorId, Permissions.USER_MANAGE);
        user(userId).setPasswordHash(passwordEncoder.encode(newPassword));
    }

    private AppUser saveUnique(AppUser user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("That employee code or email is already in use");
        }
    }

    private AppUser user(Long id) {
        return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private Role role(String name) {
        return roles.findByRoleName(name).orElseThrow(() -> new NotFoundException("Role", name));
    }

    private Team team(Long id) {
        return teams.findById(id).orElseThrow(() -> new NotFoundException("Team", id));
    }
}
