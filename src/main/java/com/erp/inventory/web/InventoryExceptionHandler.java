package com.erp.inventory.web;

import com.erp.inventory.application.InventoryException;
import com.erp.inventory.application.StockMovementValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates inventory rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(StockMovementValidationException.class)
    public ProblemDetail onBadRequest(InventoryException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Other inventory rule violations (negative inventory, not stocked) are unprocessable. */
    @ExceptionHandler(InventoryException.class)
    public ProblemDetail onUnprocessable(InventoryException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
