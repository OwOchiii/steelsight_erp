package orochi.steelsight.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Role;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;

/** A new database has no users, so nobody could sign in to create the first one. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String employeeCode;
    private final String password;

    public AdminBootstrap(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          @Value("${steelsight.bootstrap.admin-employee-code}") String employeeCode,
                          @Value("${steelsight.bootstrap.admin-password}") String password) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.employeeCode = employeeCode;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            return;
        }
        Role adminRole = roles.findByRoleName("ADMIN").orElseThrow();
        if (!users.findAllByRoleIdAndDeletedAtIsNull(adminRole.getId()).isEmpty()) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setRole(adminRole);
        admin.setEmployeeCode(employeeCode);
        admin.setFullName("Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        users.save(admin);
        log.info("Created the first admin with employee code {}", employeeCode);
    }
}
