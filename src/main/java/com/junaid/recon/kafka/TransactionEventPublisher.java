package com.junaid.recon.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junaid.recon.api.TransactionEvent;
import com.junaid.recon.config.ReconProperties;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes transactions to Kafka. The message key is the normalised reference, so both sides of
 * a payment land on the same partition and therefore the same consumer. That's what lets each
 * consumer keep its pending map in memory without missing pairs.
 */
@Component
@ConditionalOnProperty(prefix = "recon.kafka", name = "enabled", havingValue = "true")
public class TransactionEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String topic;

    public TransactionEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json, ReconProperties props) {
        this.kafka = kafka;
        this.json = json;
        this.topic = props.kafka().transactionsTopic();
    }

    public void publish(TransactionEvent event) {
        try {
            kafka.send(topic, event.reference().trim().toUpperCase(Locale.ROOT), json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialise transaction " + event.reference(), e);
        }
    }
}
