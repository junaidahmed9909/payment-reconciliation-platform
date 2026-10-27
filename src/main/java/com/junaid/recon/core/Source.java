package com.junaid.recon.core;

/** Where a transaction came from. The bank feed is the external truth; the ledger is our internal book. */
public enum Source {
    BANK,
    LEDGER
}
