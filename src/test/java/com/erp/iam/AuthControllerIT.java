package com.erp.iam;

import com.erp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/auth/me} is the SPA's login probe and role source. Unauthenticated → 401; an
 * authenticated user gets their username and roles (without the {@code ROLE_} prefix). admin is the
 * superuser holding all four roles; scoped users get exactly their own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    @Autowired
    private MockMvc mvc;

    private static String basic(String user, String password) {
        String token = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminGetsAllFourRoles() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", basic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ADMIN", "ACCOUNTANT", "WAREHOUSE", "SALES")));
    }

    @Test
    void scopedUserGetsOnlyItsRole() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", basic("sales", "sales")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("sales"))
                .andExpect(jsonPath("$.roles", contains("SALES")));
    }
}
