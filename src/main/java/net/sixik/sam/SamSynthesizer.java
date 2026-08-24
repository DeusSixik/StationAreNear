package net.sixik.sam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SamSynthesizer {
    private static final String SAM_CLASS_RESOURCE = "SamClass16";
    private static final String SAM_CLASS_NAME = "samtool.SamClass";

    private final Method samMain;

    public SamSynthesizer() {
        this.samMain = loadSamMain();
    }

    public byte[] synthesizeWav(String text) {
        return synthesize(text).getWav();
    }

    public byte[] synthesizeWav(String text, SamConfig config) {
        return synthesize(text, config).getWav();
    }

    public byte[] synthesizePcm(String text) {
        return synthesize(text).getPcm();
    }

    public byte[] synthesizePcm(String text, SamConfig config) {
        return synthesize(text, config).getPcm();
    }

    public SamResult synthesize(String text) {
        return synthesize(text, SamConfig.DEFAULT);
    }

    public SamResult synthesize(String text, SamConfig config) {
        return runSam(text, config, false);
    }

    public void synthesizeToFile(String text, Path output) throws IOException {
        synthesize(text).writeWav(output);
    }

    public void synthesizeToFile(String text, SamConfig config, Path output) throws IOException {
        synthesize(text, config).writeWav(output);
    }

    public void playOpenAl(String text) {
        playOpenAl(text, SamConfig.DEFAULT);
    }

    public void playOpenAl(String text, SamConfig config) {
        SamResult result = synthesize(text, config);
        playOpenAl(result);
    }

    public SamResult synthesizePhonetic(String phoneticInput, SamConfig config) {
        return runSam(phoneticInput, config, true);
    }

    public byte[] synthesizePhoneticWav(String phoneticInput, SamConfig config) {
        return synthesizePhonetic(phoneticInput, config).getWav();
    }

    public void playPhoneticOpenAl(String phoneticInput, SamConfig config) {
        SamResult result = synthesizePhonetic(phoneticInput, config);
        playOpenAl(result);
    }

    private SamResult runSam(String text, SamConfig config, boolean phoneticMode) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text must not be blank");
        }

        List<String> args = new ArrayList<>();
        args.add("-stdout");
        args.add("dummy");
        if (phoneticMode) {
            args.add("-phonetic");
        }
        if (config.singMode()) {
            args.add("-sing");
        }
        if (config.debug()) {
            args.add("-debug");
        }
        args.add("-pitch");
        args.add(Integer.toString(config.pitch()));
        args.add("-speed");
        args.add(Integer.toString(config.speed()));
        args.add("-mouth");
        args.add(Integer.toString(config.mouth()));
        args.add("-throat");
        args.add(Integer.toString(config.throat()));
        args.add(text);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PrintStream printStream = new PrintStream(output, true, StandardCharsets.ISO_8859_1)) {
            samMain.invoke(null, printStream, args.toArray(String[]::new));
            byte[] wav = output.toByteArray();
            return new SamResult(SamUtil.extractPcm(wav), wav, phoneticMode ? text : null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access generated SAM entrypoint", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("SAM synthesis failed: " + cause.getMessage(), cause);
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory IO failure", exception);
        }
    }

    private Method loadSamMain() {
        byte[] classBytes = readSamClassBytes();
        try {
            ByteArrayClassLoader loader = new ByteArrayClassLoader(classBytes);
            Class<?> samClass = loader.loadClass(SAM_CLASS_NAME);
            return samClass.getMethod("xmain", PrintStream.class, String[].class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to load SAM bytecode resource", exception);
        }
    }

    private byte[] readSamClassBytes() {
        try (InputStream input = SamSynthesizer.class.getClassLoader().getResourceAsStream(SAM_CLASS_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Resource `" + SAM_CLASS_RESOURCE + "` was not found");
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read SAM bytecode resource", exception);
        }
    }

    private void playOpenAl(SamResult result) {
        try (SamOpenALPlayer player = new SamOpenALPlayer()) {
            player.play(result);
        }
    }
}
