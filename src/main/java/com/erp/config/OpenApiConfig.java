package com.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Top-level OpenAPI metadata plus a JWT Bearer security scheme, so Swagger UI shows an "Authorize" button
 * (paste an access token from {@code POST /api/auth/login}) and the generated client knows requests carry
 * a Bearer token.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    static {
        // Document BigDecimal as a string in the spec, matching the runtime serialization
        // (see JacksonConfig). Otherwise springdoc would infer `number` from the Java type and the
        // generated TypeScript client would mis-type money/quantities it actually receives as strings.
        SpringDocUtils.getConfig().replaceWithClass(BigDecimal.class, String.class);
    }

    @Bean
    OpenAPI erpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Manufacturing ERP API")
                        .version("0.0.1")
                        .description("Modular-monolith manufacturing ERP — double-entry ledger, "
                                + "inventory, procure-to-pay, order-to-cash, manufacturing and reporting."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
