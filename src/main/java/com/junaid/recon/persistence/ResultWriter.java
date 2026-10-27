package com.junaid.recon.persistence;

import com.junaid.recon.core.ReconciliationResult;
import com.junaid.recon.core.Transaction;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Bulk-inserts results with JDBC batching. JPA can't batch IDENTITY inserts,
 * so for files with tens of thousands of rows this is much faster than saveAll().
 */
@Component
public class ResultWriter {

    private static final String INSERT = """
            INSERT INTO reconciliation_record
              (run_id, status, reference, bank_reference, ledger_reference, account_id, currency,
               bank_amount, ledger_amount, amount_difference, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;

    public ResultWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(String runId, List<ReconciliationResult> results, Instant createdAt) {
        Timestamp ts = Timestamp.from(createdAt);
        jdbc.batchUpdate(INSERT, results, BATCH_SIZE, (ps, r) -> {
            Transaction any = r.bank() != null ? r.bank() : r.ledger();
            ps.setString(1, runId);
            ps.setString(2, r.status().name());
            ps.setString(3, r.reference());
            ps.setString(4, r.bank() == null ? null : r.bank().reference());
            ps.setString(5, r.ledger() == null ? null : r.ledger().reference());
            ps.setString(6, any.accountId());
            ps.setString(7, any.currency());
            ps.setBigDecimal(8, r.bank() == null ? null : r.bank().amount());
            ps.setBigDecimal(9, r.ledger() == null ? null : r.ledger().amount());
            ps.setBigDecimal(10, r.amountDifference());
            ps.setString(11, truncate(r.reason(), 512));
            ps.setTimestamp(12, ts);
        });
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
