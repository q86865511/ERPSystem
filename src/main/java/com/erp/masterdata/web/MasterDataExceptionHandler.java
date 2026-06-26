package com.erp.masterdata.web;

import com.erp.masterdata.application.DuplicateSkuException;
import com.erp.masterdata.application.ItemNotFoundException;
import com.erp.masterdata.application.LocationNotFoundException;
import com.erp.masterdata.application.MasterDataException;
import com.erp.masterdata.application.WarehouseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates master-data rule violations into RFC 9457 problem responses. */
@RestControllerAdvice
public class MasterDataExceptionHandler {

    @ExceptionHandler({ItemNotFoundException.class, LocationNotFoundException.class,
            WarehouseNotFoundException.class})
    public ProblemDetail onNotFound(MasterDataException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail onConflict(MasterDataException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Other master-data rule violations (e.g. a missing posting rule) are unprocessable. */
    @ExceptionHandler(MasterDataException.class)
    public ProblemDetail onUnprocessable(MasterDataException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
