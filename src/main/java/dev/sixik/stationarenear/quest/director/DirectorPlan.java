package dev.sixik.stationarenear.quest.director;

import dev.sixik.stationarenear.quest.config.director.DirectorConfigManager;
import dev.sixik.stationarenear.quest.config.director.DirectorProfileConfig;
import dev.sixik.stationarenear.quest.data.QuestLocalization;
import dev.sixik.stationarenear.quest.data.QuestTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DirectorPlan(
        DirectorProfileConfig profile,
        List<QuestTaskPlan> questTasks,
        List<StationSpawnPlan> stationSpawns,
        DirectorCredits credits
) {

    public DirectorPlan {
        questTasks = questTasks == null ? List.of() : List.copyOf(questTasks);
        stationSpawns = stationSpawns == null ? List.of() : List.copyOf(stationSpawns);
    }

    public String missionId() {
        return profile.id();
    }

    public String displayName() {
        return profile.name();
    }

    public List<QuestTask> tasks() {
        return questTasks.stream().map(QuestTaskPlan::task).toList();
    }

    public Map<String, QuestLocalization> localizations() {
        Map<String, QuestLocalization> result = new LinkedHashMap<>();
        for (QuestTaskPlan task : questTasks) {
            result.put(task.id(), task.offer().localization());
        }
        return Map.copyOf(result);
    }

    public Map<ResourceLocation, Integer> requiredPieces() {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        for (QuestTaskPlan task : questTasks) {
            task.offer().requiredPieces().forEach((key, value) -> result.merge(key, value, Integer::sum));
        }
        return Map.copyOf(result);
    }

    public Map<String, Integer> requiredPieceTags() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (QuestTaskPlan task : questTasks) {
            task.offer().requiredPieceTags().forEach((key, value) -> result.merge(key, value, Integer::sum));
        }
        return Map.copyOf(result);
    }

    public Map<String, Integer> questElementSpawnSkips() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (QuestTaskPlan task : questTasks) {
            task.offer().questElementSpawnSkips().forEach((key, value) -> result.merge(key, value, Integer::sum));
        }
        return Map.copyOf(result);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("profileId", profile.id());
        tag.putString("displayName", profile.name());
        tag.putLong("durationSeconds", profile.durationSeconds());
        tag.putString("stationConfig", profile.stationConfig());
        tag.putInt("playMinutes", credits.playMinutes());
        tag.putInt("completedMissionCount", credits.completedMissionCount());
        tag.putFloat("baseDanger", profile.baseDanger());
        tag.putFloat("dangerGrowthMultiplier", profile.dangerGrowthMultiplier());
        tag.putFloat("directorDanger", profile.dangerForCompletedMissions(credits.completedMissionCount(), 1.0F));
        tag.putInt("questBudget", credits.questBudget());
        tag.putInt("questSpent", credits.questSpent());
        tag.putInt("questLeftover", credits.questLeftover());
        tag.putInt("stationBudget", credits.stationBudget());
        tag.putInt("stationSpent", credits.stationSpent());
        tag.putInt("stationLeftover", credits.stationLeftover());
        tag.put("requiredPieces", DirectorConfigManager.saveResourceCountMap(requiredPieces()));
        tag.put("requiredPieceTags", DirectorConfigManager.saveStringCountMap(requiredPieceTags()));
        tag.put("questElementSpawnSkips", DirectorConfigManager.saveStringCountMap(questElementSpawnSkips()));
        ListTag questList = new ListTag();
        for (QuestTaskPlan task : questTasks) {
            questList.add(task.save());
        }
        tag.put("questTasks", questList);
        ListTag stationList = new ListTag();
        for (StationSpawnPlan spawn : stationSpawns) {
            stationList.add(spawn.save());
        }
        tag.put("stationOffers", stationList);
        return tag;
    }

    public static boolean hasStationOffers(CompoundTag tag) {
        return tag != null && tag.contains("stationOffers", Tag.TAG_LIST) && !tag.getList("stationOffers", Tag.TAG_COMPOUND).isEmpty();
    }
}
