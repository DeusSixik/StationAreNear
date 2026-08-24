package dev.sixik.stationarenear.sam.client;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.sam.SamTextSanitizer;
import dev.sixik.stationarenear.sam.SamVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.sixik.sam.SamOpenALPlayer;
import net.sixik.sam.SamSynthesizer;
import org.lwjgl.BufferUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_FALSE;
import static org.lwjgl.openal.AL10.AL_FORMAT_MONO8;
import static org.lwjgl.openal.AL10.AL_GAIN;
import static org.lwjgl.openal.AL10.AL_MAX_DISTANCE;
import static org.lwjgl.openal.AL10.AL_PAUSED;
import static org.lwjgl.openal.AL10.AL_PLAYING;
import static org.lwjgl.openal.AL10.AL_POSITION;
import static org.lwjgl.openal.AL10.AL_REFERENCE_DISTANCE;
import static org.lwjgl.openal.AL10.AL_ROLLOFF_FACTOR;
import static org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDeleteBuffers;
import static org.lwjgl.openal.AL10.alDeleteSources;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alSource3f;
import static org.lwjgl.openal.AL10.alSourcef;
import static org.lwjgl.openal.AL10.alSourcei;
import static org.lwjgl.openal.AL10.alSourcePause;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourceStop;

public final class SamClientAudio {
    private static final ExecutorService SYNTHESIS_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "StationAreNear SAM Synthesizer");
        thread.setDaemon(true);
        return thread;
    });
    private static final List<PlayingSource> PLAYING_SOURCES = new ArrayList<>();

    private static SamSynthesizer synthesizer;
    private static boolean registered;
    private static boolean pausedByMinecraft;

    private SamClientAudio() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(SamClientAudio::clientTick);
    }

    public static void play(String text, SamVoice voice, Vec3 position) {
        String phrase = text == null ? "" : text.trim();
        if (phrase.isBlank()) {
            return;
        }
        SYNTHESIS_EXECUTOR.execute(() -> synthesizeAndPlay(phrase, voice, position));
    }

    private static void synthesizeAndPlay(String text, SamVoice voice, Vec3 position) {
        try {
            SamSynthesizer localSynthesizer = synthesizer();
            ByteArrayOutputStream combinedPcm = new ByteArrayOutputStream();
            for (String chunk : SamTextSanitizer.splitForSynthesis(text)) {
                byte[] pcm = localSynthesizer.synthesizePcm(chunk, voice.toConfig());
                combinedPcm.writeBytes(pcm);
                writeSilence(combinedPcm, 90);
            }
            byte[] pcm = combinedPcm.toByteArray();
            Minecraft.getInstance().execute(() -> playPcm(pcm, position));
        } catch (RuntimeException exception) {
            StationAreNear.LOGGER.warn("Failed to synthesize SAM phrase `{}`", text, exception);
        }
    }

    private static void writeSilence(ByteArrayOutputStream output, int milliseconds) {
        int samples = SamOpenALPlayer.SAMPLE_RATE * milliseconds / 1000;
        for (int i = 0; i < samples; i++) {
            output.write(128);
        }
    }

    private static SamSynthesizer synthesizer() {
        if (synthesizer == null) {
            synthesizer = new SamSynthesizer();
        }
        return synthesizer;
    }

    private static void playPcm(byte[] pcm, Vec3 position) {
        if (pcm == null || pcm.length == 0 || Minecraft.getInstance().level == null) {
            return;
        }
        try {
            int source = alGenSources();
            int buffer = alGenBuffers();
            ByteBuffer audioBuffer = BufferUtils.createByteBuffer(pcm.length);
            audioBuffer.put(pcm).flip();

            alBufferData(buffer, AL_FORMAT_MONO8, audioBuffer, SamOpenALPlayer.SAMPLE_RATE);
            alSourcei(source, AL_BUFFER, buffer);
            alSourcei(source, AL_SOURCE_RELATIVE, AL_FALSE);
            alSource3f(source, AL_POSITION, (float) position.x(), (float) position.y(), (float) position.z());
            alSourcef(source, AL_GAIN, 1.0F);
            alSourcef(source, AL_REFERENCE_DISTANCE, 12.0F);
            alSourcef(source, AL_MAX_DISTANCE, 64.0F);
            alSourcef(source, AL_ROLLOFF_FACTOR, 1.0F);
            alSourcePlay(source);
            PLAYING_SOURCES.add(new PlayingSource(source, buffer));
        } catch (RuntimeException exception) {
            StationAreNear.LOGGER.warn("Failed to play SAM audio at {}", position, exception);
        }
    }

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            stopAllSources();
            pausedByMinecraft = false;
            return;
        }

        syncPauseState(minecraft.isPaused());
        cleanupFinishedSources();
    }

    private static void syncPauseState(boolean shouldPause) {
        if (shouldPause == pausedByMinecraft) {
            return;
        }

        pausedByMinecraft = shouldPause;
        for (PlayingSource source : PLAYING_SOURCES) {
            int state = alGetSourcei(source.source(), AL_SOURCE_STATE);
            if (shouldPause && state == AL_PLAYING) {
                alSourcePause(source.source());
            } else if (!shouldPause && state == AL_PAUSED) {
                alSourcePlay(source.source());
            }
        }
    }

    private static void cleanupFinishedSources() {
        Iterator<PlayingSource> iterator = PLAYING_SOURCES.iterator();
        while (iterator.hasNext()) {
            PlayingSource source = iterator.next();
            int state = alGetSourcei(source.source(), AL_SOURCE_STATE);
            if (state == AL_PLAYING || state == AL_PAUSED) {
                continue;
            }
            deleteSource(source);
            iterator.remove();
        }
    }

    private static void stopAllSources() {
        Iterator<PlayingSource> iterator = PLAYING_SOURCES.iterator();
        while (iterator.hasNext()) {
            PlayingSource source = iterator.next();
            alSourceStop(source.source());
            deleteSource(source);
            iterator.remove();
        }
    }

    private static void deleteSource(PlayingSource source) {
        alDeleteSources(source.source());
        alDeleteBuffers(source.buffer());
    }

    private record PlayingSource(int source, int buffer) {
    }
}
