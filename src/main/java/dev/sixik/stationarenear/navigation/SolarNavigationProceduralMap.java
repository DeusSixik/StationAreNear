package dev.sixik.stationarenear.navigation;

import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class SolarNavigationProceduralMap {

    private SolarNavigationProceduralMap() {
    }

    public static List<SolarNavigationStationInfo> nearbyStations(long seed, SolarNavigationShipState shipState, Collection<SolarNavigationQuestMarker> questMarkers, float radius, int limit) {
        int sectorSize = SolarNavigationConfig.SECTOR_SIZE.get();
        int sectorRadius = Math.max(1, (int) Math.ceil(radius / sectorSize) + 1);
        int centerSectorX = floorDiv(shipState.shipX(), sectorSize);
        int centerSectorY = floorDiv(shipState.shipY(), sectorSize);
        float radiusSq = radius * radius;
        List<SolarNavigationStationInfo> stations = new ArrayList<>();

        for (int sectorX = centerSectorX - sectorRadius; sectorX <= centerSectorX + sectorRadius; sectorX++) {
            for (int sectorY = centerSectorY - sectorRadius; sectorY <= centerSectorY + sectorRadius; sectorY++) {
                randomStation(seed, shipState, sectorX, sectorY, sectorSize, radiusSq).ifPresent(stations::add);
            }
        }

        for (SolarNavigationQuestMarker marker : questMarkers) {
            float distance = distance(shipState.shipX(), shipState.shipY(), marker.x(), marker.y());
            if (distance <= radius + marker.radius()) {
                stations.add(new SolarNavigationStationInfo(marker.name(), StationCodeGenerator.code(marker.seed(), marker.x(), marker.y()), marker.x(), marker.y(), marker.radius(), true, marker.seed(), marker.color(), distance));
            }
        }

        stations.sort(Comparator.comparingDouble(SolarNavigationStationInfo::distance));
        if (limit > 0 && stations.size() > limit) {
            return List.copyOf(stations.subList(0, limit));
        }
        return List.copyOf(stations);
    }

    public static Optional<SolarNavigationStationInfo> randomStation(long seed, SolarNavigationShipState shipState, int sectorX, int sectorY, int sectorSize, float maxDistanceSq) {
        Random random = new Random(sectorSeed(seed, sectorX, sectorY, 0xD06E_57A7_10DEL));
        if (random.nextDouble() > SolarNavigationConfig.RANDOM_STATION_CHANCE.get()) {
            return Optional.empty();
        }

        float minX = sectorX * (float) sectorSize;
        float minY = sectorY * (float) sectorSize;
        long stationSeed = random.nextLong() ^ seed ^ sectorSeed(seed, sectorX, sectorY, 0x57A7_10DEL);
        float stationRadius = randomRange(random, SolarNavigationConfig.STATION_MIN_RADIUS.get().floatValue(), SolarNavigationConfig.STATION_MAX_RADIUS.get().floatValue());
        float x = minX + randomRange(random, sectorSize * 0.18F, sectorSize * 0.82F);
        float y = minY + randomRange(random, sectorSize * 0.18F, sectorSize * 0.82F);
        float distance = distance(shipState.shipX(), shipState.shipY(), x, y);
        if (distance * distance > maxDistanceSq) {
            return Optional.empty();
        }
        return Optional.of(new SolarNavigationStationInfo(randomStationName(random), StationCodeGenerator.code(stationSeed, x, y), x, y, stationRadius, false, stationSeed, 0xFF8AE6FF, distance));
    }

    public static String randomStationName(Random random) {
        String[] prefixes = {"Kappa", "Vega", "Astra", "Orion", "Helio", "Nova", "Rhea", "Ceres", "Iris", "Taurus"};
        String[] suffixes = {"Gate", "Relay", "Port", "Array", "Dock", "Spire", "Hold", "Foundry", "Bastion", "Crossing"};
        return prefixes[random.nextInt(prefixes.length)] + " " + suffixes[random.nextInt(suffixes.length)];
    }

    public static long sectorSeed(long seed, int sectorX, int sectorY, long salt) {
        long value = seed ^ salt;
        value ^= (long) sectorX * 0x9E37_79B9_7F4A_7C15L;
        value ^= (long) sectorY * 0xC2B2_AE3D_27D4_EB4FL;
        value ^= value >>> 33;
        value *= 0xFF51_AFD7_ED55_8CCDL;
        value ^= value >>> 33;
        value *= 0xC4CE_B9FE_1A85_EC53L;
        return value ^ (value >>> 33);
    }

    private static int floorDiv(float value, int divisor) {
        return (int) Math.floor(value / divisor);
    }

    private static float randomRange(Random random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private static float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
