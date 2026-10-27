package com.junaid.recon.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junaid.recon.api.ResultView;
import com.junaid.recon.api.TransactionEvent;
import com.junaid.recon.config.ReconProperties;
import com.junaid.recon.service.StreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Consumes transactions, reconciles them in real time and publishes each outcome to the results topic. */
@Component
@ConditionalOnProperty(prefix = "recon.kafka", name = "enabled", havingValue = "true")
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final StreamingService streaming;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String resultsTopic;

    public TransactionEventListener(StreamingService streaming, KafkaTemplate<String, String> kafka,
                                    ObjectMapper json, ReconProperties props) {
        this.streaming = streaming;
        this.kafka = kafka;
        this.json = json;
        this.resultsTopic = props.kafka().resultsTopic();
    }

    @KafkaListener(topics = "${recon.kafka.transactions-topic}", groupId = "recon-engine")
    public void onMessage(String payload) throws Exception {
        TransactionEvent event;
        try {
            event = json.readValue(payload, TransactionEvent.class);
        } catch (Exception e) {
            // A malformed message can never succeed, so log it and move on instead of retrying forever.
            log.warn("Skipping unreadable transaction message: {}", e.getMessage());
            return;
        }
        var result = streaming.process(event.toTransaction());
        if (result.isPresent()) {
            ResultView view = ResultView.of(result.get());
            kafka.send(resultsTopic, view.reference(), json.writeValueAsString(view));
        }
    }
}
