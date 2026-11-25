package orochi.steelsight.identity.service;

import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Suggests a free employee code for the New Account form: a prefix for the role and five random digits,
 * such as WRK-48213 (SRS BR-06: 3 to 20 characters, A-Z, 0-9 and dashes). A code any account already
 * holds, including a deleted one, is drawn again, because the unique key covers every row and a code is
 * never given twice. The Admin can still type their own; {@link UserService#create} checks it again.
 */
@Service
public class EmployeeCodes {

    static final int DIGITS = 5;
    static final int MAX_TRIES = 50;
    private static final int BOUND = 100_000;   // 10^DIGITS
    private static final Map<String, String> PREFIX = Map.of(
            "WORKER", "WRK", "MANAGER", "MGR", "HSE_OFFICER", "HSE", "ADMIN", "ADM");

    private final AppUserRepository users;
    private final AccessPolicy access;
    private final IntUnaryOperator random;   // bound -> a number in [0, bound)

    @Autowired
    public EmployeeCodes(AppUserRepository users, AccessPolicy access) {
        this(users, access, new SecureRandom()::nextInt);
    }

    EmployeeCodes(AppUserRepository users, AccessPolicy access, IntUnaryOperator random) {
        this.users = users;
        this.access = access;
        this.random = random;
    }

    /** A code no account has; EMP- when no role is chosen yet. */
    @Transactional(readOnly = true)
    public String suggest(Long actorId, String roleName) {
        access.require(actorId, Permissions.USER_MANAGE);
        String prefix = PREFIX.getOrDefault(roleName == null ? "" : roleName.strip(), "EMP");
        for (int i = 0; i < MAX_TRIES; i++) {
            String code = prefix + "-" + String.format("%0" + DIGITS + "d", random.applyAsInt(BOUND));
            if (!users.existsByEmployeeCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new BusinessRuleException("No free employee code was found. Type one yourself.");
    }
}
