package orochi.steelsight.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.identity.repository.AppUserRepository;

/** Signs users in by employee code. Soft-deleted users cannot be found; deactivated ones are refused. */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String employeeCode) {
        return users.findByEmployeeCodeAndDeletedAtIsNull(employeeCode)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown employee code"));
    }
}
