package com.erp.config;

import com.erp.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.erp.iam.JwtTestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The unified error contract (report batch 2): every error path returns RFC 9457 application/problem+json.
 * - bean-validation failure (missing required fields) -> 400 (ERP-013)
 * - illegal state-machine transition -> 409 (ERP-014, was an unhandled 500)
 * - unauthenticated -> 401 problem+json (ERP-008, was plain application/json)
 * - wrong role / no role -> 403 problem+json (ERP-012, was an empty body)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractIT {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwt;

    private String admin() {
        return bearer(jwt, "admin", "ADMIN", "ACCOUNTANT", "WAREHOUSE", "SALES", "HR");
    }

    @Test
    void missingRequiredFieldsReturn400ProblemJson() throws Exception {
        // Empty body -> @NotNull/@NotEmpty on the request DTO reject it before the service runs.
        mvc.perform(post("/api/sales/sales-orders").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void illegalStateTransitionReturns409ProblemJson() throws Exception {
        String admin = admin();
        long partnerId = createId("/api/masterdata/partners", admin,
                "{\"code\":\"EC-P1\",\"name\":\"EC Partner\",\"customer\":true}");
        long itemId = createId("/api/masterdata/items", admin,
                "{\"sku\":\"EC-FG1\",\"name\":\"EC FG\",\"itemType\":\"FINISHED\",\"uom\":\"EA\"}");
        long soId = createId("/api/sales/sales-orders", admin,
                "{\"partnerId\":" + partnerId + ",\"orderDate\":\"2026-07-02\","
                        + "\"lines\":[{\"itemId\":" + itemId + ",\"qtyOrdered\":1,\"unitPrice\":10}]}");

        // First confirm succeeds (DRAFT -> CONFIRMED).
        mvc.perform(post("/api/sales/sales-orders/" + soId + "/confirm").header("Authorization", admin))
                .andExpect(status().isOk());
        // Second confirm is an illegal transition -> 409 problem+json (was an unhandled 500).
        mvc.perform(post("/api/sales/sales-orders/" + soId + "/confirm").header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unauthenticatedReturns401ProblemJson() throws Exception {
        mvc.perform(get("/api/sales/sales-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void forbiddenReturns403ProblemJson() throws Exception {
        // SALES role may not create master data -> denied at the security filter with a problem+json body.
        mvc.perform(post("/api/masterdata/items").header("Authorization", bearer(jwt, "sales", "SALES"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    private long createId(String path, String authorization, String body) throws Exception {
        String json = mvc.perform(post(path).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
