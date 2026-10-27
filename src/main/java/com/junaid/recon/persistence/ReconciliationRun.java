package com.junaid.recon.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Header row for one batch run. */
@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {

    @Id
    @Column(name = "run_id")
    private String runId;

    @Column(nullable = false)
    private int total;

    @Column(name = "match_rate", nullable = false)
    private BigDecimal matchRate;

    @Column(name = "parse_errors", nullable = false)
    private int parseErrors;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReconciliationRun() {
        // for JPA
    }

    public ReconciliationRun(String runId, int total, BigDecimal matchRate, int parseErrors, long durationMs, Instant createdAt) {
        this.runId = runId;
        this.total = total;
        this.matchRate = matchRate;
        this.parseErrors = parseErrors;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    public String getRunId() { return runId; }
    public int getTotal() { return total; }
    public BigDecimal getMatchRate() { return matchRate; }
    public int getParseErrors() { return parseErrors; }
    public long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }
}
