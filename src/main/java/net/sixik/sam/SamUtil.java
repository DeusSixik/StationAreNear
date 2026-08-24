package net.sixik.sam;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class SamUtil {
    private SamUtil() {}

    static byte[] extractPcm(byte[] wav) {
        if (wav.length < 44 || !isAscii(wav, 0, "RIFF") || !isAscii(wav, 8, "WAVE")) {
            return wav.clone();
        }

        int offset = 12;
        while (offset + 8 <= wav.length) {
            int chunkSize = readLeInt(wav, offset + 4);
            if (isAscii(wav, offset, "data")) {
                int dataStart = offset + 8;
                int dataEnd = Math.min(wav.length, dataStart + chunkSize);
                return Arrays.copyOfRange(wav, dataStart, dataEnd);
            }
            offset += 8 + chunkSize + (chunkSize & 1);
        }

        return wav.clone();
    }

    static void writeWav(OutputStream output, byte[] pcm) throws IOException {
        writeAscii(output, "RIFF");
        writeLeInt(output, pcm.length + 36);
        writeAscii(output, "WAVE");
        writeAscii(output, "fmt ");
        writeLeInt(output, 16);
        writeLeShort(output, 1);
        writeLeShort(output, 1);
        writeLeInt(output, 22050);
        writeLeInt(output, 22050);
        writeLeShort(output, 1);
        writeLeShort(output, 8);
        writeAscii(output, "data");
        writeLeInt(output, pcm.length);
        output.write(pcm);
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean isAscii(byte[] data, int offset, String expected) {
        if (offset + expected.length() > data.length) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if ((byte) expected.charAt(i) != data[offset + i]) {
                return false;
            }
        }
        return true;
    }

    private static int readLeInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeLeShort(OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }

    private static void writeLeInt(OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }
}
