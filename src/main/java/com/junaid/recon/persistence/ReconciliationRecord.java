package com.junaid.recon.persistence;

import com.junaid.recon.core.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One stored reconciliation outcome. Rows are written in bulk by {@link ResultWriter}; JPA is used for reads. */
@Entity
@Table(name = "reconciliation_record")
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(nullable = false)
    private String reference;

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "ledger_reference")
    private String ledgerReference;

    @Column(name = "account_id")
    private String accountId;

    private String currency;

    @Column(name = "bank_amount")
    private BigDecimal bankAmount;

    @Column(name = "ledger_amount")
    private BigDecimal ledgerAmount;

    @Column(name = "amount_difference")
    private BigDecimal amountDifference;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReconciliationRecord() {
        // for JPA
    }

    public Long getId() { return id; }
    public String getRunId() { return runId; }
    public MatchStatus getStatus() { return status; }
    public String getReference() { return reference; }
    public String getBankReference() { return bankReference; }
    public String getLedgerReference() { return ledgerReference; }
    public String getAccountId() { return accountId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBankAmount() { return bankAmount; }
    public BigDecimal getLedgerAmount() { return ledgerAmount; }
    public BigDecimal getAmountDifference() { return amountDifference; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
