package com.junaid.recon.config;

import com.junaid.recon.core.CsvTransactionParser;
import com.junaid.recon.core.MatchingRules;
import com.junaid.recon.core.ReconciliationEngine;
import com.junaid.recon.core.StreamingReconciler;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free core classes into Spring. */
@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MatchingRules matchingRules(ReconProperties props) {
        return new MatchingRules(props.amountTolerance(), props.dateToleranceDays());
    }

    @Bean
    ReconciliationEngine reconciliationEngine(MatchingRules rules) {
        return new ReconciliationEngine(rules);
    }

    @Bean
    StreamingReconciler streamingReconciler(MatchingRules rules, Clock clock) {
        return new StreamingReconciler(rules, clock);
    }

    @Bean
    CsvTransactionParser csvTransactionParser() {
        return new CsvTransactionParser();
    }
}
