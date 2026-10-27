package com.junaid.recon.service;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String runId) {
        super("No reconciliation run with id " + runId);
    }
}
