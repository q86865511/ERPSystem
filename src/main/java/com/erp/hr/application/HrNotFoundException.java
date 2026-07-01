package com.erp.hr.application;

/** A referenced HR entity (department, position or employee) does not exist. */
public class HrNotFoundException extends HrException {

    public HrNotFoundException(String what) {
        super("no " + what);
    }
}
