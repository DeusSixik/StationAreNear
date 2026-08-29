package dev.sixik.stationarenear.navigation;

import java.util.Locale;
import java.util.UUID;

public final class StationCodeGenerator {

    private static final String PREFIX = "ST";
    private static final int CODE_LENGTH = 6;

    private StationCodeGenerator() {
    }

    public static String code(long seed) {
        long mixed = mix(seed);
        long value = Math.floorMod(mixed, pow36(CODE_LENGTH));
        String text = Long.toString(value, 36).toUpperCase(Locale.ROOT);
        return PREFIX + "-" + "0".repeat(Math.max(0, CODE_LENGTH - text.length())) + text;
    }

    public static String code(long seed, float x, float y) {
        long value = seed
                ^ ((long) Float.floatToIntBits(x) << 32)
                ^ Integer.toUnsignedLong(Float.floatToIntBits(y));
        return code(value);
    }

    public static String code(UUID stationId) {
        if (stationId == null) {
            return code(0L);
        }
        return code(stationId.getMostSignificantBits() ^ Long.rotateLeft(stationId.getLeastSignificantBits(), 17));
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.startsWith(PREFIX) && normalized.length() > PREFIX.length()) {
            return normalized.substring(PREFIX.length());
        }
        return normalized;
    }

    public static boolean matches(String query, String stationCode) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return false;
        }
        return normalize(stationCode).equals(normalizedQuery);
    }

    public static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value & Long.MAX_VALUE;
    }

    public static long pow36(int length) {
        long value = 1L;
        for (int i = 0; i < length; i++) {
            value *= 36L;
        }
        return value;
    }
}
