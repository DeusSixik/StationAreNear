package dev.sixik.stationarenear.quest.director;

import dev.sixik.stationarenear.quest.config.director.StationOfferConfig;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StationDirector {

    private StationDirector() {
    }

    public static Result createPlan(List<StationOfferConfig> offers, int budget, List<StationSpawnPlan> requiredSpawns, RandomSource random) {
        List<StationSpawnPlan> result = new ArrayList<>(requiredSpawns);
        Map<String, Integer> picked = new HashMap<>();
        for (StationSpawnPlan spawn : requiredSpawns) {
            picked.merge(spawn.offer().id(), spawn.count(), Integer::sum);
        }
        int remaining = Math.max(0, budget);
        for (int guard = 0; guard < 128; guard++) {
            int budgetLeft = remaining;
            List<StationOfferConfig> available = offers.stream()
                    .filter(offer -> offer.cost() <= budgetLeft)
                    .filter(offer -> picked.getOrDefault(offer.id(), 0) < offer.maxPerStation())
                    .toList();
            if (available.isEmpty()) {
                break;
            }
            StationOfferConfig offer = weighted(available, random);
            int pickedCount = picked.getOrDefault(offer.id(), 0);
            int limitLeft = offer.maxPerStation() - pickedCount;
            int affordable = offer.cost() <= 0 ? limitLeft : Math.max(1, remaining / offer.cost());
            int count = Math.min(Math.min(offer.rollCount(random), affordable), limitLeft);
            if (count <= 0) {
                break;
            }
            int cost = offer.cost() * count;
            result.add(new StationSpawnPlan(offer, count, cost));
            picked.merge(offer.id(), count, Integer::sum);
            remaining -= cost;
        }
        return new Result(List.copyOf(result), Math.max(0, budget - remaining), remaining);
    }

    private static StationOfferConfig weighted(List<StationOfferConfig> offers, RandomSource random) {
        int total = offers.stream().mapToInt(StationOfferConfig::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        for (StationOfferConfig offer : offers) {
            roll -= offer.weight();
            if (roll < 0) {
                return offer;
            }
        }
        return offers.get(0);
    }

    public record Result(List<StationSpawnPlan> spawns, int spent, int leftover) {
    }
}
