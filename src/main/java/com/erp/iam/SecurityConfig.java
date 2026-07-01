package com.erp.iam;

import com.erp.iam.application.DbUserDetailsService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Authentication and role-based access control. Stateless JWT bearer auth over a stateless API. Four
 * roles map to the people who run each part of the business — ACCOUNTANT (financial postings), WAREHOUSE
 * (physical movements), SALES (sales orders), ADMIN (superuser, master data) — enforced as request
 * authorization at the single REST entry point. Read endpoints (GETs, reports) need only authentication;
 * the read-only guest holds no role, so it reads but every write (hasRole) returns 403.
 *
 * <p>Tokens: the {@code /api/auth/login} endpoint verifies bcrypt credentials and returns a short-lived
 * access token (sent as a Bearer header by the SPA) plus a long-lived refresh token in an httpOnly cookie.
 *
 * <p>CSRF stays disabled and is safe here: write endpoints authenticate via the Bearer header, which a
 * cross-site page cannot make the browser attach (it is not a cookie). The only cookie — the refresh token
 * — is scoped to {@code /api/auth}, SameSite=Lax, and its response body cannot be read cross-origin, so
 * CSRF against {@code /refresh} yields nothing usable.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final SecretKey hmacKey;

    public SecurityConfig(JwtProperties props) {
        this.hmacKey = new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = restAuthenticationEntryPoint();
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Prometheus scrape endpoint: open, but reachable only on the internal Docker
                        // network (nginx never proxies /actuator), so it is not publicly exposed. Other
                        // actuator endpoints stay behind the catch-all authenticated() rule.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // Interactive API docs and the OpenAPI spec are open for dev/demo; a fronting
                        // nginx withholds these paths in production.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                        // Token endpoints must be reachable without a token.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        // Master data setup is administrative.
                        .requestMatchers(HttpMethod.POST, "/api/masterdata/**").hasRole("ADMIN")
                        // HR master data (departments, positions, employees) — HR staff and admin.
                        .requestMatchers(HttpMethod.POST, "/api/hr/**").hasAnyRole("ADMIN", "HR")
                        // Financial postings (most specific purchasing/sales paths first).
                        .requestMatchers(HttpMethod.POST, "/api/purchasing/vendor-bills").hasRole("ACCOUNTANT")
                        .requestMatchers(HttpMethod.POST, "/api/sales/sales-invoices",
                                "/api/sales/customer-returns").hasRole("ACCOUNTANT")
                        .requestMatchers(HttpMethod.POST, "/api/payments/**").hasRole("ACCOUNTANT")
                        .requestMatchers(HttpMethod.POST, "/api/ledger/**").hasRole("ACCOUNTANT")
                        // Sales orders are raised by sales.
                        .requestMatchers(HttpMethod.POST, "/api/sales/sales-orders/**").hasRole("SALES")
                        // Physical movements are warehouse/operations.
                        .requestMatchers(HttpMethod.POST, "/api/sales/deliveries").hasRole("WAREHOUSE")
                        .requestMatchers(HttpMethod.POST, "/api/purchasing/**").hasRole("WAREHOUSE")
                        .requestMatchers(HttpMethod.POST, "/api/inventory/**").hasRole("WAREHOUSE")
                        .requestMatchers(HttpMethod.POST, "/api/manufacturing/**").hasRole("WAREHOUSE")
                        // The audit trail is admin-only (the one GET that is role-restricted).
                        .requestMatchers(HttpMethod.GET, "/api/audit/**").hasRole("ADMIN")
                        // Everything else (reads, reports, /api/auth/me) just needs an authenticated user.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)   // suppress WWW-Authenticate: Bearer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /**
     * Returns 401 on a missing/invalid token <em>without</em> a {@code WWW-Authenticate} header (which
     * would trigger the browser's native dialog over an XHR/fetch). The SPA renders its own login page and
     * handles 401 itself (refreshing the access token where it can).
     */
    private static AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
        };
    }

    /** Maps the JWT {@code roles} claim (e.g. ["ADMIN"]) to {@code ROLE_*} authorities; principal = sub. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(hmacKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Login-flow authentication: verifies username + bcrypt password against the persisted user store. */
    @Bean
    AuthenticationManager authenticationManager(DbUserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
