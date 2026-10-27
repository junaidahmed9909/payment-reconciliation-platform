package com.junaid.recon.kafka;

import com.junaid.recon.config.ReconProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates the topics on startup when Kafka is enabled. */
@Configuration
@ConditionalOnProperty(prefix = "recon.kafka", name = "enabled", havingValue = "true")
public class KafkaTopicsConfig {

    @Bean
    NewTopic transactionsTopic(ReconProperties props) {
        return TopicBuilder.name(props.kafka().transactionsTopic()).partitions(props.kafka().partitions()).replicas(1).build();
    }

    @Bean
    NewTopic resultsTopic(ReconProperties props) {
        return TopicBuilder.name(props.kafka().resultsTopic()).partitions(props.kafka().partitions()).replicas(1).build();
    }
}
