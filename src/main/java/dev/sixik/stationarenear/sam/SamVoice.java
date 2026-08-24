package dev.sixik.stationarenear.sam;

import net.minecraft.network.FriendlyByteBuf;
import net.sixik.sam.SamConfig;

public record SamVoice(int speed, int pitch, int mouth, int throat) {
    public static final SamVoice DEFAULT = new SamVoice(72, 64, 128, 128);

    private static final SamVoice[] PRESETS = {
            new SamVoice(72, 64, 128, 128),
            new SamVoice(82, 58, 122, 145),
            new SamVoice(64, 78, 150, 110),
            new SamVoice(92, 52, 105, 170),
            new SamVoice(70, 92, 180, 95),
            new SamVoice(105, 45, 96, 190),
            new SamVoice(58, 72, 160, 135),
            new SamVoice(88, 85, 190, 100)
    };

    public static SamVoice random(long seed) {
        int index = Math.floorMod(Long.hashCode(seed), PRESETS.length);
        return PRESETS[index];
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(speed);
        buffer.writeByte(pitch);
        buffer.writeByte(mouth);
        buffer.writeByte(throat);
    }

    public static SamVoice decode(FriendlyByteBuf buffer) {
        return new SamVoice(
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte()
        );
    }

    public SamConfig toConfig() {
        return SamConfig.builder()
                .speed(speed)
                .pitch(pitch)
                .mouth(mouth)
                .throat(throat)
                .build();
    }
}
