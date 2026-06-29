package com.erp.iam.web;

import com.erp.iam.api.AuthAuditEvent;
import com.erp.iam.api.Role;
import com.erp.iam.application.JwtService;
import com.erp.iam.application.UserRepository;
import com.erp.iam.domain.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Authentication endpoints for the SPA. {@code POST /login} verifies bcrypt credentials and returns a
 * short-lived access token in the body plus a long-lived refresh token in an httpOnly cookie;
 * {@code POST /refresh} mints a fresh access token from that cookie (silent renewal across page reloads);
 * {@code POST /logout} clears the cookie. {@code GET /me} returns the caller's name and roles (driving the
 * frontend's role-based navigation/guards); it needs only authentication, so an unauthenticated call is 401.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          UserRepository users, ApplicationEventPublisher events) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.users = users;
        this.events = events;
    }

    /** Caller's name and roles (roles without Spring's {@code ROLE_} prefix). */
    public record CurrentUserResponse(String username, List<String> roles) {}

    public record LoginRequest(String username, String password) {}

    /** Access token (Bearer) plus the caller's identity; the refresh token rides in an httpOnly cookie. */
    public record TokenResponse(String accessToken, String username, List<String> roles) {}

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return new CurrentUserResponse(authentication.getName(),
                stripRolePrefix(authentication.getAuthorities()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            events.publishEvent(new AuthAuditEvent(request.username(), false));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        events.publishEvent(new AuthAuditEvent(auth.getName(), true));
        List<String> roles = stripRolePrefix(auth.getAuthorities());
        String access = jwtService.createAccessToken(auth.getName(), roles);
        String refresh = jwtService.createRefreshToken(auth.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtService.buildRefreshCookie(refresh).toString())
                .body(new TokenResponse(access, auth.getName(), roles));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(
            @CookieValue(name = JwtService.REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }
        String username;
        try {
            username = jwtService.parseRefreshSubject(refreshToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        Optional<User> user = users.findByUsername(username);
        if (user.isEmpty() || !user.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer valid");
        }
        // Re-derive roles from the store so a refresh reflects the user's current roles.
        List<String> roles = user.get().roleSet().stream().map(Role::name).sorted().toList();
        return new TokenResponse(jwtService.createAccessToken(username, roles), username, roles);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, jwtService.clearRefreshCookie().toString())
                .build();
    }

    private static List<String> stripRolePrefix(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .sorted()
                .toList();
    }
}
