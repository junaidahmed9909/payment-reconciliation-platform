package com.junaid.recon.core;

import java.util.Locale;

/**
 * Measures raw engine throughput on generated data, without HTTP or the database.
 * Use the numbers it prints on your own machine for the README and your resume.
 *
 * Usage: java -cp target/classes com.junaid.recon.core.EngineBenchmark [transactions] [rounds]
 */
public final class EngineBenchmark {

    private EngineBenchmark() {}

    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        SampleDataGenerator.Generated data = new SampleDataGenerator().generate(count, 42L);
        ReconciliationEngine engine = new ReconciliationEngine(MatchingRules.defaults());

        // Warm up the JIT so the timed rounds reflect steady-state speed.
        for (int i = 0; i < 3; i++) {
            engine.reconcile(data.bank(), data.ledger());
        }

        long best = Long.MAX_VALUE;
        ReconciliationReport report = null;
        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
            report = engine.reconcile(data.bank(), data.ledger());
            best = Math.min(best, System.nanoTime() - start);
        }

        int processed = data.bank().size() + data.ledger().size();
        double ms = best / 1_000_000.0;
        System.out.printf(Locale.ROOT, "Transactions processed: %,d (bank %,d + ledger %,d)%n", processed, data.bank().size(), data.ledger().size());
        System.out.printf(Locale.ROOT, "Best of %d rounds:      %.1f ms%n", rounds, ms);
        System.out.printf(Locale.ROOT, "Throughput:             %,.0f transactions/sec%n", processed / (ms / 1000.0));
        System.out.printf(Locale.ROOT, "Match rate:             %s%%%n", report.summary().matchRatePercent());
        System.out.println("Breakdown:              " + report.summary().countsByStatus());
    }
}
