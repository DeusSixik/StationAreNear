package net.sixik.sam;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Small usage examples for embedding SAM in another Java application.
 */
public final class SamExample {
    private SamExample() {}

    public static void basicExample() throws IOException {
        SamSynthesizer sam = new SamSynthesizer();
        sam.synthesizeToFile("Hello from SAM", Path.of("hello.wav"));
    }

    public static void configuredExample() throws IOException {
        SamSynthesizer sam = new SamSynthesizer();
        SamConfig config = SamConfig.builder()
            .speed(84)
            .pitch(60)
            .mouth(128)
            .throat(140)
            .build();

        SamResult result = sam.synthesize("Configured voice example", config);
        result.writeWav(Path.of("configured.wav"));
    }

    public static void phoneticExample() throws IOException {
        SamSynthesizer sam = new SamSynthesizer();
        SamConfig config = SamConfig.builder().singMode(true).build();
        SamResult result = sam.synthesizePhonetic("/HEH3LOW WERLD.", config);
        result.writeWav(Path.of("phonetic.wav"));
    }

    public static void openAlPlaybackExample() {
        SamSynthesizer sam = new SamSynthesizer();
        sam.playOpenAl("OpenAL playback example");
    }
}
