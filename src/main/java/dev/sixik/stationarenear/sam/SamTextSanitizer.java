package dev.sixik.stationarenear.sam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SamTextSanitizer {
    public static final int MAX_NETWORK_TEXT_LENGTH = 512;
    private static final int MAX_SYNTHESIS_CHUNK_LENGTH = 96;

    private SamTextSanitizer() {
    }

    public static String normalizeForNetwork(String text) {
        String normalized = normalize(text);
        if (normalized.length() > MAX_NETWORK_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_NETWORK_TEXT_LENGTH).trim();
        }
        return normalized;
    }

    public static List<String> splitForSynthesis(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String token : normalized.split(" ")) {
            if (token.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + 1 + token.length() > MAX_SYNTHESIS_CHUNK_LENGTH) {
                addChunk(chunks, current);
            }
            if (token.length() > MAX_SYNTHESIS_CHUNK_LENGTH) {
                int offset = 0;
                while (offset < token.length()) {
                    int end = Math.min(token.length(), offset + MAX_SYNTHESIS_CHUNK_LENGTH);
                    chunks.add(token.substring(offset, end));
                    offset = end;
                }
                continue;
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(token);
            char last = token.charAt(token.length() - 1);
            if ((last == '.' || last == '!' || last == '?' || last == ':') && current.length() >= 32) {
                addChunk(chunks, current);
            }
        }
        addChunk(chunks, current);
        return chunks;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2026', '.')
                .replaceAll("([.!?:;,])(?=\\S)", "$1 ")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);

        StringBuilder ascii = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (character >= 32 && character <= 126) {
                ascii.append(character);
            } else {
                ascii.append(' ');
            }
        }
        return ascii.toString().replaceAll("\\s+", " ").trim();
    }

    private static void addChunk(List<String> chunks, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        String chunk = current.toString().trim();
        if (!chunk.isBlank()) {
            chunks.add(chunk);
        }
        current.setLength(0);
    }
}