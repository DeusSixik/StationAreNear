package net.sixik.sam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SamResult {
    private final byte[] pcm;
    private final byte[] wav;
    private final String phonetic;

    public SamResult(byte[] pcm, byte[] wav, String phonetic) {
        this.pcm = pcm;
        this.wav = wav;
        this.phonetic = phonetic;
    }

    public byte[] getPcm() {
        return pcm;
    }

    public byte[] getWav() {
        return wav;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void writeWav(Path output) throws IOException {
        Files.write(output, wav);
    }
}
