package net.sixik.sam;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            return;
        }

        SamConfig.Builder config = SamConfig.builder();
        boolean phonetic = false;
        boolean playOpenAl = false;
        Path output = Path.of("sam.wav");
        List<String> textParts = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-phonetic" -> phonetic = true;
                case "-play-openal" -> playOpenAl = true;
                case "-sing" -> config.singMode(true);
                case "-debug" -> config.debug(true);
                case "-pitch" -> config.pitch(parseValue(args, ++i, "pitch"));
                case "-speed" -> config.speed(parseValue(args, ++i, "speed"));
                case "-mouth" -> config.mouth(parseValue(args, ++i, "mouth"));
                case "-throat" -> config.throat(parseValue(args, ++i, "throat"));
                case "-wav" -> output = Path.of(requireValue(args, ++i, "wav"));
                default -> textParts.add(args[i]);
            }
        }

        if (textParts.isEmpty()) {
            printUsage();
            return;
        }

        String text = String.join(" ", textParts);
        SamSynthesizer synthesizer = new SamSynthesizer();
        SamConfig samConfig = config.build();
        SamResult result = phonetic
            ? synthesizer.synthesizePhonetic(text, samConfig)
            : synthesizer.synthesize(text, samConfig);

        result.writeWav(output);
        if (playOpenAl) {
            if (phonetic) {
                synthesizer.playPhoneticOpenAl(text, samConfig);
            } else {
                synthesizer.playOpenAl(text, samConfig);
            }
        }
        System.out.println("Saved WAV to " + output.toAbsolutePath());
        System.out.println("PCM bytes: " + result.getPcm().length);
    }

    private static int parseValue(String[] args, int index, String option) {
        return Integer.parseInt(requireValue(args, index, option));
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for -" + option);
        }
        return args[index];
    }

    private static void printUsage() {
        System.out.println("Usage: sam [-phonetic] [-play-openal] [-sing] [-debug] [-pitch n] [-speed n] [-mouth n] [-throat n] [-wav file] text");
    }
}
