package com.junaid.recon.core;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tolerances used when comparing two transactions.
 *
 * @param amountTolerance   largest absolute amount difference still treated as equal
 * @param dateToleranceDays largest gap in value dates still treated as equal (settlement lag)
 */
public record MatchingRules(BigDecimal amountTolerance, int dateToleranceDays) {

    public MatchingRules {
        Objects.requireNonNull(amountTolerance, "amountTolerance is required");
        if (amountTolerance.signum() < 0) {
            throw new IllegalArgumentException("amountTolerance must not be negative");
        }
        if (dateToleranceDays < 0) {
            throw new IllegalArgumentException("dateToleranceDays must not be negative");
        }
    }

    /** Exact amounts, two days of settlement lag. */
    public static MatchingRules defaults() {
        return new MatchingRules(BigDecimal.ZERO, 2);
    }
}
