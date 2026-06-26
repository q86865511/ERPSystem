package com.erp.ledger.web;

import com.erp.ledger.application.AccountNotFoundException;
import com.erp.ledger.application.InvalidLedgerRequestException;
import com.erp.ledger.application.LedgerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates ledger domain-rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class LedgerExceptionHandler {

    @ExceptionHandler({InvalidLedgerRequestException.class, AccountNotFoundException.class})
    public ProblemDetail onBadRequest(LedgerException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Other ledger rule violations (unbalanced entry, closed period) are unprocessable. */
    @ExceptionHandler(LedgerException.class)
    public ProblemDetail onUnprocessable(LedgerException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
