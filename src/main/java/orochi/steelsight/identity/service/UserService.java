package orochi.steelsight.identity.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import orochi.steelsight.attendance.model.EmbeddingStatus;
import orochi.steelsight.attendance.model.FaceEmbedding;
import orochi.steelsight.attendance.repository.FaceEmbeddingRepository;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Role;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.storage.PhotoAlbum;
import orochi.steelsight.storage.PhotoStorage;
import orochi.steelsight.validation.Text;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String MANAGER_ROLE = "MANAGER";
    private static final String WORKER_ROLE = "WORKER";
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+");
    private static final int PHONE_LENGTH = 30;
    private static final Pattern EMPLOYEE_CODE = Pattern.compile("[A-Za-z0-9._-]{1,50}");
    private static final int SHORTEST_PASSWORD = 8;
    private static final int LONGEST_PASSWORD_BYTES = 72;   // BCrypt can't use any more than this

    private final AccessPolicy access;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final TeamRepository teams;
    private final FaceEmbeddingRepository embeddings;
    private final PasswordEncoder passwordEncoder;
    private final PhotoStorage photos;

    public record NewUser(String employeeCode, String fullName, String email, String phoneNumber,
                          String rawPassword, String roleName, Long teamId) {
    }

    public record Profile(String fullName, String email, String phoneNumber) {
    }

    public AppUser create(Long actorId, NewUser details) {
        return create(actorId, details, null);
    }

    /**
     * The admin chose the password, so the person must pick their own at their first sign-in. A face
     * photo, when there is one, is queued for enrolment in the same transaction.
     */
    public AppUser create(Long actorId, NewUser details, String facePhotoPath) {
        access.require(actorId, Permissions.USER_MANAGE);
        String code = details.employeeCode() == null ? "" : details.employeeCode().strip();
        if (!EMPLOYEE_CODE.matcher(code).matches()) {
            throw new BusinessRuleException("An employee code can use letters, numbers, dots, dashes and underscores, up to 50 characters");
        }
        if (details.roleName() == null || details.roleName().isBlank()) {
            throw new BusinessRuleException("Choose a role");
        }
        AppUser user = new AppUser();
        user.setEmployeeCode(code);
        user.setFullName(Text.required("A name", details.fullName(), Text.SHORT));
        user.setEmail(email(details.email()));
        user.setPhoneNumber(Text.optional("A phone number", details.phoneNumber(), PHONE_LENGTH));
        user.setPasswordHash(passwordEncoder.encode(checkedPassword(details.rawPassword())));
        user.setMustChangePassword(true);
        user.setRole(role(details.roleName()));
        user.setTeam(details.teamId() == null ? null : team(details.teamId()));
        AppUser saved = saveUnique(user);
        if (facePhotoPath == null) {
            return saved;
        }
        saved.setFaceFilePath(facePhotoPath);
        users.saveAndFlush(saved);
        embeddings.queueForEnrolment(saved.getId(), facePhotoPath);
        return users.require(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<Role> listRoles(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return roles.findAll(Sort.by("id"));
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

    /** The people this user plans shifts for: their teams, or everyone with USER_MANAGE. */
    @Transactional(readOnly = true)
    public Page<AppUser> listManaged(Long actorId, Pageable page) {
        if (access.has(actorId, Permissions.USER_MANAGE)) {
            return users.findAllLiveByName(page);
        }
        access.require(actorId, Permissions.SHIFT_UPDATE_TEAM);
        return users.findTeamMembers(actorId, page);
    }

    @Transactional(readOnly = true)
    public AppUser getForEdit(Long actorId, Long userId) {
        access.requireCanUpdateUser(actorId, userId);
        return users.require(userId);
    }

    /** Anyone with USER_READ_ALL can look at any worker, whichever team they are on. */
    @Transactional(readOnly = true)
    public AppUser getWorker(Long actorId, Long workerId) {
        access.require(actorId, Permissions.USER_READ_ALL);
        AppUser worker = users.require(workerId);
        if (worker.getDeletedAt() != null || !WORKER_ROLE.equals(worker.getRole().getRoleName())) {
            throw new NotFoundException("Worker", workerId);
        }
        return worker;
    }

    @Transactional(readOnly = true)
    public boolean canEdit(Long actorId, Long userId) {
        return access.canUpdateUser(actorId, userId);
    }

    @Transactional(readOnly = true)
    public boolean canPlanShiftFor(Long actorId, Long workerId) {
        return access.canAssignShift(actorId, workerId);
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

    /** Admins only; the version comes from the form the admin loaded. */
    public AppUser updateProfile(Long actorId, Long userId, Profile profile, long expectedVersion) {
        access.requireCanUpdateUser(actorId, userId);
        AppUser user = users.require(userId);
        user.requireVersion(expectedVersion);
        user.setFullName(Text.required("A name", profile.fullName(), Text.SHORT));
        user.setEmail(email(profile.email()));
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

    /**
     * Admins only, because face photos are biometric data. Saves the new path and queues it for
     * enrolment in the same transaction. The old photo is removed only once that has committed.
     */
    public AppUser changeFacePhoto(Long actorId, Long userId, String photoPath) {
        access.require(actorId, Permissions.USER_MANAGE);
        AppUser user = users.require(userId);
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("User", userId);
        }
        String old = user.getFaceFilePath();
        user.setFaceFilePath(photoPath);
        users.saveAndFlush(user);
        embeddings.queueForEnrolment(userId, photoPath);
        if (old != null && !old.equals(photoPath)) {
            removeFacePhotoAfterCommit(old);
        }
        return users.require(userId);
    }

    /** The detection service's progress with this person's face photo, if they have one. */
    @Transactional(readOnly = true)
    public Optional<FaceEmbedding> faceEnrolment(Long actorId, Long userId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return embeddings.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Map<Long, EmbeddingStatus> faceStatuses(Long actorId, Collection<Long> userIds) {
        access.require(actorId, Permissions.USER_MANAGE);
        return embeddings.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), FaceEmbedding::getStatus));
    }

    @Transactional(readOnly = true)
    public String facePhotoPath(Long actorId, Long userId) {
        access.require(actorId, Permissions.USER_MANAGE);
        String path = users.require(userId).getFaceFilePath();
        if (path == null) {
            throw new NotFoundException("Face photo of user", userId);
        }
        return path;
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
        String photo = user.getFaceFilePath();
        user.setDeletedAt(OffsetDateTime.now());
        user.setActive(false);
        user.setFaceFilePath(null);
        if (photo != null) {
            removeFacePhotoAfterCommit(photo);
        }
    }

    public void changeOwnPassword(Long actorId, String currentPassword, String newPassword) {
        AppUser user = users.require(actorId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("The current password is wrong");
        }
        String next = checkedPassword(newPassword);
        if (passwordEncoder.matches(next, user.getPasswordHash())) {
            throw new BusinessRuleException("Choose a password that's different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        user.setMustChangePassword(false);
    }

    /** The admin then knows the password, so the person must change it at their next sign-in. */
    public void resetPassword(Long actorId, Long userId, String newPassword) {
        access.require(actorId, Permissions.USER_MANAGE);
        AppUser user = users.require(userId);
        user.setPasswordHash(passwordEncoder.encode(checkedPassword(newPassword)));
        user.setMustChangePassword(true);
    }

    private String checkedPassword(String raw) {
        if (raw == null || raw.length() < SHORTEST_PASSWORD) {
            throw new BusinessRuleException("A password needs at least 8 characters");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > LONGEST_PASSWORD_BYTES) {
            throw new BusinessRuleException("That password is too long");
        }
        return raw;
    }

    private String email(String typed) {
        String email = Text.optional("An email", typed, Text.SHORT);
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new BusinessRuleException("That email address doesn't look right");
        }
        return email;
    }

    /** Only after commit, so a rolled-back change never loses the photo that is still in use. */
    private void removeFacePhotoAfterCommit(String path) {
        Runnable remove = () -> {
            try {
                photos.delete(PhotoAlbum.FACE, path);
            } catch (RuntimeException e) {
                log.warn("Could not remove old face photo {}: {}", path, e.getClass().getSimpleName());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            remove.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                remove.run();
            }
        });
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
