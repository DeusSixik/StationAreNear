package dev.sixik.stationarenear.quest.director;

import dev.sixik.stationarenear.quest.config.director.DirectorConfigManager;
import dev.sixik.stationarenear.quest.config.director.DirectorProfileConfig;
import dev.sixik.stationarenear.quest.config.director.QuestOfferConfig;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuestDirector {

    private QuestDirector() {
    }

    public static DirectorPlan createPlan(DirectorProfileConfig profile, DirectorContext context) {
        RandomSource random = RandomSource.create(context.seed() ^ profile.id().hashCode());
        int questBudget = context.questBudget(profile.questBudgetForPlayMinutes(context.playMinutes()));
        int targetObjectives = profile.rollObjectiveCount(random);
        List<QuestOfferConfig> offers = DirectorConfigManager.questOffers(profile.questOfferPool()).stream()
                .filter(offer -> offer.requiredQuests().stream().allMatch(context.completedQuestIds()::contains))
                .toList();
        List<QuestTaskPlan> tasks = buyQuestTasks(offers, questBudget, targetObjectives, random);
        int questSpent = tasks.stream().mapToInt(QuestTaskPlan::totalCost).sum();
        int questLeft = Math.max(0, questBudget - questSpent);
        int stationBudget = context.stationBudget(profile.stationBudgetForPlayMinutes(context.playMinutes())) + (profile.leftoverQuestCreditsToStation() ? questLeft : 0);
        List<dev.sixik.stationarenear.quest.config.director.StationOfferConfig> allStationOffers = DirectorConfigManager.stationOffers(profile.stationOfferPool());
        List<StationSpawnPlan> requiredSpawns = new ArrayList<>();
        for (QuestTaskPlan task : tasks) {
            dev.sixik.stationarenear.quest.config.director.StationOfferType requiredType = null;
            if (task.offer().kind() == dev.sixik.stationarenear.quest.data.QuestObjectiveKind.REPAIR_DOOR) {
                requiredType = dev.sixik.stationarenear.quest.config.director.StationOfferType.BROKEN_DOOR;
            } else if (task.offer().kind() == dev.sixik.stationarenear.quest.data.QuestObjectiveKind.REPAIR_ELECTRIC_PANEL) {
                requiredType = dev.sixik.stationarenear.quest.config.director.StationOfferType.ENERGY_FAILURE;
            } else if (task.offer().kind() == dev.sixik.stationarenear.quest.data.QuestObjectiveKind.REPAIR_GRAVITATION_PANEL) {
                requiredType = dev.sixik.stationarenear.quest.config.director.StationOfferType.GRAVITATION_FAILURE;
            } else if (task.offer().kind() == dev.sixik.stationarenear.quest.data.QuestObjectiveKind.REPAIR_OXYGEN_PANEL) {
                requiredType = dev.sixik.stationarenear.quest.config.director.StationOfferType.OXYGEN_FAILURE;
            }
            if (requiredType != null) {
                dev.sixik.stationarenear.quest.config.director.StationOfferType finalType = requiredType;
                allStationOffers.stream()
                        .filter(o -> o.type() == finalType)
                        .findFirst()
                        .ifPresent(offer -> requiredSpawns.add(new StationSpawnPlan(offer, task.count(), 0)));
            }
        }
        StationDirector.Result stationResult = StationDirector.createPlan(allStationOffers, stationBudget, requiredSpawns, random);
        return new DirectorPlan(profile, tasks, stationResult.spawns(), new DirectorCredits(context.playMinutes(), context.completedMissionCount(), questBudget, questSpent, questLeft, stationBudget, stationResult.spent(), stationResult.leftover()));
    }

    private static List<QuestTaskPlan> buyQuestTasks(List<QuestOfferConfig> offers, int budget, int targetObjectives, RandomSource random) {
        List<QuestTaskPlan> result = new ArrayList<>();
        Map<String, Integer> pickedById = new HashMap<>();
        Set<String> exclusiveGroups = new HashSet<>();
        int remaining = budget;
        for (int guard = 0; guard < Math.max(1, targetObjectives * 8) && result.size() < targetObjectives; guard++) {
            int budgetLeft = remaining;
            List<QuestOfferConfig> available = offers.stream()
                    .filter(offer -> offer.cost() <= budgetLeft)
                    .filter(offer -> pickedById.getOrDefault(offer.id(), 0) < offer.maxPerMission())
                    .filter(offer -> offer.exclusiveGroup().isBlank() || !exclusiveGroups.contains(offer.exclusiveGroup()))
                    .toList();
            if (available.isEmpty()) {
                break;
            }
            QuestOfferConfig offer = weighted(available, random);
            result.add(new QuestTaskPlan(offer, offer.rollCount(random), offer.cost()));
            pickedById.merge(offer.id(), 1, Integer::sum);
            if (!offer.exclusiveGroup().isBlank()) {
                exclusiveGroups.add(offer.exclusiveGroup());
            }
            remaining -= offer.cost();
        }
        if (result.isEmpty()) {
            offers.stream().filter(offer -> offer.cost() <= budget).min(java.util.Comparator.comparingInt(QuestOfferConfig::cost)).ifPresent(offer -> result.add(new QuestTaskPlan(offer, offer.rollCount(random), offer.cost())));
        }
        return List.copyOf(result);
    }

    private static QuestOfferConfig weighted(List<QuestOfferConfig> offers, RandomSource random) {
        int total = offers.stream().mapToInt(QuestOfferConfig::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        for (QuestOfferConfig offer : offers) {
            roll -= offer.weight();
            if (roll < 0) {
                return offer;
            }
        }
        return offers.get(0);
    }
}
