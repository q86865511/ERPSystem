package com.erp.manufacturing.web;

import com.erp.manufacturing.application.BomNotFoundException;
import com.erp.manufacturing.application.ManufacturingException;
import com.erp.manufacturing.application.ManufacturingValidationException;
import com.erp.manufacturing.application.WorkOrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates manufacturing rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class ManufacturingExceptionHandler {

    @ExceptionHandler({BomNotFoundException.class, WorkOrderNotFoundException.class})
    public ProblemDetail onNotFound(ManufacturingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ManufacturingValidationException.class)
    public ProblemDetail onBadRequest(ManufacturingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ManufacturingException.class)
    public ProblemDetail onUnprocessable(ManufacturingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
