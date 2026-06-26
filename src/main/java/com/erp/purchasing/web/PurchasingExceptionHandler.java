package com.erp.purchasing.web;

import com.erp.purchasing.application.GoodsReceiptNotFoundException;
import com.erp.purchasing.application.PurchaseOrderNotFoundException;
import com.erp.purchasing.application.PurchasingException;
import com.erp.purchasing.application.PurchasingValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates purchasing rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class PurchasingExceptionHandler {

    @ExceptionHandler({PurchaseOrderNotFoundException.class, GoodsReceiptNotFoundException.class})
    public ProblemDetail onNotFound(PurchasingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PurchasingValidationException.class)
    public ProblemDetail onBadRequest(PurchasingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PurchasingException.class)
    public ProblemDetail onUnprocessable(PurchasingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
