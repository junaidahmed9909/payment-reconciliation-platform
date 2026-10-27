package com.junaid.recon.core;

import java.util.List;

/** Full output of a batch run: every result plus the totals. */
public record ReconciliationReport(List<ReconciliationResult> results, ReconciliationSummary summary) {

    public ReconciliationReport {
        results = List.copyOf(results);
    }

    public static ReconciliationReport of(List<ReconciliationResult> results) {
        return new ReconciliationReport(results, ReconciliationSummary.of(results.stream().map(ReconciliationResult::status).toList()));
    }

    /** Only the items a person needs to look at. */
    public List<ReconciliationResult> exceptions() {
        return results.stream().filter(r -> !r.status().isReconciled() || r.status() == MatchStatus.PROBABLE_MATCH).toList();
    }
}
