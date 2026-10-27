package com.junaid.recon.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Creates realistic bank and ledger CSV files with known problems injected
 * (amount drift, late settlement, missing entries, duplicates, renamed references).
 * The same seed always gives the same files, so demos and tests are repeatable.
 *
 * Usage: java -cp target/classes com.junaid.recon.core.SampleDataGenerator [count] [outputDir] [seed]
 */
public final class SampleDataGenerator {

    public record Generated(List<Transaction> bank, List<Transaction> ledger) {}

    private static final String[] MERCHANTS = {"Grocery Mart", "City Utilities", "Payroll", "Card Settlement", "Rent", "Fuel Stop", "Insurance Premium"};

    public Generated generate(int count, long seed) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
        Random rnd = new Random(seed);
        List<Transaction> bank = new ArrayList<>();
        List<Transaction> ledger = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);

        for (int i = 1; i <= count; i++) {
            String ref = String.format("TXN%07d", i);
            String account = "ACC" + (1000 + rnd.nextInt(50));
            BigDecimal amount = BigDecimal.valueOf(500 + rnd.nextInt(500_000)).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
            LocalDate date = start.plusDays(rnd.nextInt(90));
            String desc = MERCHANTS[rnd.nextInt(MERCHANTS.length)];
            Transaction ledgerTxn = new Transaction("L" + i, Source.LEDGER, ref, account, amount, "USD", date, desc);

            int roll = rnd.nextInt(100);
            if (roll < 3) { // missing from bank
                ledger.add(ledgerTxn);
            } else if (roll < 6) { // missing from ledger
                bank.add(copy(ledgerTxn, "B" + i, Source.BANK, ref, amount, date));
            } else if (roll < 9) { // amount drift (fees, FX rounding)
                ledger.add(ledgerTxn);
                bank.add(copy(ledgerTxn, "B" + i, Source.BANK, ref, amount.subtract(BigDecimal.valueOf(1 + rnd.nextInt(2500)).movePointLeft(2)), date));
            } else if (roll < 11) { // late settlement
                ledger.add(ledgerTxn);
                bank.add(copy(ledgerTxn, "B" + i, Source.BANK, ref, amount, date.plusDays(5 + rnd.nextInt(5))));
            } else if (roll < 13) { // bank renamed the reference
                ledger.add(ledgerTxn);
                bank.add(copy(ledgerTxn, "B" + i, Source.BANK, "BNK-" + ref, amount, date.plusDays(rnd.nextInt(2))));
            } else if (roll < 14) { // duplicate in bank feed
                ledger.add(ledgerTxn);
                bank.add(copy(ledgerTxn, "B" + i, Source.BANK, ref, amount, date));
                bank.add(copy(ledgerTxn, "B" + i + "d", Source.BANK, ref, amount, date));
            } else { // clean match, sometimes settling a day later
                ledger.add(ledgerTxn);
                bank.add(copy(ledgerTxn, "B" + i, Source.BANK, ref, amount, date.plusDays(rnd.nextInt(2))));
            }
        }
        return new Generated(bank, ledger);
    }

    public void writeCsv(List<Transaction> txns, Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write("reference,account_id,amount,currency,value_date,description\n");
                for (Transaction t : txns) {
                    w.write(String.join(",", t.reference(), t.accountId(), t.amount().toPlainString(), t.currency(),
                            t.valueDate().toString(), t.description()));
                    w.write("\n");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Transaction copy(Transaction t, String id, Source source, String ref, BigDecimal amount, LocalDate date) {
        return new Transaction(id, source, ref, t.accountId(), amount, t.currency(), date, t.description());
    }

    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        Path dir = Path.of(args.length > 1 ? args[1] : "sample-data");
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        SampleDataGenerator gen = new SampleDataGenerator();
        Generated g = gen.generate(count, seed);
        gen.writeCsv(g.bank(), dir.resolve("bank.csv"));
        gen.writeCsv(g.ledger(), dir.resolve("ledger.csv"));
        System.out.printf("Wrote %d bank and %d ledger transactions to %s%n", g.bank().size(), g.ledger().size(), dir.toAbsolutePath());
    }
}
