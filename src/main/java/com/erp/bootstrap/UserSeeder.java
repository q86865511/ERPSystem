package com.erp.bootstrap;

import com.erp.iam.api.Role;
import com.erp.iam.application.UserRepository;
import com.erp.iam.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Seeds the five fixed application users with bcrypt-hashed passwords. Unlike {@link DataSeeder} this is
 * <em>not</em> profile-scoped: users must exist in every profile (local run, tests, demo) so login works
 * everywhere. Idempotent — skips any user that already exists, so it is safe across restarts and after a
 * demo {@code down -v} reset. Demo-grade: each password equals its username (guest/guest); guest holds no
 * role, so it can read but every write returns 403.
 *
 * <p>Lives in the composition root (not a business module), wiring iam persistence + the password encoder.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        create("admin", EnumSet.allOf(Role.class));
        create("accountant", Set.of(Role.ACCOUNTANT));
        create("warehouse", Set.of(Role.WAREHOUSE));
        create("sales", Set.of(Role.SALES));
        create("hr", Set.of(Role.HR));
        create("guest", Set.of()); // read-only: no role
    }

    private void create(String username, Set<Role> roles) {
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new User(username, passwordEncoder.encode(username), roles));
        log.info("Seeded user '{}' with roles {}", username, roles);
    }
}
