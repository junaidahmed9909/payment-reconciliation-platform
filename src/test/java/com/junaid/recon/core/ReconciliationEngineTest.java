package com.junaid.recon.core;

import static com.junaid.recon.core.TestData.DAY;
import static com.junaid.recon.core.TestData.bank;
import static com.junaid.recon.core.TestData.ledger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationEngineTest {

    private final ReconciliationEngine engine = new ReconciliationEngine(MatchingRules.defaults());

    @Test
    void matchesSameReferenceAmountAndDate() {
        ReconciliationReport report = engine.reconcile(List.of(bank("T1", "100.00")), List.of(ledger("T1", "100")));

        assertEquals(1, report.results().size());
        ReconciliationResult r = report.results().get(0);
        assertEquals(MatchStatus.MATCHED, r.status());
        assertEquals(new BigDecimal("0.00"), r.amountDifference());
        assertEquals(new BigDecimal("100.00"), report.summary().matchRatePercent());
    }

    @Test
    void referenceMatchingIgnoresCaseAndWhitespace() {
        ReconciliationReport report = engine.reconcile(List.of(bank("  t1 ", "5")), List.of(ledger("T1", "5")));
        assertEquals(MatchStatus.MATCHED, report.results().get(0).status());
    }

    @Test
    void flagsAmountMismatchWithSignedDifference() {
        ReconciliationResult r = engine.reconcile(List.of(bank("T1", "99.50")), List.of(ledger("T1", "100.00"))).results().get(0);

        assertEquals(MatchStatus.AMOUNT_MISMATCH, r.status());
        assertEquals(new BigDecimal("-0.50"), r.amountDifference());
        assertTrue(r.reason().contains("0.50"), r.reason());
    }

    @Test
    void amountToleranceAbsorbsRounding() {
        ReconciliationEngine lenient = new ReconciliationEngine(new MatchingRules(new BigDecimal("0.05"), 2));
        ReconciliationResult r = lenient.reconcile(List.of(bank("T1", "100.04")), List.of(ledger("T1", "100.00"))).results().get(0);
        assertEquals(MatchStatus.MATCHED, r.status());
    }

    @Test
    void settlementLagWithinToleranceStillMatches() {
        ReconciliationResult r = engine.reconcile(
                List.of(bank("T1", "ACC1", "10", DAY.plusDays(2))),
                List.of(ledger("T1", "ACC1", "10", DAY))).results().get(0);
        assertEquals(MatchStatus.MATCHED, r.status());
    }

    @Test
    void flagsDateMismatchBeyondTolerance() {
        ReconciliationResult r = engine.reconcile(
                List.of(bank("T1", "ACC1", "10", DAY.plusDays(3))),
                List.of(ledger("T1", "ACC1", "10", DAY))).results().get(0);
        assertEquals(MatchStatus.DATE_MISMATCH, r.status());
    }

    @Test
    void flagsCurrencyMismatchBeforeAmount() {
        Transaction eur = new Transaction("B1", Source.BANK, "T1", "ACC1", new BigDecimal("10"), "eur", DAY, "");
        ReconciliationResult r = engine.reconcile(List.of(eur), List.of(ledger("T1", "99"))).results().get(0);
        assertEquals(MatchStatus.CURRENCY_MISMATCH, r.status());
    }

    @Test
    void reportsUnmatchedOnBothSides() {
        ReconciliationReport report = engine.reconcile(
                List.of(bank("ONLY-BANK", "ACC1", "10", DAY)),
                List.of(ledger("ONLY-LEDGER", "ACC2", "20", DAY)));

        assertEquals(1, report.summary().count(MatchStatus.UNMATCHED_BANK));
        assertEquals(1, report.summary().count(MatchStatus.UNMATCHED_LEDGER));
        assertEquals(new BigDecimal("0.00"), report.summary().matchRatePercent());
        ReconciliationResult unmatchedBank = report.results().stream()
                .filter(r -> r.status() == MatchStatus.UNMATCHED_BANK).findFirst().orElseThrow();
        assertNull(unmatchedBank.ledger());
        assertNull(unmatchedBank.amountDifference());
    }

    @Test
    void findsProbableMatchWhenReferencesDiffer() {
        ReconciliationReport report = engine.reconcile(
                List.of(bank("BNK-77", "ACC9", "250.00", DAY.plusDays(1))),
                List.of(ledger("TXN-77", "ACC9", "250.00", DAY)));

        ReconciliationResult r = report.results().get(0);
        assertEquals(1, report.results().size());
        assertEquals(MatchStatus.PROBABLE_MATCH, r.status());
        assertEquals("TXN-77", r.ledger().reference());
        assertEquals(1, report.exceptions().size(), "probable matches still go to review");
    }

    @Test
    void probableMatchPicksClosestDate() {
        ReconciliationReport report = engine.reconcile(
                List.of(bank("BNK-1", "ACC9", "50", DAY)),
                List.of(ledger("FAR", "ACC9", "50", DAY.minusDays(2)), ledger("NEAR", "ACC9", "50", DAY.minusDays(1))));

        ReconciliationResult probable = report.results().stream()
                .filter(r -> r.status() == MatchStatus.PROBABLE_MATCH).findFirst().orElseThrow();
        assertEquals("NEAR", probable.ledger().reference());
        assertEquals(1, report.summary().count(MatchStatus.UNMATCHED_LEDGER));
    }

    @Test
    void noProbableMatchAcrossDifferentAccounts() {
        ReconciliationReport report = engine.reconcile(
                List.of(bank("BNK-1", "ACC1", "50", DAY)),
                List.of(ledger("TXN-1", "ACC2", "50", DAY)));
        assertEquals(0, report.summary().count(MatchStatus.PROBABLE_MATCH));
    }

    @Test
    void ledgerEntryIsNeverClaimedTwice() {
        ReconciliationReport report = engine.reconcile(
                List.of(bank("BNK-1", "ACC1", "50", DAY), bank("BNK-2", "ACC1", "50", DAY)),
                List.of(ledger("TXN-1", "ACC1", "50", DAY)));
        assertEquals(1, report.summary().count(MatchStatus.PROBABLE_MATCH));
        assertEquals(1, report.summary().count(MatchStatus.UNMATCHED_BANK));
    }

    @Test
    void flagsDuplicatesAndStillMatchesTheFirstCopy() {
        ReconciliationReport report = engine.reconcile(
                List.of(bank("T1", "10"), bank("T1", "10")),
                List.of(ledger("T1", "10")));

        assertEquals(1, report.summary().count(MatchStatus.DUPLICATE));
        assertEquals(1, report.summary().count(MatchStatus.MATCHED));
        assertEquals(2, report.summary().total());
    }

    @Test
    void rejectsTransactionsOnTheWrongSide() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.reconcile(List.of(ledger("T1", "10")), List.of()));
    }

    @Test
    void emptyInputsGiveEmptyReport() {
        ReconciliationReport report = engine.reconcile(List.of(), List.of());
        assertEquals(0, report.summary().total());
        assertEquals(new BigDecimal("0.00"), report.summary().matchRatePercent());
    }

    @Test
    void generatedDataReconcilesEveryTransaction() {
        SampleDataGenerator.Generated g = new SampleDataGenerator().generate(5_000, 7L);
        ReconciliationReport report = engine.reconcile(g.bank(), g.ledger());

        long bankSeen = report.results().stream().filter(r -> r.bank() != null).count();
        long ledgerSeen = report.results().stream().filter(r -> r.ledger() != null).count();
        assertEquals(g.bank().size(), bankSeen, "every bank transaction appears exactly once");
        assertEquals(g.ledger().size(), ledgerSeen, "every ledger transaction appears exactly once");
        assertTrue(report.summary().count(MatchStatus.PROBABLE_MATCH) > 0);
        assertTrue(report.summary().matchRatePercent().compareTo(new BigDecimal("80")) > 0);
    }
}
