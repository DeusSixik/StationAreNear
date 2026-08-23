package dev.sixik.stationarenear.structures.generation;

import dev.sixik.stationarenear.structures.data.StationInstance;

import java.util.Optional;

public record StationGenerationResult(
        boolean success,
        Optional<StationInstance> station,
        String message
) {

    public static StationGenerationResult success(StationInstance station) {
        return new StationGenerationResult(true, Optional.of(station), "Generated station " + station.id());
    }

    public static StationGenerationResult failure(String message) {
        return new StationGenerationResult(false, Optional.empty(), message);
    }
}
