package com.erp.manufacturing.application;

/** Raised when a manufacturing request is structurally invalid (bad quantity, bad state, bad refs). */
public class ManufacturingValidationException extends ManufacturingException {

    public ManufacturingValidationException(String message) {
        super(message);
    }
}
