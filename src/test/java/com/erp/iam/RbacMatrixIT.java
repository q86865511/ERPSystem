package com.erp.iam;

import com.erp.TestcontainersConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.stream.Stream;

import static com.erp.iam.JwtTestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Full RBAC matrix over the write-gating rules in {@link SecurityConfig} (lines 79-98): 6 seeded accounts
 * (admin/accountant/warehouse/sales/hr/guest) × 13 representative endpoints (12 gated POSTs + the one
 * role-restricted GET) = 78 cells, reproducing the table verified manually in
 * {@code docs/TEST_REPORT_2026-07-02.md} §4 / {@code docs/test-evidence/2026-07-02/rbac-results.json}.
 *
 * <p>Judgment is authorization-only, same convention as {@link RbacIT}: a denied cell must be 403; an
 * allowed cell must be anything <em>other than</em> 401/403 (400/404/409/201 all mean the request cleared
 * the filter chain and reached the controller/service layer — bean validation and domain guards are
 * exercised elsewhere, e.g. the error-contract ITs). Bodies are minimal-but-well-formed so allowed cells
 * clear deserialization and fail no earlier than bean validation.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RbacMatrixIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwt;

    private static final String ITEMS_BODY =
            "{\"sku\":\"RBAC-1\",\"name\":\"Rbac Item\",\"itemType\":\"FG\",\"uom\":\"EA\"}";
    private static final String HR_DEPT_BODY =
            "{\"code\":\"RBAC-HR\",\"name\":\"RBAC HR Dept\",\"budgetAccountCode\":\"6100\"}";
    private static final String VENDOR_BILL_BODY =
            "{\"purchaseOrderId\":999999,\"lines\":[{\"poLineId\":1,\"qty\":1,\"unitPrice\":1}]}";
    private static final String SALES_INVOICE_BODY =
            "{\"salesOrderId\":999999,\"lines\":[{\"soLineId\":1,\"qty\":1,\"unitPrice\":1}]}";
    private static final String CUSTOMER_RETURN_BODY =
            "{\"salesInvoiceId\":999999,\"stockLocationId\":1}";
    private static final String PAY_OUT_BODY =
            "{\"partnerId\":999999,\"amount\":1,\"allocations\":[]}";
    private static final String JOURNAL_ENTRY_BODY =
            "{\"postingDate\":\"2026-07-02\",\"lines\":[]}";
    private static final String SALES_ORDER_BODY = "{\"partnerId\":999999,\"lines\":[]}";
    private static final String DELIVERY_BODY =
            "{\"salesOrderId\":999999,\"stockLocationId\":1,\"lines\":[]}";
    private static final String PURCHASE_ORDER_BODY = "{\"partnerId\":999999,\"lines\":[]}";
    private static final String ADJUSTMENT_BODY =
            "{\"itemId\":999999,\"locationId\":1,\"qtyDelta\":1}";
    private static final String WORK_ORDER_BODY =
            "{\"itemId\":999999,\"bomId\":999999,\"qtyToProduce\":1}";

    /** One endpoint under test: method/path/body, plus the single role that (besides ADMIN) may call it. */
    private record Endpoint(String method, String path, String body, String requiredRole) {
        boolean allowedFor(List<String> accountRoles) {
            return accountRoles.contains("ADMIN") || accountRoles.contains(requiredRole);
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("POST", "/api/masterdata/items", ITEMS_BODY, "ADMIN"),
            new Endpoint("POST", "/api/hr/departments", HR_DEPT_BODY, "HR"),
            new Endpoint("POST", "/api/purchasing/vendor-bills", VENDOR_BILL_BODY, "ACCOUNTANT"),
            new Endpoint("POST", "/api/sales/sales-invoices", SALES_INVOICE_BODY, "ACCOUNTANT"),
            new Endpoint("POST", "/api/sales/customer-returns", CUSTOMER_RETURN_BODY, "ACCOUNTANT"),
            new Endpoint("POST", "/api/payments/out", PAY_OUT_BODY, "ACCOUNTANT"),
            new Endpoint("POST", "/api/ledger/journal-entries", JOURNAL_ENTRY_BODY, "ACCOUNTANT"),
            new Endpoint("POST", "/api/sales/sales-orders", SALES_ORDER_BODY, "SALES"),
            new Endpoint("POST", "/api/sales/deliveries", DELIVERY_BODY, "WAREHOUSE"),
            new Endpoint("POST", "/api/purchasing/purchase-orders", PURCHASE_ORDER_BODY, "WAREHOUSE"),
            new Endpoint("POST", "/api/inventory/adjustments", ADJUSTMENT_BODY, "WAREHOUSE"),
            new Endpoint("POST", "/api/manufacturing/work-orders", WORK_ORDER_BODY, "WAREHOUSE"),
            new Endpoint("GET", "/api/audit", null, "ADMIN"));

    /** The six seeded accounts, mirroring {@link com.erp.bootstrap.UserSeeder} exactly. */
    private static final List<List<String>> ACCOUNTS = List.of(
            List.of("admin", "ADMIN", "ACCOUNTANT", "WAREHOUSE", "SALES", "HR"),
            List.of("accountant", "ACCOUNTANT"),
            List.of("warehouse", "WAREHOUSE"),
            List.of("sales", "SALES"),
            List.of("hr", "HR"),
            List.of("guest"));

    /** One matrix cell, ready to assert. {@code toString} drives the parameterized display name. */
    private record Case(String username, List<String> roles, Endpoint endpoint, boolean expectAllowed) {
        @Override
        public String toString() {
            return username + " " + endpoint.method() + " " + endpoint.path()
                    + (expectAllowed ? " -> allowed" : " -> 403");
        }
    }

    /** Cross product: 6 accounts × 13 endpoints = 78 cells, matching the report's matrix exactly. */
    static Stream<Case> cases() {
        return ACCOUNTS.stream().flatMap(account -> {
            String username = account.get(0);
            List<String> roles = account.subList(1, account.size());
            return ENDPOINTS.stream().map(ep -> new Case(username, roles, ep, ep.allowedFor(roles)));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void matrixCell(Case c) throws Exception {
        String authorization = bearer(jwt, c.username(), c.roles().toArray(String[]::new));
        MockHttpServletRequestBuilder request = "GET".equals(c.endpoint().method())
                ? get(c.endpoint().path()).header("Authorization", authorization)
                : post(c.endpoint().path()).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(c.endpoint().body());

        MvcResult result = mvc.perform(request).andReturn();
        int status = result.getResponse().getStatus();

        if (c.expectAllowed()) {
            // The route must resolve to a real controller method — guards against silent path/method
            // drift turning cells green via a routing 404/405. A handler-level 404 (minimal body points
            // at entities that don't exist) is fine: only the authorization layer is under test here.
            assertThat(result.getHandler()).as("%s — endpoint no longer routes", c).isNotNull();
            assertThat(status).as(c.toString()).isNotIn(401, 403);
        } else {
            assertThat(status).as(c.toString()).isEqualTo(403);
        }
    }
}
