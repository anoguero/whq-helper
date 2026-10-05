package com.whq.app.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

public class CardCsvService {
    private static final String DEFAULT_ENVIRONMENT = "The Old World";
    private static final String[] LEGACY_HEADER = {
            "name",
            "type",
            "environment",
            "description_text",
            "rules_text",
            "tile_image_path"};

    private static final String[] HEADER = {
            "name",
            "type",
            "environment",
            "copy_count",
            "enabled",
            "description_text",
            "rules_text",
            "tile_image_path"};

    public List<DungeonCard> importFromCsv(Path csvPath) throws IOException {
        List<List<String>> rows = parseCsv(csvPath);
        if (rows.isEmpty()) {
            return List.of();
        }

        int startRow = 0;
        if (looksLikeHeader(rows.get(0))) {
            startRow = 1;
        }

        List<DungeonCard> cards = new ArrayList<>();
        for (int i = startRow; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.isEmpty() || row.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (row.size() < 6) {
                throw new IOException("Fila CSV inválida en línea " + (i + 1) + ": columnas insuficientes");
            }

            int copyCount = 1;
            boolean enabled = true;
            int textStartColumn = 3;
            if (row.size() >= HEADER.length) {
                copyCount = parseCopyCount(row.get(3), i + 1);
                enabled = parseEnabled(row.get(4), i + 1);
                textStartColumn = 5;
            }

            cards.add(new DungeonCard(
                    0,
                    row.get(0).trim(),
                    CardType.valueOf(row.get(1).trim().toUpperCase(Locale.ROOT)),
                    normalizeEnvironment(row.get(2)),
                    copyCount,
                    enabled,
                    row.get(textStartColumn).trim(),
                    row.get(textStartColumn + 1).trim(),
                    row.get(textStartColumn + 2).trim()));
        }

        return cards;
    }

    public void exportToCsv(Path csvPath, List<DungeonCard> cards) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADER)).append("\n");

        for (DungeonCard card : cards) {
            appendCell(csv, card.getName());
            csv.append(',');
            appendCell(csv, card.getType().name());
            csv.append(',');
            appendCell(csv, normalizeEnvironment(card.getEnvironment()));
            csv.append(',');
            appendCell(csv, String.valueOf(card.getCopyCount()));
            csv.append(',');
            appendCell(csv, card.isEnabled() ? "1" : "0");
            csv.append(',');
            appendCell(csv, card.getDescriptionText());
            csv.append(',');
            appendCell(csv, card.getRulesText());
            csv.append(',');
            appendCell(csv, card.getTileImagePath());
            csv.append('\n');
        }

        Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
    }

    private String normalizeEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ENVIRONMENT;
        }
        return value.trim();
    }

    private int parseCopyCount(String rawValue, int lineNumber) throws IOException {
        try {
            int value = Integer.parseInt(rawValue.trim());
            if (value < 0) {
                throw new IOException("Fila CSV inválida en línea " + lineNumber + ": copy_count no puede ser negativo.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IOException("Fila CSV inválida en línea " + lineNumber + ": copy_count no es un número válido.");
        }
    }

    private boolean parseEnabled(String rawValue, int lineNumber) throws IOException {
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "1", "true", "yes", "si", "sí" -> true;
            case "0", "false", "no" -> false;
            default -> throw new IOException(
                    "Fila CSV inválida en línea " + lineNumber + ": enabled debe ser 0/1 o true/false.");
        };
    }

    private void appendCell(StringBuilder out, String value) {
        String safe = value == null ? "" : value;
        out.append('"').append(safe.replace("\"", "\"\"")).append('"');
    }

    private boolean looksLikeHeader(List<String> row) {
        if (row.size() >= HEADER.length) {
            for (int i = 0; i < HEADER.length; i++) {
                if (!HEADER[i].equalsIgnoreCase(row.get(i).trim())) {
                    return false;
                }
            }
            return true;
        }

        if (row.size() >= LEGACY_HEADER.length) {
            for (int i = 0; i < LEGACY_HEADER.length; i++) {
                if (!LEGACY_HEADER[i].equalsIgnoreCase(row.get(i).trim())) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private List<List<String>> parseCsv(Path csvPath) throws IOException {
        String content = Files.readString(csvPath, StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }

            if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                rows.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }

        if (inQuotes) {
            throw new IOException("CSV inválido: comillas sin cerrar");
        }

        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }

        return rows;
    }
}
