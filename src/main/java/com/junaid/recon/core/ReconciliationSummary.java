package com.junaid.recon.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Totals for a reconciliation run.
 *
 * @param matchRatePercent share of results that need no follow-up (MATCHED or PROBABLE_MATCH), 0-100
 */
public record ReconciliationSummary(int total, Map<MatchStatus, Long> countsByStatus, BigDecimal matchRatePercent) {

    public ReconciliationSummary {
        countsByStatus = Collections.unmodifiableMap(new EnumMap<>(countsByStatus));
    }

    public static ReconciliationSummary of(Collection<MatchStatus> statuses) {
        Map<MatchStatus, Long> counts = new EnumMap<>(MatchStatus.class);
        for (MatchStatus s : MatchStatus.values()) {
            counts.put(s, 0L);
        }
        long reconciled = 0;
        for (MatchStatus s : statuses) {
            counts.merge(s, 1L, Long::sum);
            if (s.isReconciled()) {
                reconciled++;
            }
        }
        int total = statuses.size();
        BigDecimal rate = total == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(reconciled * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new ReconciliationSummary(total, counts, rate);
    }

    public long count(MatchStatus status) {
        return countsByStatus.getOrDefault(status, 0L);
    }
}
