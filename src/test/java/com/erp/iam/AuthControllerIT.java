package com.erp.iam;

import com.erp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.erp.iam.JwtTestTokens.bearer;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/auth/me} is the SPA's role source. Unauthenticated → 401; an authenticated user gets
 * their username and roles (without the {@code ROLE_} prefix) derived from the JWT roles claim.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwt;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminGetsAllFourRoles() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(jwt, "admin", "ADMIN", "ACCOUNTANT", "WAREHOUSE", "SALES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ADMIN", "ACCOUNTANT", "WAREHOUSE", "SALES")));
    }

    @Test
    void scopedUserGetsOnlyItsRole() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(jwt, "sales", "SALES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("sales"))
                .andExpect(jsonPath("$.roles", contains("SALES")));
    }
}
