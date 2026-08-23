package dev.sixik.stationarenear.structures.generation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public record StationGenerationSettings(
        ResourceLocation pool,
        float missionDanger,
        boolean randomStation,
        int maxFloors,
        int minRooms,
        int maxRooms,
        long seed
) {

    public StationGenerationSettings(ResourceLocation pool, float missionDanger, boolean randomStation, int maxPieces, long seed) {
        this(pool, missionDanger, randomStation, 1, 0, maxPieces, seed);
    }

    public StationGenerationSettings {
        missionDanger = Mth.clamp(missionDanger, 0.0F, 1.0F);
        maxFloors = Math.max(1, maxFloors);
        minRooms = Math.max(0, minRooms);
        maxRooms = Math.max(1, maxRooms);
        if (minRooms > 0) {
            maxRooms = Math.max(minRooms, maxRooms);
        }
    }

    public float rollDanger(RandomSource random) {
        if (!randomStation) {
            return missionDanger;
        }

        float spread = 0.25F;
        return Mth.clamp(missionDanger + (random.nextFloat() * spread * 2.0F) - spread, 0.0F, 1.0F);
    }
}
