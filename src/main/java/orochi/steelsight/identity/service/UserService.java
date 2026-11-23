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
import orochi.steelsight.attendance.model.FacePose;
import orochi.steelsight.attendance.repository.FaceEmbeddingRepository;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Role;
import orochi.steelsight.identity.model.StaffGroup;
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
import java.util.EnumMap;
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
    private static final Pattern EMPLOYEE_CODE = Pattern.compile("[A-Z0-9-]{3,20}");   // SRS BR-06, MSG12
    private static final int SHORTEST_PASSWORD = 8;
    private static final int LONGEST_PASSWORD_BYTES = 72;   // BCrypt can't use any more than this

    private final AccessPolicy access;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final TeamRepository teams;
    private final FaceEmbeddingRepository embeddings;
    private final PasswordEncoder passwordEncoder;
    private final PhotoStorage photos;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** consent: the Admin ticked that the employee signed the consent form for face data (BR-10, BR-11). */
    public record NewUser(String employeeCode, String fullName, String email, String phoneNumber,
                          String rawPassword, String roleName, Long teamId, StaffGroup staffGroup, boolean consent) {

        public NewUser(String employeeCode, String fullName, String email, String phoneNumber,
                       String rawPassword, String roleName, Long teamId) {
            this(employeeCode, fullName, email, phoneNumber, rawPassword, roleName, teamId, null, true);
        }
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
        String code = details.employeeCode() == null ? "" : details.employeeCode().strip().toUpperCase(java.util.Locale.ROOT);
        if (!EMPLOYEE_CODE.matcher(code).matches()) {
            throw new BusinessRuleException("Employee code: 3 to 20 characters, letters A-Z, digits and dashes.");
        }
        if (users.existsByEmployeeCodeIgnoreCase(code)) {
            throw new BusinessRuleException("This employee code already exists.");
        }
        if (details.roleName() == null || details.roleName().isBlank()) {
            throw new BusinessRuleException("Choose a role");
        }
        if (!details.consent()) {
            throw new BusinessRuleException("Confirm that the employee has signed the consent form for face data.");
        }
        AppUser user = new AppUser();
        user.setEmployeeCode(code);
        user.setFullName(Text.sized("A name", details.fullName(), 2, 100));
        user.setEmail(email(details.email()));
        user.setPhoneNumber(phone(details.phoneNumber(), null));
        user.setPasswordHash(passwordEncoder.encode(checkedPassword(details.rawPassword())));
        user.setMustChangePassword(true);
        user.setRole(role(details.roleName()));
        user.setFaceConsentAt(OffsetDateTime.now());
        placeInGroup(user, details.staffGroup(), details.teamId());
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

    /** The members of the team this user leads, for Team Attendance (SRS 3.2.13). */
    @Transactional(readOnly = true)
    public List<AppUser> listTeamMembers(Long actorId) {
        access.require(actorId, Permissions.CHECKIN_READ_TEAM);
        return users.findTeamMembers(actorId);
    }

    /** For the Admin's Home: face photos the detection app could not use (SRS 3.2.3). */
    @Transactional(readOnly = true)
    public long countFailedFacePhotos(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return embeddings.countFailed();
    }

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
        user.setFullName(Text.sized("A name", profile.fullName(), 2, 100));
        user.setEmail(email(profile.email()));
        user.setPhoneNumber(phone(profile.phoneNumber(), userId));
        return saveUnique(user);
    }

    /** A WORKER is Site or Office; a MANAGER is always Site; HSE and the Admin are always Office (SRS 3.2.6 #4). */
    public AppUser changeGroup(Long actorId, Long userId, StaffGroup group) {
        access.require(actorId, Permissions.USER_MANAGE);
        AppUser user = users.require(userId);
        placeInGroup(user, group, user.getTeam() == null ? null : user.getTeam().getId());
        return user;
    }

    /**
     * BR-06, BR-20: the role decides the group where it can; a site WORKER needs a team (MSG16); office
     * staff and HSE Officers belong to no team. A MANAGER gets his team on the Teams page.
     */
    private void placeInGroup(AppUser user, StaffGroup chosen, Long teamId) {
        String role = user.getRole().getRoleName();
        StaffGroup group = switch (role) {
            case MANAGER_ROLE -> StaffGroup.SITE;
            case "HSE_OFFICER", "ADMIN" -> StaffGroup.OFFICE;
            default -> chosen == null ? StaffGroup.SITE : chosen;
        };
        user.setStaffGroup(group);
        if (group == StaffGroup.OFFICE) {
            user.setTeam(null);
        } else if ("WORKER".equals(role)) {
            if (teamId == null) {
                throw new BusinessRuleException("Choose the team of this site worker.");
            }
            user.setTeam(team(teamId));
        }
    }

    /** BR-06, MSG14, MSG15: 10 digits starting with 0, unique among active accounts. */
    private String phone(String value, Long exceptUserId) {
        String digits = value == null ? "" : value.strip().replace(" ", "");
        if (!digits.matches("0\\d{9}")) {
            throw new BusinessRuleException("Phone number: 10 digits starting with 0.");
        }
        boolean used = users.findAllByDeletedAtIsNull().stream()
                .anyMatch(other -> digits.equals(other.getPhoneNumber()) && !other.getId().equals(exceptUserId));
        if (used) {
            throw new BusinessRuleException("This phone number is used by another employee.");
        }
        return digits;
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
        if (!"WORKER".equals(roleName)) {
            placeInGroup(user, null, null);
        } else if (user.getStaffGroup() == StaffGroup.SITE && user.getTeam() == null) {
            user.setStaffGroup(StaffGroup.OFFICE);   // a worker without a team works in the office until one is chosen
        }
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

    /**
     * Admins only. Adds or replaces a photo of the worker turned LEFT or RIGHT, so the gate camera
     * still knows them when they don't look straight at it. Queued for enrolment in the same
     * transaction; a replaced photo is removed only once that has committed.
     */
    public FaceEmbedding changeFaceAngle(Long actorId, Long userId, FacePose pose, String photoPath) {
        access.require(actorId, Permissions.USER_MANAGE);
        requireExtraAngle(pose);
        // Locked, so this runs wholly before or after a soft delete or another change to this person's photos.
        AppUser user = users.requireForUpdate(userId);
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("User", userId);
        }
        if (user.getFaceFilePath() == null) {
            throw new BusinessRuleException("Add the photo looking at the camera first");
        }
        String old = embeddings.findByUserIdAndPose(userId, pose).map(FaceEmbedding::getSourceFilePath).orElse(null);
        embeddings.queueForEnrolment(userId, pose.name(), photoPath);
        if (old != null && !old.equals(photoPath)) {
            removeFacePhotoAfterCommit(old);
        }
        return embeddings.findByUserIdAndPose(userId, pose).orElseThrow();
    }

    /** Admins only. Removes one extra angle and its face data; the photo goes after commit. */
    public void removeFaceAngle(Long actorId, Long userId, FacePose pose) {
        access.require(actorId, Permissions.USER_MANAGE);
        requireExtraAngle(pose);
        users.requireForUpdate(userId);
        FaceEmbedding row = embeddings.findByUserIdAndPose(userId, pose)
                .orElseThrow(() -> new NotFoundException("Face photo of user", userId));
        String path = row.getSourceFilePath();
        embeddings.deleteAngle(userId, pose.name());
        removeFacePhotoAfterCommit(path);
    }

    /** The detection service's progress with this person's FRONT face photo, if they have one. */
    @Transactional(readOnly = true)
    public Optional<FaceEmbedding> faceEnrolment(Long actorId, Long userId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return embeddings.findFrontByUserId(userId);
    }

    /** The extra angles (LEFT, RIGHT) this person has, by pose. */
    @Transactional(readOnly = true)
    public Map<FacePose, FaceEmbedding> faceAngles(Long actorId, Long userId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Map<FacePose, FaceEmbedding> angles = new EnumMap<>(FacePose.class);
        for (FaceEmbedding row : embeddings.findAllByUserIdOrderByPose(userId)) {
            if (row.getPose() != FacePose.FRONT) {
                angles.put(row.getPose(), row);
            }
        }
        return angles;
    }

    /** The status of each person's FRONT photo, the one that decides whether they are enrolled. */
    @Transactional(readOnly = true)
    public Map<Long, EmbeddingStatus> faceStatuses(Long actorId, Collection<Long> userIds) {
        access.require(actorId, Permissions.USER_MANAGE);
        return embeddings.findAllByUserIdInAndPose(userIds, FacePose.FRONT).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), FaceEmbedding::getStatus));
    }

    @Transactional(readOnly = true)
    public String faceAnglePath(Long actorId, Long userId, FacePose pose) {
        access.require(actorId, Permissions.USER_MANAGE);
        requireExtraAngle(pose);
        return embeddings.findByUserIdAndPose(userId, pose)
                .map(FaceEmbedding::getSourceFilePath)
                .orElseThrow(() -> new NotFoundException("Face photo of user", userId));
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
        // Locked, so a face-angle upload can't slip in between reading the photos and deleting.
        AppUser user = users.requireForUpdate(userId);
        String photo = user.getFaceFilePath();
        // The trigger deletes the extra-angle rows too, so note their photos first.
        List<String> anglePhotos = embeddings.findAllByUserIdOrderByPose(userId).stream()
                .filter(row -> row.getPose() != FacePose.FRONT)
                .map(FaceEmbedding::getSourceFilePath)
                .toList();
        user.setDeletedAt(OffsetDateTime.now());
        user.setActive(false);
        user.setFaceFilePath(null);
        // BR-07: the Zalo link goes and future shifts are cancelled; history stays.
        jdbc.update("UPDATE app_user SET zalo_chat_id = NULL WHERE id = ?", userId);
        jdbc.update("DELETE FROM zalo_link_code WHERE user_id = ?", userId);
        jdbc.update("UPDATE shift SET status = 'CANCELLED' WHERE user_id = ? AND status = 'SCHEDULED' AND start_at > now()", userId);
        if (photo != null) {
            removeFacePhotoAfterCommit(photo);
        }
        anglePhotos.forEach(this::removeFacePhotoAfterCommit);
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

    private static void requireExtraAngle(FacePose pose) {
        if (pose == null || pose == FacePose.FRONT) {
            throw new BusinessRuleException("Choose the left or the right angle");
        }
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
