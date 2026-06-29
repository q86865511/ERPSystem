package com.erp.iam.domain;

import com.erp.iam.api.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PROTECTED;

/**
 * A persisted application user. Replaces the in-memory {@code {noop}} store. Roles are kept as a CSV of
 * {@link Role} codes (a closed set, never queried by role); the guest account has an empty roles string —
 * authenticated and read-only, holding no write role. Passwords are stored as bcrypt hashes.
 *
 * <p>Note: distinct from Spring's {@code org.springframework.security.core.userdetails.User} — the
 * {@code DbUserDetailsService} adapts this entity into that.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** CSV of role codes, e.g. {@code "ADMIN,ACCOUNTANT,WAREHOUSE,SALES"}; empty for guest. */
    @Column(nullable = false)
    private String roles;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public User(String username, String passwordHash, Set<Role> roles) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = (roles == null ? Set.<Role>of() : roles).stream()
                .map(Role::name).sorted().collect(Collectors.joining(","));
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    /** Parses the CSV roles column into a {@link Role} set ({@code empty} for guest). */
    public Set<Role> roleSet() {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        Set<Role> out = EnumSet.noneOf(Role.class);
        for (String code : roles.split(",")) {
            out.add(Role.valueOf(code.trim()));
        }
        return out;
    }
}
