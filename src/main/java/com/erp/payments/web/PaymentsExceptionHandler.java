package com.erp.payments.web;

import com.erp.payments.application.PaymentException;
import com.erp.payments.application.PaymentNotFoundException;
import com.erp.payments.application.PaymentValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates payment rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class PaymentsExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail onNotFound(PaymentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ProblemDetail onBadRequest(PaymentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PaymentException.class)
    public ProblemDetail onUnprocessable(PaymentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
