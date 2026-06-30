package com.erp.observation.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tags every request with a correlation id: honours an inbound {@code X-Request-Id} (e.g. nginx's
 * {@code $request_id}) or generates a UUID, puts it in the SLF4J MDC so every log line on the thread
 * carries it, and echoes it back as a response header. Registered before the security filter chain (see
 * {@link ObservationWebConfig}) so 401/403 logs carry the id too. Inbound ids are sanitised to a short
 * safe charset to prevent log forging via crafted header values.
 */
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String id = inbound != null && SAFE.matcher(inbound).matches()
                ? inbound
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
