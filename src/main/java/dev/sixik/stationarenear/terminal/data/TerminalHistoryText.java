package dev.sixik.stationarenear.terminal.data;

import java.util.ArrayList;
import java.util.List;

public final class TerminalHistoryText {

    public static final int DEFAULT_WRAP_WIDTH = 92;

    private TerminalHistoryText() {
    }

    public static List<TerminalHistoryLine> wrap(TerminalHistoryLine line) {
        return wrap(line, DEFAULT_WRAP_WIDTH);
    }

    public static List<TerminalHistoryLine> wrap(TerminalHistoryLine line, int width) {
        if (line == null) {
            return List.of();
        }
        int safeWidth = Math.max(16, width);
        List<TerminalHistoryLine> result = new ArrayList<>();
        for (String paragraph : line.text().split("\\R", -1)) {
            wrapParagraph(line.kind(), paragraph, safeWidth, result);
        }
        return result;
    }

    public static List<TerminalHistoryLine> wrapAll(List<TerminalHistoryLine> lines) {
        List<TerminalHistoryLine> result = new ArrayList<>();
        for (TerminalHistoryLine line : lines) {
            result.addAll(wrap(line));
        }
        return result;
    }

    private static void wrapParagraph(TerminalHistoryKind kind, String text, int width, List<TerminalHistoryLine> result) {
        if (text.length() <= width) {
            result.add(new TerminalHistoryLine(kind, text));
            return;
        }

        String remaining = text;
        String continuationIndent = continuationIndent(text);
        boolean first = true;
        while (remaining.length() > width) {
            int split = splitIndex(remaining, width);
            String part = rtrim(remaining.substring(0, split));
            result.add(new TerminalHistoryLine(kind, part));
            remaining = ltrim(remaining.substring(split));
            if (!first && remaining.startsWith(continuationIndent)) {
                remaining = remaining.substring(continuationIndent.length());
            }
            if (!continuationIndent.isEmpty() && !remaining.startsWith(continuationIndent)) {
                remaining = continuationIndent + remaining;
            }
            first = false;
        }
        if (!remaining.isBlank()) {
            result.add(new TerminalHistoryLine(kind, remaining));
        }
    }

    private static int splitIndex(String text, int width) {
        int limit = Math.min(width, text.length());
        for (int i = limit; i >= Math.max(1, limit - 24); i--) {
            if (Character.isWhitespace(text.charAt(i - 1))) {
                return i;
            }
        }
        return limit;
    }

    private static String continuationIndent(String text) {
        int spaces = 0;
        while (spaces < text.length() && Character.isWhitespace(text.charAt(spaces))) {
            spaces++;
        }
        return spaces <= 0 ? "  " : text.substring(0, Math.min(spaces, 8));
    }

    private static String ltrim(String text) {
        int index = 0;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return text.substring(index);
    }

    private static String rtrim(String text) {
        int index = text.length();
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return text.substring(0, index);
    }
}
