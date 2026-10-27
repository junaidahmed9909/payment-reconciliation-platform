package com.junaid.recon.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Real-time reconciliation. Transactions arrive one at a time from either side; each one waits
 * until its counterpart (same reference) shows up. Items that wait too long are expired as unmatched.
 *
 * Methods are synchronized, so a single instance can be shared by concurrent Kafka consumers.
 */
public final class StreamingReconciler {

    private record Pending(Transaction txn, Instant receivedAt) {}

    private final MatchEvaluator evaluator;
    private final Clock clock;
    private final Map<String, Pending> pendingBank = new HashMap<>();
    private final Map<String, Pending> pendingLedger = new HashMap<>();

    public StreamingReconciler(MatchingRules rules, Clock clock) {
        this.evaluator = new MatchEvaluator(rules);
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Accepts one transaction.
     *
     * @return a result when this transaction completes a pair or is a duplicate; empty while it waits
     */
    public synchronized Optional<ReconciliationResult> accept(Transaction txn) {
        Objects.requireNonNull(txn, "txn is required");
        boolean isBank = txn.source() == Source.BANK;
        Map<String, Pending> own = isBank ? pendingBank : pendingLedger;
        Map<String, Pending> other = isBank ? pendingLedger : pendingBank;
        String key = txn.matchKey();

        Pending counterpart = other.remove(key);
        if (counterpart != null) {
            Transaction bank = isBank ? txn : counterpart.txn();
            Transaction ledger = isBank ? counterpart.txn() : txn;
            return Optional.of(evaluator.evaluate(bank, ledger));
        }
        if (own.containsKey(key)) {
            return Optional.of(ReconciliationResult.duplicate(txn));
        }
        own.put(key, new Pending(txn, clock.instant()));
        return Optional.empty();
    }

    /** Removes and reports every transaction that has waited longer than {@code maxAge}. */
    public synchronized List<ReconciliationResult> expire(Duration maxAge) {
        Instant cutoff = clock.instant().minus(maxAge);
        List<ReconciliationResult> expired = new ArrayList<>();
        expireFrom(pendingBank, cutoff, maxAge, expired);
        expireFrom(pendingLedger, cutoff, maxAge, expired);
        return expired;
    }

    public synchronized int pendingCount() {
        return pendingBank.size() + pendingLedger.size();
    }

    private static void expireFrom(Map<String, Pending> pending, Instant cutoff, Duration maxAge, List<ReconciliationResult> out) {
        for (Iterator<Pending> it = pending.values().iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.receivedAt().isBefore(cutoff)) {
                it.remove();
                out.add(ReconciliationResult.unmatched(p.txn(), "No counterpart received within " + maxAge.toMinutes() + " minutes"));
            }
        }
    }
}
