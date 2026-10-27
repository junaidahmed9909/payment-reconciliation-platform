package com.junaid.recon.api;

import com.junaid.recon.core.Source;
import com.junaid.recon.core.Transaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A single transaction sent to the streaming API or the Kafka topic. */
public record TransactionEvent(
        String id,
        @NotNull Source source,
        @NotBlank @Size(max = 128) String reference,
        @Size(max = 64) String accountId,
        @NotNull BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull LocalDate valueDate,
        @Size(max = 256) String description) {

    public Transaction toTransaction() {
        String txnId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        return new Transaction(txnId, source, reference, accountId, amount, currency, valueDate, description);
    }

    public static TransactionEvent of(Transaction t) {
        return new TransactionEvent(t.id(), t.source(), t.reference(), t.accountId(), t.amount(), t.currency(),
                t.valueDate(), t.description());
    }
}
