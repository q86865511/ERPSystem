package com.erp.observation.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RequestCorrelationFilter} at highest precedence — before Spring Security's filter chain
 * (order -100) — so the correlation id is in the MDC even for requests Security rejects with 401/403.
 */
@Configuration
public class ObservationWebConfig {

    @Bean
    FilterRegistrationBean<RequestCorrelationFilter> correlationFilter() {
        FilterRegistrationBean<RequestCorrelationFilter> registration =
                new FilterRegistrationBean<>(new RequestCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
