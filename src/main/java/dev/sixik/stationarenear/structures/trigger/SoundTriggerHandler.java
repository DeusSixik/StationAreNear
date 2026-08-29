package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

public final class SoundTriggerHandler {

    private SoundTriggerHandler() {
    }

    public static void handleTrigger(StationTriggerEvent event) {
        PlacedTriggerZone zone = event.getZone();
        if (StationStructureTriggerType.from(zone.type()) != StationStructureTriggerType.SOUND_TRIGGER) {
            return;
        }
        CompoundTag data = zone.data();
        boolean once = !data.contains("once") || data.getBoolean("once");
        if (once && !event.isFirstGlobalActivation()) {
            return;
        }

        String soundId = extractSoundId(data);
        if (soundId.isBlank()) {
            return;
        }

        float volume = data.contains("volume") ? data.getFloat("volume") : 1.0F;
        float pitch = data.contains("pitch") ? data.getFloat("pitch") : 1.0F;
        if (data.contains("randomPitch")) {
            float variance = data.getFloat("randomPitch");
            pitch += (event.getLevel().random.nextFloat() * 2.0F - 1.0F) * variance;
        }

        SoundSource source = SoundSource.AMBIENT;
        if (data.contains("source", Tag.TAG_STRING)) {
            try {
                source = SoundSource.valueOf(data.getString("source").toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
        }

        BlockPos centerPos = new BlockPos(
                (zone.min().getX() + zone.max().getX()) / 2,
                (zone.min().getY() + zone.max().getY()) / 2,
                (zone.min().getZ() + zone.max().getZ()) / 2
        );

        ResourceLocation loc = ResourceLocation.tryParse(soundId);
        if (loc != null) {
            SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(loc);
            if (soundEvent != null) {
                boolean global = data.contains("global") && data.getBoolean("global");
                if (global) {
                    event.getLevel().playSound(null, centerPos, soundEvent, source, volume, pitch);
                } else {
                    event.getLevel().playSound(null, event.getPlayer().blockPosition(), soundEvent, source, volume, pitch);
                }
            }
        }
    }

    public static void handleSpawnTrigger(StationStructureSpawnTriggerEvent event) {
        PlacedTriggerZone zone = event.getZone();
        if (StationStructureTriggerType.from(zone.type()) != StationStructureTriggerType.SOUND_TRIGGER) {
            return;
        }
        CompoundTag data = zone.data();
        if (!data.contains("playOnSpawn") || !data.getBoolean("playOnSpawn")) {
            return;
        }

        String soundId = extractSoundId(data);
        if (soundId.isBlank()) {
            return;
        }

        float volume = data.contains("volume") ? data.getFloat("volume") : 1.0F;
        float pitch = data.contains("pitch") ? data.getFloat("pitch") : 1.0F;
        SoundSource source = SoundSource.AMBIENT;
        if (data.contains("source", Tag.TAG_STRING)) {
            try {
                source = SoundSource.valueOf(data.getString("source").toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
        }

        BlockPos centerPos = new BlockPos(
                (zone.min().getX() + zone.max().getX()) / 2,
                (zone.min().getY() + zone.max().getY()) / 2,
                (zone.min().getZ() + zone.max().getZ()) / 2
        );

        ResourceLocation loc = ResourceLocation.tryParse(soundId);
        if (loc != null) {
            SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(loc);
            if (soundEvent != null) {
                event.getLevel().playSound(null, centerPos, soundEvent, source, volume, pitch);
            }
        }
    }

    private static String extractSoundId(CompoundTag data) {
        if (data.contains("sound", Tag.TAG_STRING)) {
            return data.getString("sound");
        }
        if (data.contains("soundId", Tag.TAG_STRING)) {
            return data.getString("soundId");
        }
        if (data.contains("sound_id", Tag.TAG_STRING)) {
            return data.getString("sound_id");
        }
        return "";
    }
}