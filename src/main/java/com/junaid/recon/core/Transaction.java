package com.junaid.recon.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * An immutable money movement from either the bank feed or the internal ledger.
 * Amounts are normalised to two decimal places so 10.5 and 10.50 compare as equal.
 */
public record Transaction(
        String id,
        Source source,
        String reference,
        String accountId,
        BigDecimal amount,
        String currency,
        LocalDate valueDate,
        String description) {

    public Transaction {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(reference, "reference is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        Objects.requireNonNull(valueDate, "valueDate is required");
        reference = reference.trim();
        if (reference.isEmpty()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code: " + currency);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        accountId = accountId == null || accountId.isBlank() ? null : accountId.trim();
        description = description == null ? "" : description.trim();
    }

    /** Reference used for matching: case-insensitive and whitespace-trimmed. */
    public String matchKey() {
        return reference.toUpperCase(Locale.ROOT);
    }
}
