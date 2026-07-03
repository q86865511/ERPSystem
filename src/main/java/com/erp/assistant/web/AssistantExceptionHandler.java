package com.erp.assistant.web;

import com.erp.assistant.application.AssistantBusyException;
import com.erp.assistant.application.AssistantDisabledException;
import com.erp.assistant.application.AssistantRateLimitedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates assistant errors into RFC 9457 problem responses, matching the per-module advice style: a chat
 * attempted while the assistant is disabled (503), a chat rejected because the SSE executor is at capacity
 * (503), and a per-user rate limit exceeded (429, with a {@code Retry-After} hint).
 *
 * <p>Handlers return {@code ResponseEntity<ProblemDetail>} with an explicit
 * {@code application/problem+json} content type rather than a bare {@link ProblemDetail}: {@code POST
 * /chat} is called with {@code Accept: text/event-stream} (the SSE client's default), and without a pinned
 * content type Spring's content negotiation can otherwise fail that Accept header (406) or, on an
 * async/SSE-mapped handler, render only the status with no body instead of problem+json.
 */
@RestControllerAdvice
public class AssistantExceptionHandler {

    @ExceptionHandler(AssistantDisabledException.class)
    public ResponseEntity<ProblemDetail> onDisabled(AssistantDisabledException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(AssistantBusyException.class)
    public ResponseEntity<ProblemDetail> onBusy(AssistantBusyException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(AssistantRateLimitedException.class)
    public ResponseEntity<ProblemDetail> onRateLimited(AssistantRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
