package com.junaid.recon.api;

import com.junaid.recon.kafka.TransactionEventPublisher;
import com.junaid.recon.service.StreamingService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real-time entry point. With Kafka on, transactions are queued and reconciled by the consumer.
 * With Kafka off (local dev, tests), they're reconciled immediately and the outcome is returned.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final ObjectProvider<TransactionEventPublisher> publisher;
    private final StreamingService streaming;

    public TransactionController(ObjectProvider<TransactionEventPublisher> publisher, StreamingService streaming) {
        this.publisher = publisher;
        this.streaming = streaming;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody TransactionEvent event) {
        TransactionEventPublisher kafka = publisher.getIfAvailable();
        Map<String, Object> body = new LinkedHashMap<>();
        if (kafka != null) {
            kafka.publish(event);
            body.put("status", "QUEUED");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }
        var result = streaming.process(event.toTransaction());
        if (result.isPresent()) {
            body.put("status", "RECONCILED");
            body.put("result", ResultView.of(result.get()));
        } else {
            body.put("status", "PENDING");
            body.put("pendingCount", streaming.pendingCount());
        }
        return ResponseEntity.ok(body);
    }
}
