package com.junaid.recon.api;

import com.junaid.recon.core.ReconciliationSummary;
import java.util.List;

/**
 * Response for a batch run.
 *
 * @param exceptions items needing human review (everything except clean matches)
 */
public record RunResponse(
        String runId,
        ReconciliationSummary summary,
        long durationMs,
        List<String> parseErrors,
        List<ResultView> exceptions) {}
