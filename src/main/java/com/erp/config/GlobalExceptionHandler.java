package com.erp.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Cross-cutting error contract for exceptions the per-module {@code @RestControllerAdvice}s don't own:
 * generic domain guards and framework request-binding failures. Every response is RFC 9457
 * {@code application/problem+json}, matching the modules' {@code ProblemDetail.forStatusAndDetail} style.
 *
 * <ul>
 *   <li>{@link IllegalStateException} — domain state-machine guards (e.g. confirm an already-confirmed order)
 *       → 409 Conflict (previously fell through to an unhandled 500).</li>
 *   <li>{@link IllegalArgumentException} — rejected input at the domain boundary → 400 Bad Request.</li>
 *   <li>Bean-validation / unreadable body — invalid or missing request payload → 400 Bad Request.</li>
 * </ul>
 *
 * Lives in {@code com.erp.config} (like JacksonConfig/OpenApiConfig): a cross-cutting package outside the
 * module-boundary ArchUnit rules, so it may reference framework types without violating them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail onIllegalState(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detail.isBlank() ? "Request validation failed" : detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onConstraintViolation(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadableBody(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed or missing request body");
    }
}
