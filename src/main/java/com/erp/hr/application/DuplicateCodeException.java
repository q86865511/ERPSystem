package com.erp.hr.application;

/** An HR entity with the given business code already exists. */
public class DuplicateCodeException extends HrException {

    public DuplicateCodeException(String entity, String code) {
        super("a " + entity + " with code " + code + " already exists");
    }
}
