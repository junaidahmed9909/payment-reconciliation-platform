package com.junaid.recon.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTransactionParserTest {

    private final CsvTransactionParser parser = new CsvTransactionParser();

    @Test
    void parsesRowsByHeaderNameInAnyOrder() {
        String csv = "currency,amount,reference,value_date,account_id,description\n"
                + "usd,\"1,250.50\",T1,2026-03-01,ACC1,\"Rent, March\"\n";
        CsvTransactionParser.ParseResult result = parser.parse(new StringReader(csv), Source.LEDGER);

        assertTrue(result.errors().isEmpty(), result.errors().toString());
        Transaction t = result.transactions().get(0);
        assertEquals("T1", t.reference());
        assertEquals(new BigDecimal("1250.50"), t.amount());
        assertEquals("USD", t.currency());
        assertEquals(LocalDate.of(2026, 3, 1), t.valueDate());
        assertEquals("Rent, March", t.description());
        assertEquals(Source.LEDGER, t.source());
    }

    @Test
    void skipsBadRowsAndReportsLineNumbers() {
        String csv = "reference,account_id,amount,currency,value_date\n"
                + "T1,ACC1,10.00,USD,2026-03-01\n"
                + "T2,ACC1,abc,USD,2026-03-01\n"
                + "\n"
                + "T3,ACC1,5,USD,03/01/2026\n"
                + ",ACC1,5,USD,2026-03-01\n";
        CsvTransactionParser.ParseResult result = parser.parse(new StringReader(csv), Source.BANK);

        assertEquals(1, result.transactions().size());
        assertEquals(3, result.errors().size());
        assertTrue(result.errors().get(0).startsWith("Line 3:"), result.errors().get(0));
        assertTrue(result.errors().get(1).startsWith("Line 5:"), result.errors().get(1));
        assertTrue(result.errors().get(2).startsWith("Line 6:"), result.errors().get(2));
    }

    @Test
    void reportsMissingColumns() {
        CsvTransactionParser.ParseResult result = parser.parse(new StringReader("reference,amount\nT1,5\n"), Source.BANK);
        assertTrue(result.transactions().isEmpty());
        assertTrue(result.errors().get(0).contains("account_id"));
    }

    @Test
    void reportsEmptyFile() {
        CsvTransactionParser.ParseResult result = parser.parse(new StringReader(""), Source.BANK);
        assertEquals(List.of("File is empty"), result.errors());
    }

    @Test
    void handlesByteOrderMark() {
        String csv = "﻿reference,account_id,amount,currency,value_date\nT1,A,1,USD,2026-01-01\n";
        assertEquals(1, parser.parse(new StringReader(csv), Source.BANK).transactions().size());
    }

    @Test
    void roundTripsGeneratedFiles() throws Exception {
        SampleDataGenerator gen = new SampleDataGenerator();
        SampleDataGenerator.Generated g = gen.generate(200, 1L);
        Path dir = Files.createTempDirectory("recon");
        gen.writeCsv(g.bank(), dir.resolve("bank.csv"));

        CsvTransactionParser.ParseResult parsed = parser.parse(Files.newBufferedReader(dir.resolve("bank.csv")), Source.BANK);
        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        assertEquals(g.bank().size(), parsed.transactions().size());
        assertEquals(g.bank().get(0).amount(), parsed.transactions().get(0).amount());
    }
}
