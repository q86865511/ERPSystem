package com.erp.iam;

import com.erp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.erp.iam.JwtTestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access control over the security filter chain (MockMvc, no socket). Authorization is driven by
 * the {@code roles} claim in a real JWT (decoded by the resource server, mapped to {@code ROLE_*}). Reads
 * need any authenticated user; writes are gated by role (goods receipt → WAREHOUSE, vendor bill →
 * ACCOUNTANT), admin is a superuser, and the read-only guest (no role) reads but cannot write. 401 =
 * unauthenticated, 403 = wrong role; any other status means authorization passed (empty body then fails
 * business validation, which is fine here).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RbacIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwt;

    @Test
    void unauthenticatedReadIsRejected() throws Exception {
        mvc.perform(get("/api/reporting/trial-balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyAuthenticatedRoleMayRead() throws Exception {
        mvc.perform(get("/api/reporting/balance-sheet")
                        .header("Authorization", bearer(jwt, "sales", "SALES")))
                .andExpect(status().isOk());
    }

    @Test
    void readOnlyGuestMayReadButNotWrite() throws Exception {
        // guest holds no role: reads succeed, every write is forbidden.
        mvc.perform(get("/api/reporting/balance-sheet").header("Authorization", bearer(jwt, "guest")))
                .andExpect(status().isOk());
        mvc.perform(postBody("/api/purchasing/goods-receipts", bearer(jwt, "guest"), EMPTY_LINES))
                .andExpect(status().isForbidden());
    }

    // Bodies that pass deserialization and reach a *handled* validation error (HTTP 400), so the
    // observed status reflects authorization rather than an unhandled controller NPE.
    private static final String EMPTY_LINES = "{\"lines\":[]}";
    private static final String SO_BODY = "{\"partnerId\":999999,\"lines\":[]}";

    @Test
    void wrongRoleIsForbidden() throws Exception {
        // Authorization is denied at the filter chain before the controller runs (body irrelevant).
        mvc.perform(postBody("/api/purchasing/goods-receipts", bearer(jwt, "sales", "SALES"), EMPTY_LINES))
                .andExpect(status().isForbidden());
        mvc.perform(postBody("/api/purchasing/vendor-bills", bearer(jwt, "warehouse", "WAREHOUSE"), EMPTY_LINES))
                .andExpect(status().isForbidden());
    }

    @Test
    void rightRolePassesAuthorization() throws Exception {
        assertThat(postStatus("/api/purchasing/goods-receipts", bearer(jwt, "warehouse", "WAREHOUSE"), EMPTY_LINES))
                .isNotIn(401, 403);
        assertThat(postStatus("/api/purchasing/vendor-bills", bearer(jwt, "accountant", "ACCOUNTANT"), EMPTY_LINES))
                .isNotIn(401, 403);
    }

    @Test
    void adminIsSuperuser() throws Exception {
        String admin = bearer(jwt, "admin", "ADMIN", "ACCOUNTANT", "WAREHOUSE", "SALES");
        assertThat(postStatus("/api/purchasing/goods-receipts", admin, EMPTY_LINES)).isNotIn(401, 403);
        assertThat(postStatus("/api/sales/sales-orders", admin, SO_BODY)).isNotIn(401, 403);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postBody(
            String path, String authorization, String body) {
        return post(path).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int postStatus(String path, String authorization, String body) throws Exception {
        return mvc.perform(postBody(path, authorization, body)).andReturn().getResponse().getStatus();
    }
}
