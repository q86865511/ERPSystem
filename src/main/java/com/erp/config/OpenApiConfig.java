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
 * Top-level OpenAPI metadata plus an HTTP Basic security scheme, so Swagger UI shows an "Authorize"
 * button (use {@code admin}/{@code admin}) and the generated client knows requests carry Basic auth.
 */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

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
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
