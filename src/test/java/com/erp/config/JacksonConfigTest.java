package com.erp.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The BigDecimal module must emit a quoted string at full scale — never a float, never sci-notation. */
class JacksonConfigTest {

    private record Holder(BigDecimal amount) {}

    @Test
    void bigDecimalSerializesAsQuotedStringPreservingScale() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JacksonConfig().bigDecimalAsStringModule())
                .build();

        String json = mapper.writeValueAsString(new Holder(new BigDecimal("1234.5000")));

        assertThat(json).isEqualTo("{\"amount\":\"1234.5000\"}");
    }
}
