package com.junaid.recon.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batch reconciliation of a bank feed against the ledger.
 *
 * <ol>
 *   <li>Flag repeated references on either side as {@link MatchStatus#DUPLICATE}.</li>
 *   <li>Pair transactions by reference and check currency, amount and date.</li>
 *   <li>For what's left, look for probable matches by account, currency, amount and date.
 *       Candidates are indexed by account and currency, so this stays fast on large files.</li>
 *   <li>Anything still unpaired is reported as unmatched.</li>
 * </ol>
 *
 * The engine is stateless and thread-safe.
 */
public final class ReconciliationEngine {

    private final MatchEvaluator evaluator;

    public ReconciliationEngine(MatchingRules rules) {
        this.evaluator = new MatchEvaluator(rules);
    }

    public ReconciliationReport reconcile(List<Transaction> bank, List<Transaction> ledger) {
        requireSource(bank, Source.BANK);
        requireSource(ledger, Source.LEDGER);

        List<ReconciliationResult> results = new ArrayList<>();
        Map<String, Transaction> bankByRef = dedupe(bank, results);
        Map<String, Transaction> ledgerByRef = dedupe(ledger, results);

        // Pass 1: exact reference pairing.
        List<Transaction> unpairedBank = new ArrayList<>();
        for (Transaction b : bankByRef.values()) {
            Transaction l = ledgerByRef.remove(b.matchKey());
            if (l != null) {
                results.add(evaluator.evaluate(b, l));
            } else {
                unpairedBank.add(b);
            }
        }

        // Pass 2: probable matches among what's left.
        Map<String, List<Transaction>> candidates = new HashMap<>();
        for (Transaction l : ledgerByRef.values()) {
            if (l.accountId() != null) {
                candidates.computeIfAbsent(bucket(l), k -> new ArrayList<>()).add(l);
            }
        }
        Set<Transaction> claimedLedger = new HashSet<>();
        for (Transaction b : unpairedBank) {
            Transaction best = b.accountId() == null ? null : closestCandidate(b, candidates.get(bucket(b)));
            if (best != null) {
                candidates.get(bucket(b)).remove(best);
                claimedLedger.add(best);
                results.add(ReconciliationResult.pair(MatchStatus.PROBABLE_MATCH, b, best,
                        "References differ (bank " + b.reference() + ", ledger " + best.reference()
                                + ") but account, amount and date agree"));
            } else {
                results.add(ReconciliationResult.unmatched(b, "No ledger entry found for this bank transaction"));
            }
        }

        // Pass 3: ledger leftovers.
        for (Transaction l : ledgerByRef.values()) {
            if (!claimedLedger.contains(l)) {
                results.add(ReconciliationResult.unmatched(l, "No bank transaction found for this ledger entry"));
            }
        }
        return ReconciliationReport.of(results);
    }

    private Transaction closestCandidate(Transaction bank, List<Transaction> pool) {
        if (pool == null) {
            return null;
        }
        Transaction best = null;
        long bestGap = Long.MAX_VALUE;
        for (Transaction l : pool) {
            if (evaluator.isProbableMatch(bank, l)) {
                long gap = evaluator.dayGap(bank, l);
                if (gap < bestGap) {
                    best = l;
                    bestGap = gap;
                }
            }
        }
        return best;
    }

    /** Keeps the first occurrence of each reference; later ones become DUPLICATE results. */
    private static Map<String, Transaction> dedupe(List<Transaction> txns, List<ReconciliationResult> results) {
        Map<String, Transaction> byRef = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (byRef.putIfAbsent(t.matchKey(), t) != null) {
                results.add(ReconciliationResult.duplicate(t));
            }
        }
        return byRef;
    }

    private static String bucket(Transaction t) {
        return t.accountId() + "|" + t.currency();
    }

    private static void requireSource(List<Transaction> txns, Source expected) {
        if (txns == null) {
            throw new IllegalArgumentException(expected + " transactions are required");
        }
        for (Iterator<Transaction> it = txns.iterator(); it.hasNext(); ) {
            Transaction t = it.next();
            if (t.source() != expected) {
                throw new IllegalArgumentException("Transaction " + t.id() + " is from " + t.source() + ", expected " + expected);
            }
        }
    }
}
