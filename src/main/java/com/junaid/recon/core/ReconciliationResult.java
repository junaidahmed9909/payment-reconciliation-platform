package com.junaid.recon.core;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One reconciliation outcome. Either side may be null (for example an unmatched ledger entry has no bank side).
 *
 * @param amountDifference bank amount minus ledger amount, or null when one side is missing
 */
public record ReconciliationResult(
        MatchStatus status,
        Transaction bank,
        Transaction ledger,
        String reason,
        BigDecimal amountDifference) {

    public ReconciliationResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(reason, "reason is required");
        if (bank == null && ledger == null) {
            throw new IllegalArgumentException("a result needs at least one transaction");
        }
    }

    static ReconciliationResult pair(MatchStatus status, Transaction bank, Transaction ledger, String reason) {
        return new ReconciliationResult(status, bank, ledger, reason, bank.amount().subtract(ledger.amount()));
    }

    static ReconciliationResult unmatched(Transaction t, String reason) {
        return t.source() == Source.BANK
                ? new ReconciliationResult(MatchStatus.UNMATCHED_BANK, t, null, reason, null)
                : new ReconciliationResult(MatchStatus.UNMATCHED_LEDGER, null, t, reason, null);
    }

    static ReconciliationResult duplicate(Transaction t) {
        String reason = "Reference " + t.reference() + " appears more than once in the " + t.source().name().toLowerCase() + " feed";
        return t.source() == Source.BANK
                ? new ReconciliationResult(MatchStatus.DUPLICATE, t, null, reason, null)
                : new ReconciliationResult(MatchStatus.DUPLICATE, null, t, reason, null);
    }

    /** Reference from whichever side is present, for display and lookups. */
    public String reference() {
        return bank != null ? bank.reference() : ledger.reference();
    }
}
