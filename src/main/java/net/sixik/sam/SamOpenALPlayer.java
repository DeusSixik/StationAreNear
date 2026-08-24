package net.sixik.sam;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_FORMAT_MONO8;
import static org.lwjgl.openal.AL10.AL_PLAYING;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDeleteBuffers;
import static org.lwjgl.openal.AL10.alDeleteSources;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourcei;
import static org.lwjgl.openal.ALC10.alcCloseDevice;
import static org.lwjgl.openal.ALC10.alcCreateContext;
import static org.lwjgl.openal.ALC10.alcDestroyContext;
import static org.lwjgl.openal.ALC10.alcMakeContextCurrent;
import static org.lwjgl.openal.ALC10.alcOpenDevice;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

public final class SamOpenALPlayer implements AutoCloseable {
    public static final int SAMPLE_RATE = 22_050;

    private final long device;
    private final long context;
    private boolean closed;

    public SamOpenALPlayer() {
        device = alcOpenDevice((ByteBuffer) null);
        if (device == 0L) {
            throw new IllegalStateException("Failed to open the default OpenAL device");
        }

        ALCCapabilities deviceCapabilities = ALC.createCapabilities(device);
        context = alcCreateContext(device, (IntBuffer) null);
        if (context == 0L) {
            alcCloseDevice(device);
            throw new IllegalStateException("Failed to create an OpenAL context");
        }

        alcMakeContextCurrent(context);
        AL.createCapabilities(deviceCapabilities);
    }

    public void play(SamResult result) {
        play(result.getPcm());
    }

    public void play(byte[] pcm) {
        ensureOpen();
        if (pcm == null || pcm.length == 0) {
            throw new IllegalArgumentException("PCM data must not be empty");
        }

        int source = alGenSources();
        int buffer = alGenBuffers();
        try {
            ByteBuffer audioBuffer = BufferUtils.createByteBuffer(pcm.length);
            audioBuffer.put(pcm).flip();

            alBufferData(buffer, AL_FORMAT_MONO8, audioBuffer, SAMPLE_RATE);
            alSourcei(source, AL_BUFFER, buffer);
            alSourcePlay(source);

            while (alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING) {
                Thread.sleep(10L);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAL playback was interrupted", exception);
        } finally {
            alDeleteSources(source);
            alDeleteBuffers(buffer);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        alcMakeContextCurrent(0L);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("OpenAL player is already closed");
        }
    }
}
