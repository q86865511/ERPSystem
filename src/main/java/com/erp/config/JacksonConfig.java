package com.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * Serialise every {@link BigDecimal} as a JSON <em>string</em>, never a floating-point number. Money
 * ({@code NUMERIC(19,4)}) and quantities/costs ({@code NUMERIC(19,6)}) must round-trip without IEEE-754
 * drift, and the generated OpenAPI spec (hence the TypeScript client) then types amounts as {@code string}
 * so the frontend never does float arithmetic on money.
 *
 * <p>Spring Boot's Jackson auto-configuration collects every {@link JacksonModule} bean and adds it to the
 * application {@code JsonMapper}, so exposing the module as a bean is enough — no manual mapper wiring.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JacksonModule bigDecimalAsStringModule() {
        SimpleModule module = new SimpleModule("BigDecimalAsString");
        // The single-arg overload registers the serializer against its handled type (BigDecimal).
        module.addSerializer(new ToStringSerializer(BigDecimal.class));
        return module;
    }
}
