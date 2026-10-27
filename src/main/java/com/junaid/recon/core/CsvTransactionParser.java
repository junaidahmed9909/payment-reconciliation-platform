package com.junaid.recon.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads transactions from CSV. Columns are found by header name, so their order doesn't matter.
 * Required: reference, account_id, amount, currency, value_date (yyyy-MM-dd). Optional: description.
 * Bad rows are skipped and reported, so one typo doesn't sink a whole file.
 */
public final class CsvTransactionParser {

    public record ParseResult(List<Transaction> transactions, List<String> errors) {
        public ParseResult {
            transactions = List.copyOf(transactions);
            errors = List.copyOf(errors);
        }
    }

    private static final List<String> REQUIRED = List.of("reference", "account_id", "amount", "currency", "value_date");

    public ParseResult parse(Reader reader, Source source) {
        List<Transaction> txns = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String headerLine = in.readLine();
            if (headerLine == null) {
                errors.add("File is empty");
                return new ParseResult(txns, errors);
            }
            Map<String, Integer> cols = new HashMap<>();
            List<String> header = splitLine(stripBom(headerLine));
            for (int i = 0; i < header.size(); i++) {
                cols.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
            }
            List<String> missing = REQUIRED.stream().filter(c -> !cols.containsKey(c)).toList();
            if (!missing.isEmpty()) {
                errors.add("Missing required columns: " + String.join(", ", missing));
                return new ParseResult(txns, errors);
            }

            String line;
            int lineNo = 1;
            while ((line = in.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    List<String> f = splitLine(line);
                    txns.add(new Transaction(
                            source.name() + "-" + lineNo,
                            source,
                            field(f, cols, "reference"),
                            field(f, cols, "account_id"),
                            parseAmount(field(f, cols, "amount")),
                            field(f, cols, "currency"),
                            parseDate(field(f, cols, "value_date")),
                            cols.containsKey("description") ? field(f, cols, "description") : ""));
                } catch (IllegalArgumentException | NullPointerException e) {
                    errors.add("Line " + lineNo + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read CSV", e);
        }
        return new ParseResult(txns, errors);
    }

    private static String field(List<String> fields, Map<String, Integer> cols, String name) {
        int idx = cols.get(name);
        return idx < fields.size() ? fields.get(idx).trim() : "";
    }

    private static BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount '" + raw + "'");
        }
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid date '" + raw + "', expected yyyy-MM-dd");
        }
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }

    /** Splits one CSV line, honouring double quotes and "" escapes inside quoted fields. */
    static List<String> splitLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
