package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestDefinition;
import dev.sixik.stationarenear.quest.data.QuestObjectiveKind;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestPlacementZoneHint;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.event.QuestCompletedEvent;
import dev.sixik.stationarenear.quest.event.QuestMissionFailedEvent;
import dev.sixik.stationarenear.quest.event.QuestProgressChangedEvent;
import dev.sixik.stationarenear.quest.event.QuestStartedEvent;
import dev.sixik.stationarenear.quest.event.QuestTaskCompletedEvent;
import dev.sixik.stationarenear.quest.event.StationQuestsCompletedEvent;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.quest.registry.StationQuests;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class QuestPlacementZoneManager {

    private QuestPlacementZoneManager() {
    }

    public static void sync(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Optional<UUID> stationId = QuestApi.currentStationId(level);
        if (stationId.isEmpty()) {
            QuestNetwork.syncPlacementZones(player, List.of());
            return;
        }
        Optional<StationInstance> stationOpt = StationSavedData.get(level).station(stationId.get());
        Optional<QuestStationState> questStateOpt = QuestSavedData.get(level).stationIfPresent(stationId.get());
        if (stationOpt.isEmpty() || questStateOpt.isEmpty()) {
            QuestNetwork.syncPlacementZones(player, List.of());
            return;
        }
        List<QuestPlacementZoneHint> hints = resolveHints(stationOpt.get(), questStateOpt.get());
        QuestNetwork.syncPlacementZones(player, hints);
    }

    public static void sync(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            sync(player);
        }
    }

    public static List<QuestPlacementZoneHint> resolveHints(StationInstance station, QuestStationState questState) {
        List<QuestPlacementZoneHint> hints = new ArrayList<>();
        for (QuestObjectiveState objective : questState.objectives()) {
            if (objective.completed() || !isPlaceItemObjective(objective)) {
                continue;
            }

            Set<String> targetTriggerIds = targetTriggerIds(objective);
            String requiredItem = requiredPlacedId(objective);
            PlacementVisual visual = resolveVisual(objective.id(), requiredItem);

            for (PlacedStationPiece piece : station.pieces()) {
                for (PlacedTriggerZone zone : piece.triggerZones()) {
                    if (!isPlaceTargetTrigger(zone)) {
                        continue;
                    }
                    if (!targetTriggerIds.isEmpty()) {
                        if (!targetTriggerIds.contains(zone.id())) {
                            continue;
                        }
                    } else if (!zoneMatchesObjective(objective, zone)) {
                        continue;
                    }

                    int radius = Math.max(0, Math.min(16, zone.data().contains("radius") ? zone.data().getInt("radius") : 1));
                    int heightRadius = Math.max(0, Math.min(16, zone.data().contains("heightRadius") ? zone.data().getInt("heightRadius") : 1));
                    int minX = zone.min().getX() == zone.max().getX() ? zone.min().getX() - radius : zone.min().getX();
                    int maxX = zone.min().getX() == zone.max().getX() ? zone.max().getX() + radius : zone.max().getX();
                    int minY = zone.min().getY() == zone.max().getY() ? zone.min().getY() - heightRadius : zone.min().getY();
                    int maxY = zone.min().getY() == zone.max().getY() ? zone.max().getY() + heightRadius : zone.max().getY();
                    int minZ = zone.min().getZ() == zone.max().getZ() ? zone.min().getZ() - radius : zone.min().getZ();
                    int maxZ = zone.min().getZ() == zone.max().getZ() ? zone.max().getZ() + radius : zone.max().getZ();

                    hints.add(new QuestPlacementZoneHint(
                            objective.id(),
                            visual.label(),
                            new BlockPos(minX, minY, minZ),
                            new BlockPos(maxX, maxY, maxZ),
                            requiredItem,
                            visual.red(),
                            visual.green(),
                            visual.blue(),
                            visual.textColor()
                    ));
                }
            }
        }
        return hints;
    }

    private static boolean isPlaceItemObjective(QuestObjectiveState objective) {
        return QuestApi.definition(objective.id())
                .map(QuestDefinition::kind)
                .map(kind -> kind == QuestObjectiveKind.PLACE_ITEM)
                .orElse(StationQuests.PLACE_ITEM.equals(objective.id())
                        || StationQuests.PLACE_FRIDGE.equals(objective.id())
                        || StationQuests.PLACE_MICROWAVE.equals(objective.id())
                        || StationQuests.PLACE_KITCHEN_SINK.equals(objective.id()));
    }

    private static boolean isPlaceTargetTrigger(PlacedTriggerZone zone) {
        StationStructureTriggerType type = StationStructureTriggerType.from(zone.type());
        return type == StationStructureTriggerType.QUEST_PLACE || type == StationStructureTriggerType.QUEST_OBJECT_PLACER;
    }

    private static boolean zoneMatchesObjective(QuestObjectiveState objective, PlacedTriggerZone zone) {
        String requiredItem = requiredPlacedId(objective);
        String zoneItem = requiredPlacedId(zone.data());
        if (!zoneItem.isBlank() && !requiredItem.isBlank()) {
            return zoneItem.equalsIgnoreCase(requiredItem);
        }
        if (zone.data().contains("quest", Tag.TAG_STRING)) {
            return objective.id().equalsIgnoreCase(zone.data().getString("quest"));
        }
        return true;
    }

    private static Set<String> targetTriggerIds(QuestObjectiveState objective) {
        Set<String> ids = new HashSet<>();
        CompoundTag progress = objective.progress();
        if (progress.contains("targetTriggerId", Tag.TAG_STRING)) {
            String singleId = progress.getString("targetTriggerId");
            if (!singleId.isBlank()) {
                ids.add(singleId);
            }
        }
        if (progress.contains("targetTriggerIds", Tag.TAG_LIST)) {
            ListTag list = progress.getList("targetTriggerIds", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String id = list.getString(i);
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static String requiredPlacedId(QuestObjectiveState objective) {
        String progressRequired = requiredPlacedId(objective.progress());
        if (!progressRequired.isBlank()) {
            return progressRequired;
        }
        if (StationQuests.PLACE_FRIDGE.equals(objective.id())) {
            return registryId(QuestBlocks.FRIDGE.get());
        }
        if (StationQuests.PLACE_MICROWAVE.equals(objective.id())) {
            return registryId(QuestBlocks.MICROWAVE.get());
        }
        if (StationQuests.PLACE_KITCHEN_SINK.equals(objective.id())) {
            return registryId(QuestBlocks.KITCHEN_SINK.get());
        }
        return "";
    }

    private static String requiredPlacedId(CompoundTag data) {
        if (data.contains("block", Tag.TAG_STRING)) {
            return data.getString("block");
        }
        if (data.contains("requiredBlock", Tag.TAG_STRING)) {
            return data.getString("requiredBlock");
        }
        if (data.contains("item", Tag.TAG_STRING)) {
            return data.getString("item");
        }
        if (data.contains("requiredItem", Tag.TAG_STRING)) {
            return data.getString("requiredItem");
        }
        return "";
    }

    private static String registryId(net.minecraft.world.level.block.Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "" : key.toString();
    }

    private static PlacementVisual resolveVisual(String questId, String requiredItem) {
        String lowerQuest = questId == null ? "" : questId.toLowerCase(Locale.ROOT);
        String lowerItem = requiredItem == null ? "" : requiredItem.toLowerCase(Locale.ROOT);

        if (lowerQuest.contains("fridge") || lowerItem.contains("fridge")) {
            return new PlacementVisual("Холодильник", 0.15F, 0.85F, 1.0F, 0xFF55FFFF);
        }
        if (lowerQuest.contains("microwave") || lowerItem.contains("microwave")) {
            return new PlacementVisual("Микроволновка", 1.0F, 0.70F, 0.15F, 0xFFFFAA33);
        }
        if (lowerQuest.contains("sink") || lowerItem.contains("sink") || lowerQuest.contains("kitchen_sink") || lowerItem.contains("kitchen_sink")) {
            return new PlacementVisual("Раковина", 0.25F, 0.60F, 1.0F, 0xFF55AAFF);
        }
        return new PlacementVisual("Место установки", 0.20F, 1.0F, 0.40F, 0xFF55FF55);
    }

    @SubscribeEvent
    public static void onQuestStarted(QuestStartedEvent event) {
        sync(event.getLevel());
    }

    @SubscribeEvent
    public static void onQuestProgressChanged(QuestProgressChangedEvent event) {
        sync(event.getLevel());
    }

    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        sync(event.getLevel());
    }

    @SubscribeEvent
    public static void onQuestTaskCompleted(QuestTaskCompletedEvent event) {
        sync(event.getLevel());
    }

    @SubscribeEvent
    public static void onStationQuestsCompleted(StationQuestsCompletedEvent event) {
        sync(event.getLevel());
    }

    @SubscribeEvent
    public static void onQuestMissionFailed(QuestMissionFailedEvent event) {
        sync(event.getLevel());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    private record PlacementVisual(String label, float red, float green, float blue, int textColor) {
    }
}
