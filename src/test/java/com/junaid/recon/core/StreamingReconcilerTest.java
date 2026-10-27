package com.junaid.recon.core;

import static com.junaid.recon.core.TestData.bank;
import static com.junaid.recon.core.TestData.ledger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamingReconcilerTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-03-10T09:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private StreamingReconciler reconciler;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        reconciler = new StreamingReconciler(MatchingRules.defaults(), clock);
    }

    @Test
    void waitsForCounterpartThenMatches() {
        assertTrue(reconciler.accept(bank("T1", "10")).isEmpty());
        assertEquals(1, reconciler.pendingCount());

        Optional<ReconciliationResult> r = reconciler.accept(ledger("T1", "10"));
        assertTrue(r.isPresent());
        assertEquals(MatchStatus.MATCHED, r.get().status());
        assertEquals(0, reconciler.pendingCount());
    }

    @Test
    void orderOfArrivalDoesNotMatter() {
        reconciler.accept(ledger("T1", "10"));
        ReconciliationResult r = reconciler.accept(bank("T1", "12")).orElseThrow();
        assertEquals(MatchStatus.AMOUNT_MISMATCH, r.status());
        assertEquals("B-T1", r.bank().id());
        assertEquals("L-T1", r.ledger().id());
    }

    @Test
    void sameSideRepeatIsDuplicate() {
        reconciler.accept(bank("T1", "10"));
        ReconciliationResult r = reconciler.accept(bank("T1", "10")).orElseThrow();
        assertEquals(MatchStatus.DUPLICATE, r.status());
        assertEquals(1, reconciler.pendingCount());
    }

    @Test
    void expiresOnlyItemsOlderThanMaxAge() {
        reconciler.accept(bank("OLD", "10"));
        clock.advance(Duration.ofMinutes(30));
        reconciler.accept(ledger("NEW", "10"));
        clock.advance(Duration.ofMinutes(31));

        List<ReconciliationResult> expired = reconciler.expire(Duration.ofMinutes(60));
        assertEquals(1, expired.size());
        assertEquals(MatchStatus.UNMATCHED_BANK, expired.get(0).status());
        assertEquals("OLD", expired.get(0).reference());
        assertEquals(1, reconciler.pendingCount());
    }

    @Test
    void handlesConcurrentProducers() throws InterruptedException {
        int n = 2_000;
        Thread banks = new Thread(() -> {
            for (int i = 0; i < n; i++) reconciler.accept(bank("T" + i, "10"));
        });
        Thread ledgers = new Thread(() -> {
            for (int i = 0; i < n; i++) reconciler.accept(ledger("T" + i, "10"));
        });
        banks.start();
        ledgers.start();
        banks.join();
        ledgers.join();
        assertEquals(0, reconciler.pendingCount(), "every pair should have met exactly once");
    }
}
