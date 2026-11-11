package org.example.service;

import org.example.model.TideMeasurement;
import org.example.model.TideTable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitor de tábuas de maré em CSV (RF010 - A2).
 * Formato aceito por linha: {@code data;hora;altura} — ex.: {@code 2026-12-01;00:30;1,23}
 * ou {@code 01/12/2026;00:30;1.23}. Linhas de cabeçalho e comentários (#) são ignorados.
 */
public final class TideCsvParser {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TideCsvParser() {
    }

    public static TideTable parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<TideMeasurement> measurements = new ArrayList<>();
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw.strip().replace("﻿", "");
            if (line.isEmpty() || line.startsWith("#") || !Character.isDigit(line.charAt(0))) {
                continue; // vazio, comentário ou cabeçalho
            }
            String[] parts = line.contains(";") ? line.split(";") : line.split(",");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Linha " + lineNumber + ": esperado data;hora;altura.");
            }
            try {
                LocalDate date = parseDate(parts[0].strip());
                LocalTime time = LocalTime.parse(parts[1].strip());
                double height = Double.parseDouble(parts[2].strip().replace(',', '.'));
                measurements.add(new TideMeasurement(LocalDateTime.of(date, time), height));
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new IllegalArgumentException("Linha " + lineNumber + " inválida: \"" + line + "\".");
            }
        }
        if (measurements.isEmpty()) {
            throw new IllegalArgumentException("O arquivo não contém medições de maré.");
        }
        YearMonth month = YearMonth.from(measurements.getFirst().timestamp());
        boolean sameMonth = measurements.stream().allMatch(m -> YearMonth.from(m.timestamp()).equals(month));
        if (!sameMonth) {
            throw new IllegalArgumentException("Todas as medições do arquivo devem pertencer ao mesmo mês.");
        }
        return new TideTable(month, "CSV importado: " + file.getFileName(), measurements);
    }

    private static LocalDate parseDate(String text) {
        return text.contains("/") ? LocalDate.parse(text, BR_DATE) : LocalDate.parse(text);
    }
}
