package com.erp.iam.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity endpoint for the SPA. {@code GET /api/auth/me} doubles as the login probe (a 200 means the
 * supplied HTTP Basic credentials are valid) and the source of the caller's roles, which drive the
 * frontend's role-based navigation and route guards. It needs only authentication (no specific role),
 * so it falls under the {@code anyRequest().authenticated()} rule — an unauthenticated call returns 401.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";

    /** The authenticated user's name and roles (roles without Spring's {@code ROLE_} prefix). */
    public record CurrentUserResponse(String username, List<String> roles) {}

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .sorted()
                .toList();
        return new CurrentUserResponse(authentication.getName(), roles);
    }
}
