package com.junaid.recon.core;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Compares one bank transaction with one ledger transaction under a set of rules. Shared by batch and streaming. */
public final class MatchEvaluator {

    private final MatchingRules rules;

    public MatchEvaluator(MatchingRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules are required");
    }

    public MatchingRules rules() {
        return rules;
    }

    /** Evaluates two transactions that share a reference. Checks currency, then amount, then date. */
    public ReconciliationResult evaluate(Transaction bank, Transaction ledger) {
        requireSides(bank, ledger);
        if (!bank.currency().equals(ledger.currency())) {
            return ReconciliationResult.pair(MatchStatus.CURRENCY_MISMATCH, bank, ledger,
                    "Currency differs: bank " + bank.currency() + ", ledger " + ledger.currency());
        }
        if (!amountsAgree(bank, ledger)) {
            return ReconciliationResult.pair(MatchStatus.AMOUNT_MISMATCH, bank, ledger,
                    "Amount differs by " + bank.amount().subtract(ledger.amount()).abs().toPlainString() + " " + bank.currency());
        }
        long days = dayGap(bank, ledger);
        if (days > rules.dateToleranceDays()) {
            return ReconciliationResult.pair(MatchStatus.DATE_MISMATCH, bank, ledger,
                    "Value dates are " + days + " days apart (tolerance " + rules.dateToleranceDays() + ")");
        }
        return ReconciliationResult.pair(MatchStatus.MATCHED, bank, ledger, "Reference, amount and date agree");
    }

    /**
     * True when two transactions with different references still look like the same payment:
     * same account, same currency, agreeing amounts and dates within tolerance.
     */
    public boolean isProbableMatch(Transaction bank, Transaction ledger) {
        requireSides(bank, ledger);
        return bank.accountId() != null
                && bank.accountId().equals(ledger.accountId())
                && bank.currency().equals(ledger.currency())
                && amountsAgree(bank, ledger)
                && dayGap(bank, ledger) <= rules.dateToleranceDays();
    }

    long dayGap(Transaction a, Transaction b) {
        return Math.abs(ChronoUnit.DAYS.between(a.valueDate(), b.valueDate()));
    }

    private boolean amountsAgree(Transaction bank, Transaction ledger) {
        return bank.amount().subtract(ledger.amount()).abs().compareTo(rules.amountTolerance()) <= 0;
    }

    private static void requireSides(Transaction bank, Transaction ledger) {
        if (bank.source() != Source.BANK || ledger.source() != Source.LEDGER) {
            throw new IllegalArgumentException("expected a BANK and a LEDGER transaction");
        }
    }
}
