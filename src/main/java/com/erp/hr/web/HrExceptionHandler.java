package com.erp.hr.web;

import com.erp.hr.application.DuplicateCodeException;
import com.erp.hr.application.HrConflictException;
import com.erp.hr.application.HrException;
import com.erp.hr.application.HrNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates HR rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class HrExceptionHandler {

    @ExceptionHandler(HrNotFoundException.class)
    public ProblemDetail onNotFound(HrException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DuplicateCodeException.class, HrConflictException.class})
    public ProblemDetail onConflict(HrException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(HrException.class)
    public ProblemDetail onUnprocessable(HrException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
