package com.junaid.recon.service;

import com.junaid.recon.config.ReconProperties;
import com.junaid.recon.core.ReconciliationResult;
import com.junaid.recon.core.StreamingReconciler;
import com.junaid.recon.core.Transaction;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Feeds single transactions into the in-memory {@link StreamingReconciler} and stores what comes out.
 * Used by the Kafka listener, and directly by the REST API when Kafka is turned off.
 */
@Service
public class StreamingService {

    private static final Logger log = LoggerFactory.getLogger(StreamingService.class);

    private final StreamingReconciler reconciler;
    private final ReconciliationService reconciliationService;
    private final ReconProperties props;

    public StreamingService(StreamingReconciler reconciler, ReconciliationService reconciliationService,
                            ReconProperties props, MeterRegistry meters) {
        this.reconciler = reconciler;
        this.reconciliationService = reconciliationService;
        this.props = props;
        Gauge.builder("recon.stream.pending", reconciler, StreamingReconciler::pendingCount)
                .description("Transactions waiting for their counterpart")
                .register(meters);
    }

    public Optional<ReconciliationResult> process(Transaction txn) {
        Optional<ReconciliationResult> result = reconciler.accept(txn);
        result.ifPresent(r -> reconciliationService.saveStreamResults(List.of(r)));
        return result;
    }

    /** Periodically reports transactions whose counterpart never arrived. */
    @Scheduled(fixedDelayString = "${recon.expiry-check-interval:PT1M}")
    public void expireStale() {
        List<ReconciliationResult> expired = reconciler.expire(props.pendingMaxAge());
        if (!expired.isEmpty()) {
            log.info("Expired {} transactions with no counterpart", expired.size());
            reconciliationService.saveStreamResults(expired);
        }
    }

    public int pendingCount() {
        return reconciler.pendingCount();
    }
}
