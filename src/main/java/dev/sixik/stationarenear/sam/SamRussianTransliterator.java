package dev.sixik.stationarenear.sam;

import java.util.Locale;

public final class SamRussianTransliterator {
    private SamRussianTransliterator() {
    }

    public static String transliterate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String source = text.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2026', '.');

        StringBuilder output = new StringBuilder(source.length() * 3);
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char previous = previousLetter(source, i);
            char next = nextLetter(source, i);
            appendLetter(output, current, previous, next, i == 0 || !isRussianLetter(previous));
        }
        return output.toString()
                .replaceAll("([.!?:;,])(?=\\S)", "$1 ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void appendLetter(StringBuilder output, char current, char previous, char next, boolean wordStart) {
        switch (current) {
            case 'а' -> output.append("AH");
            case 'б' -> output.append("B");
            case 'в' -> output.append("V");
            case 'г' -> output.append("G");
            case 'д' -> output.append("D");
            case 'е' -> output.append(wordStart || isSofteningVowel(previous) || previous == 'ь' || previous == 'ъ' ? "YEH" : "EH");
            case 'ж' -> output.append("ZH");
            case 'з' -> output.append("Z");
            case 'и' -> output.append("EE");
            case 'й' -> output.append("Y");
            case 'к' -> output.append("K");
            case 'л' -> output.append("L");
            case 'м' -> output.append("M");
            case 'н' -> output.append("N");
            case 'о' -> output.append("OH");
            case 'п' -> output.append("P");
            case 'р' -> output.append("R");
            case 'с' -> output.append("S");
            case 'т' -> output.append("T");
            case 'у' -> output.append("OO");
            case 'ф' -> output.append("F");
            case 'х' -> output.append("KH");
            case 'ц' -> output.append("TS");
            case 'ч' -> output.append("CH");
            case 'ш' -> output.append("SH");
            case 'щ' -> output.append("SHCH");
            case 'ы' -> output.append("IH");
            case 'э' -> output.append("EH");
            case 'ю' -> output.append(wordStart || isSofteningVowel(previous) || previous == 'ь' || previous == 'ъ' ? "YOO" : "OO");
            case 'я' -> output.append(wordStart || isSofteningVowel(previous) || previous == 'ь' || previous == 'ъ' ? "YAH" : "AH");
            case 'ь', 'ъ' -> {
                if (isRussianLetter(next)) {
                    output.append('Y');
                }
            }
            default -> appendPassthrough(output, current);
        }
    }

    private static void appendPassthrough(StringBuilder output, char current) {
        if (current >= 32 && current <= 126) {
            output.append(current);
        } else if (Character.isWhitespace(current)) {
            output.append(' ');
        } else {
            output.append(' ');
        }
    }

    private static char previousLetter(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char character = text.charAt(i);
            if (Character.isLetter(character)) {
                return character;
            }
            if (!Character.isWhitespace(character) && character != '-' && character != '\'') {
                return 0;
            }
        }
        return 0;
    }

    private static char nextLetter(String text, int index) {
        for (int i = index + 1; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isLetter(character)) {
                return character;
            }
            if (!Character.isWhitespace(character) && character != '-' && character != '\'') {
                return 0;
            }
        }
        return 0;
    }

    private static boolean isSofteningVowel(char character) {
        return character == 'а' || character == 'е' || character == 'и' || character == 'о' || character == 'у'
                || character == 'ы' || character == 'э' || character == 'ю' || character == 'я';
    }

    private static boolean isRussianLetter(char character) {
        return character >= 'а' && character <= 'я';
    }
}