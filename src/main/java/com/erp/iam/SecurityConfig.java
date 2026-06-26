package com.erp.iam;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication and role-based access control. Stateless HTTP Basic over a stateless API (sessions and
 * CSRF disabled). Four roles map to the people who run each part of the business — ACCOUNTANT (financial
 * postings: bills, invoices, payments, manual entries, period close), WAREHOUSE (physical movements:
 * receipts, deliveries, stock adjustments, production), SALES (sales orders), and ADMIN (superuser,
 * master data) — enforced as request authorization at the single REST entry point. Read endpoints (GETs,
 * reports) need only authentication. JWT and a persisted user store are a deliberate later enhancement.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Master data setup is administrative.
                        .requestMatchers(HttpMethod.POST, "/api/masterdata/**").hasRole("ADMIN")
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
                        // Everything else (reads, reports) just needs an authenticated user.
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // admin is a superuser holding every role; the others are scoped to their function.
        UserDetails admin = User.withUsername("admin").password("{noop}admin")
                .roles("ADMIN", "ACCOUNTANT", "WAREHOUSE", "SALES").build();
        UserDetails accountant = User.withUsername("accountant").password("{noop}accountant")
                .roles("ACCOUNTANT").build();
        UserDetails warehouse = User.withUsername("warehouse").password("{noop}warehouse")
                .roles("WAREHOUSE").build();
        UserDetails sales = User.withUsername("sales").password("{noop}sales")
                .roles("SALES").build();
        return new InMemoryUserDetailsManager(admin, accountant, warehouse, sales);
    }
}
