package com.junaid.recon.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under the {@code recon} prefix. Defaults live in application.yml.
 *
 * @param pendingMaxAge how long a streamed transaction waits for its counterpart before it's reported unmatched
 */
@ConfigurationProperties(prefix = "recon")
public record ReconProperties(
        BigDecimal amountTolerance,
        int dateToleranceDays,
        Duration pendingMaxAge,
        Kafka kafka) {

    public record Kafka(boolean enabled, String transactionsTopic, String resultsTopic, int partitions) {}
}
