package com.junaid.recon.api;

import com.junaid.recon.core.MatchStatus;
import com.junaid.recon.core.ReconciliationResult;
import com.junaid.recon.persistence.ReconciliationRecord;
import java.math.BigDecimal;

/** Flat, API-friendly view of one reconciliation outcome. */
public record ResultView(
        MatchStatus status,
        String reference,
        String bankReference,
        String ledgerReference,
        String accountId,
        BigDecimal bankAmount,
        BigDecimal ledgerAmount,
        BigDecimal amountDifference,
        String reason) {

    public static ResultView of(ReconciliationResult r) {
        return new ResultView(
                r.status(),
                r.reference(),
                r.bank() == null ? null : r.bank().reference(),
                r.ledger() == null ? null : r.ledger().reference(),
                r.bank() != null ? r.bank().accountId() : r.ledger().accountId(),
                r.bank() == null ? null : r.bank().amount(),
                r.ledger() == null ? null : r.ledger().amount(),
                r.amountDifference(),
                r.reason());
    }

    public static ResultView of(ReconciliationRecord r) {
        return new ResultView(r.getStatus(), r.getReference(), r.getBankReference(), r.getLedgerReference(),
                r.getAccountId(), r.getBankAmount(), r.getLedgerAmount(), r.getAmountDifference(), r.getReason());
    }
}
