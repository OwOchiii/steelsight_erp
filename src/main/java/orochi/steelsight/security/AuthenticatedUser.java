package orochi.steelsight.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The signed-in user. Controllers pass getUserId() to services as the acting user. */
@Getter
public class AuthenticatedUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String fullName;
    private final String roleName;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedUser(AppUser user) {
        this.userId = user.getId();
        this.username = user.getEmployeeCode();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.roleName = user.getRole().getRoleName();
        this.enabled = user.isActive();

        List<GrantedAuthority> granted = new ArrayList<>();
        granted.add(new SimpleGrantedAuthority("ROLE_" + roleName));
        for (Permission permission : user.getRole().getPermissions()) {
            granted.add(new SimpleGrantedAuthority(permission.getCode()));
        }
        this.authorities = List.copyOf(granted);
    }

    public boolean can(String permission) {
        return authorities.contains(new SimpleGrantedAuthority(permission));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
