package com.erp.iam.application;

import com.erp.iam.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads persisted {@link User}s for the login flow's {@code DaoAuthenticationProvider} (bcrypt password
 * check). Roles map to {@code ROLE_*} authorities; the guest user resolves to an empty authority set
 * (authenticated, read-only). Bearer-token requests do not hit this — they are validated by the JWT decoder.
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public DbUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
        List<GrantedAuthority> authorities = user.roleSet().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
