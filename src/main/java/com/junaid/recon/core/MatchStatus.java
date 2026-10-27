package com.junaid.recon.core;

/** Outcome of reconciling a bank transaction against the ledger. */
public enum MatchStatus {
    /** Same reference, amount and date within tolerance. */
    MATCHED(true),
    /** References differ, but account, currency, amount and date line up. Needs a quick human look. */
    PROBABLE_MATCH(true),
    AMOUNT_MISMATCH(false),
    DATE_MISMATCH(false),
    CURRENCY_MISMATCH(false),
    /** In the bank feed but missing from the ledger. */
    UNMATCHED_BANK(false),
    /** In the ledger but missing from the bank feed. */
    UNMATCHED_LEDGER(false),
    /** Same reference seen twice on the same side. */
    DUPLICATE(false);

    private final boolean reconciled;

    MatchStatus(boolean reconciled) {
        this.reconciled = reconciled;
    }

    /** True when no follow-up is needed to close the item. */
    public boolean isReconciled() {
        return reconciled;
    }
}
