package com.erp.sales.web;

import com.erp.sales.application.DeliveryNotFoundException;
import com.erp.sales.application.SalesException;
import com.erp.sales.application.SalesOrderNotFoundException;
import com.erp.sales.application.SalesValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates sales rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class SalesExceptionHandler {

    @ExceptionHandler({SalesOrderNotFoundException.class, DeliveryNotFoundException.class})
    public ProblemDetail onNotFound(SalesException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SalesValidationException.class)
    public ProblemDetail onBadRequest(SalesException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(SalesException.class)
    public ProblemDetail onUnprocessable(SalesException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
