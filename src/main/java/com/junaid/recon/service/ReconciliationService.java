package com.junaid.recon.service;

import com.junaid.recon.api.ResultView;
import com.junaid.recon.api.RunResponse;
import com.junaid.recon.core.CsvTransactionParser;
import com.junaid.recon.core.MatchStatus;
import com.junaid.recon.core.ReconciliationEngine;
import com.junaid.recon.core.ReconciliationReport;
import com.junaid.recon.core.ReconciliationResult;
import com.junaid.recon.core.ReconciliationSummary;
import com.junaid.recon.core.Source;
import com.junaid.recon.persistence.ReconciliationRecord;
import com.junaid.recon.persistence.ReconciliationRecordRepository;
import com.junaid.recon.persistence.ReconciliationRun;
import com.junaid.recon.persistence.ReconciliationRunRepository;
import com.junaid.recon.persistence.ResultWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs batch reconciliations and stores every outcome. */
@Service
public class ReconciliationService {

    /** Run id used for results produced by the streaming path. */
    public static final String STREAM_RUN_ID = "stream";

    private static final Set<MatchStatus> NEEDS_REVIEW = EnumSet.complementOf(EnumSet.of(MatchStatus.MATCHED));

    private final ReconciliationEngine engine;
    private final CsvTransactionParser parser;
    private final ResultWriter writer;
    private final ReconciliationRunRepository runs;
    private final ReconciliationRecordRepository records;
    private final MeterRegistry meters;
    private final Clock clock;

    public ReconciliationService(ReconciliationEngine engine, CsvTransactionParser parser, ResultWriter writer,
                                 ReconciliationRunRepository runs, ReconciliationRecordRepository records,
                                 MeterRegistry meters, Clock clock) {
        this.engine = engine;
        this.parser = parser;
        this.writer = writer;
        this.runs = runs;
        this.records = records;
        this.meters = meters;
        this.clock = clock;
    }

    @Transactional
    public RunResponse runBatch(InputStream bankCsv, InputStream ledgerCsv) {
        Instant started = clock.instant();
        CsvTransactionParser.ParseResult bank = parser.parse(new InputStreamReader(bankCsv, StandardCharsets.UTF_8), Source.BANK);
        CsvTransactionParser.ParseResult ledger = parser.parse(new InputStreamReader(ledgerCsv, StandardCharsets.UTF_8), Source.LEDGER);

        List<String> parseErrors = new ArrayList<>();
        bank.errors().forEach(e -> parseErrors.add("bank: " + e));
        ledger.errors().forEach(e -> parseErrors.add("ledger: " + e));

        ReconciliationReport report = engine.reconcile(bank.transactions(), ledger.transactions());
        String runId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        writer.write(runId, report.results(), now);

        long durationMs = Duration.between(started, clock.instant()).toMillis();
        runs.save(new ReconciliationRun(runId, report.summary().total(), report.summary().matchRatePercent(),
                parseErrors.size(), durationMs, now));
        report.results().forEach(r -> countResult(r.status(), "batch"));
        meters.timer("recon.batch.duration").record(Duration.ofMillis(durationMs));

        return new RunResponse(runId, report.summary(), durationMs, parseErrors,
                report.exceptions().stream().map(ResultView::of).toList());
    }

    @Transactional(readOnly = true)
    public RunResponse getRun(String runId) {
        ReconciliationRun run = runs.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
        List<ReconciliationRecord> rows = records.findByRunIdOrderByIdAsc(runId);
        ReconciliationSummary summary = ReconciliationSummary.of(rows.stream().map(ReconciliationRecord::getStatus).toList());
        List<ResultView> exceptions = rows.stream()
                .filter(r -> NEEDS_REVIEW.contains(r.getStatus()))
                .map(ResultView::of)
                .toList();
        return new RunResponse(runId, summary, run.getDurationMs(), List.of(), exceptions);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationRun> recentRuns() {
        return runs.findTop20ByOrderByCreatedAtDesc();
    }

    /** Pages through items needing review across all runs, newest first. */
    @Transactional(readOnly = true)
    public Page<ResultView> exceptions(MatchStatus status, int page, int size) {
        Set<MatchStatus> filter = status == null ? NEEDS_REVIEW : EnumSet.of(status);
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id"));
        return records.findByStatusIn(filter, request).map(ResultView::of);
    }

    /** Stores results coming from the streaming path. */
    @Transactional
    public void saveStreamResults(List<ReconciliationResult> results) {
        if (results.isEmpty()) {
            return;
        }
        writer.write(STREAM_RUN_ID, results, clock.instant());
        results.forEach(r -> countResult(r.status(), "stream"));
    }

    private void countResult(MatchStatus status, String mode) {
        meters.counter("recon.results", "status", status.name(), "mode", mode).increment();
    }
}
