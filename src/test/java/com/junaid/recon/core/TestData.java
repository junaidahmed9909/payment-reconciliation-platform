package com.junaid.recon.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Small builders that keep the tests readable. */
final class TestData {

    static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    private TestData() {}

    static Transaction bank(String ref, String amount) {
        return bank(ref, "ACC1", amount, DAY);
    }

    static Transaction bank(String ref, String account, String amount, LocalDate date) {
        return new Transaction("B-" + ref, Source.BANK, ref, account, new BigDecimal(amount), "USD", date, "test");
    }

    static Transaction ledger(String ref, String amount) {
        return ledger(ref, "ACC1", amount, DAY);
    }

    static Transaction ledger(String ref, String account, String amount, LocalDate date) {
        return new Transaction("L-" + ref, Source.LEDGER, ref, account, new BigDecimal(amount), "USD", date, "test");
    }
}
