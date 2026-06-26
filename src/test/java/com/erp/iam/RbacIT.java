package com.erp.iam;

import com.erp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access control over the security filter chain (MockMvc, no socket). Reads need any
 * authenticated user; writes are gated by role (goods receipt → WAREHOUSE, vendor bill → ACCOUNTANT),
 * and admin is a superuser. 401 = unauthenticated, 403 = wrong role; any other status means
 * authorization passed (the empty body then fails business validation, which is fine here).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RbacIT {

    @Autowired
    private MockMvc mvc;

    private static String basic(String user, String password) {
        String token = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    @Test
    void unauthenticatedReadIsRejected() throws Exception {
        mvc.perform(get("/api/reporting/trial-balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyAuthenticatedRoleMayRead() throws Exception {
        mvc.perform(get("/api/reporting/balance-sheet").header("Authorization", basic("sales", "sales")))
                .andExpect(status().isOk());
    }

    // Bodies that pass deserialization and reach a *handled* validation error (HTTP 400), so the
    // observed status reflects authorization rather than an unhandled controller NPE.
    private static final String EMPTY_LINES = "{\"lines\":[]}";
    private static final String SO_BODY = "{\"partnerId\":999999,\"lines\":[]}";

    @Test
    void wrongRoleIsForbidden() throws Exception {
        // Authorization is denied at the filter chain before the controller runs (body irrelevant).
        mvc.perform(postBody("/api/purchasing/goods-receipts", basic("sales", "sales"), EMPTY_LINES))
                .andExpect(status().isForbidden());
        mvc.perform(postBody("/api/purchasing/vendor-bills", basic("warehouse", "warehouse"), EMPTY_LINES))
                .andExpect(status().isForbidden());
    }

    @Test
    void rightRolePassesAuthorization() throws Exception {
        assertThat(postStatus("/api/purchasing/goods-receipts", basic("warehouse", "warehouse"), EMPTY_LINES))
                .isNotIn(401, 403);
        assertThat(postStatus("/api/purchasing/vendor-bills", basic("accountant", "accountant"), EMPTY_LINES))
                .isNotIn(401, 403);
    }

    @Test
    void adminIsSuperuser() throws Exception {
        assertThat(postStatus("/api/purchasing/goods-receipts", basic("admin", "admin"), EMPTY_LINES))
                .isNotIn(401, 403);
        assertThat(postStatus("/api/sales/sales-orders", basic("admin", "admin"), SO_BODY))
                .isNotIn(401, 403);
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
