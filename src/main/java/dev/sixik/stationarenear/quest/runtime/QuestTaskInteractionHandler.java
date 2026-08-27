package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.registry.QuestItems;
import dev.sixik.stationarenear.quest.registry.QuestTags;
import dev.sixik.stationarenear.quest.registry.StationQuests;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.ship.event.PressureTightDoorRepairedEvent;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.trigger.StationTriggerEvent;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import java.util.UUID;

public final class QuestTaskInteractionHandler {

    private static final String KEY_DONE_POSITIONS = "donePositions";

    private QuestTaskInteractionHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(QuestTags.TRASH_BLOCKS)) {
            return;
        }
        stationPieceAt(level, event.getPos()).ifPresent(context -> incrementCleanup(level, context, event.getPos()));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Optional<StationPieceContext> context = stationPieceAt(level, event.getPos());
        if (context.isEmpty()) {
            return;
        }

        BlockState state = event.getPlacedBlock();
        incrementPlaceItem(level, context.get(), event.getPos(), state);
        if (state.is(QuestTags.REPAIRABLE_BLOCKS)) {
            increment(level, context.get().station().id(), StationQuests.REPAIR_BLOCKS, event.getPos());
        }
        if (state.is(QuestTags.BUILD_TARGET_BLOCKS)) {
            increment(level, context.get().station().id(), StationQuests.BUILD_SHEATHING, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onTrigger(StationTriggerEvent event) {
        if (StationStructureTriggerType.from(event.getZone().type()) != StationStructureTriggerType.QUEST) {
            return;
        }
        progressZoneQuest(event.getLevel(), event.getStation().id(), event.getZone(), event.getPlayer().getMainHandItem());
    }

    private static void progressZoneQuest(ServerLevel level, UUID stationId, PlacedTriggerZone zone, ItemStack stack) {
        String questId = zone.data().contains("quest", Tag.TAG_STRING) ? zone.data().getString("quest") : zone.id();
        if (questId == null || questId.isBlank()) {
            return;
        }
        if (stack.is(QuestItems.PUTTY_BUCKET.get()) && QuestApi.isActive(level, stationId, StationQuests.REPAIR_BLOCKS)) {
            if (zoneMatches(level, stationId, StationQuests.REPAIR_BLOCKS, zone)) {
                increment(level, stationId, StationQuests.REPAIR_BLOCKS, zone.min());
            }
        }
        if (stack.is(QuestItems.STATION_SHEATHING.get()) && QuestApi.isActive(level, stationId, StationQuests.BUILD_SHEATHING)) {
            if (zoneMatches(level, stationId, StationQuests.BUILD_SHEATHING, zone)) {
                increment(level, stationId, StationQuests.BUILD_SHEATHING, zone.min());
            }
        }
    }

    private static boolean zoneMatches(ServerLevel level, UUID stationId, String questId, PlacedTriggerZone zone) {
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .flatMap(state -> state.objective(questId))
                .filter(objective -> !objective.completed())
                .map(objective -> {
                    if (!objective.targetTriggerId().isBlank()) {
                        return objective.targetTriggerId().equals(zone.id());
                    }
                    return !zone.data().contains("quest", Tag.TAG_STRING) || questId.equals(zone.data().getString("quest"));
                })
                .orElse(false);
    }

    private static boolean itemMatches(PlacedTriggerZone zone, ItemStack stack) {
        String itemId = "";
        if (zone.data().contains("item", Tag.TAG_STRING)) {
            itemId = zone.data().getString("item");
        } else if (zone.data().contains("requiredItem", Tag.TAG_STRING)) {
            itemId = zone.data().getString("requiredItem");
        }
        if (itemId.isBlank()) {
            return true;
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId.contains(":") ? itemId : "stationarenear:" + itemId);
        return location != null && ForgeRegistries.ITEMS.getValue(location) == stack.getItem();
    }

    private static boolean incrementPlaceItem(ServerLevel level, StationPieceContext context, BlockPos pos, BlockState placedState) {
        Optional<QuestObjectiveState> objective = QuestSavedData.get(level)
                .stationIfPresent(context.station().id())
                .flatMap(state -> state.objective(StationQuests.PLACE_ITEM));
        if (objective.isEmpty() || objective.get().completed() || alreadyDone(objective.get(), pos)) {
            return false;
        }
        if (!placeTargetMatches(context.piece(), objective.get(), pos, placedState)) {
            return false;
        }

        int current = objective.get().progress().getInt("value");
        int next = Math.min(objective.get().targetCount(), current + 1);
        if (!QuestApi.progress(level, context.station().id(), StationQuests.PLACE_ITEM, next)) {
            return false;
        }
        markDone(level, context.station().id(), StationQuests.PLACE_ITEM, pos);
        if (next >= objective.get().targetCount()) {
            QuestApi.complete(level, context.station().id(), StationQuests.PLACE_ITEM);
        }
        return true;
    }

    private static boolean placeTargetMatches(PlacedStationPiece piece, QuestObjectiveState objective, BlockPos pos, BlockState placedState) {
        return piece.triggerZones().stream()
                .filter(zone -> StationStructureTriggerType.from(zone.type()) == StationStructureTriggerType.QUEST_PLACE)
                .filter(zone -> objective.targetTriggerId().isBlank() || objective.targetTriggerId().equals(zone.id()))
                .anyMatch(zone -> placeZoneContains(zone, pos) && placedBlockMatches(zone, placedState));
    }

    private static boolean placeZoneContains(PlacedTriggerZone zone, BlockPos pos) {
        int radius = Math.max(0, Math.min(16, zone.data().contains("radius") ? zone.data().getInt("radius") : 1));
        int heightRadius = Math.max(0, Math.min(16, zone.data().contains("heightRadius") ? zone.data().getInt("heightRadius") : 1));
        int minX = zone.min().getX() == zone.max().getX() ? zone.min().getX() - radius : zone.min().getX();
        int maxX = zone.min().getX() == zone.max().getX() ? zone.max().getX() + radius : zone.max().getX();
        int minY = zone.min().getY() == zone.max().getY() ? zone.min().getY() - heightRadius : zone.min().getY();
        int maxY = zone.min().getY() == zone.max().getY() ? zone.max().getY() + heightRadius : zone.max().getY();
        int minZ = zone.min().getZ() == zone.max().getZ() ? zone.min().getZ() - radius : zone.min().getZ();
        int maxZ = zone.min().getZ() == zone.max().getZ() ? zone.max().getZ() + radius : zone.max().getZ();
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static boolean placedBlockMatches(PlacedTriggerZone zone, BlockState state) {
        String required = requiredPlacedId(zone);
        if (required.isBlank()) {
            return true;
        }
        ResourceLocation location = ResourceLocation.tryParse(required.contains(":") ? required : "stationarenear:" + required);
        if (location == null) {
            return false;
        }
        Block block = state.getBlock();
        return location.equals(ForgeRegistries.BLOCKS.getKey(block)) || location.equals(ForgeRegistries.ITEMS.getKey(block.asItem()));
    }

    private static String requiredPlacedId(PlacedTriggerZone zone) {
        if (zone.data().contains("block", Tag.TAG_STRING)) {
            return zone.data().getString("block");
        }
        if (zone.data().contains("requiredBlock", Tag.TAG_STRING)) {
            return zone.data().getString("requiredBlock");
        }
        if (zone.data().contains("item", Tag.TAG_STRING)) {
            return zone.data().getString("item");
        }
        if (zone.data().contains("requiredItem", Tag.TAG_STRING)) {
            return zone.data().getString("requiredItem");
        }
        return "";
    }

    @SubscribeEvent
    public static void onPressureDoorRepaired(PressureTightDoorRepairedEvent event) {
        stationPieceAt(event.getLevel(), event.getMasterPos())
                .ifPresent(context -> incrementRepairDoor(event.getLevel(), context, event.getMasterPos()));
    }

    private static boolean increment(ServerLevel level, UUID stationId, String questId, BlockPos pos) {
        Optional<QuestObjectiveState> objective = QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .flatMap(state -> state.objective(questId));
        if (objective.isEmpty() || objective.get().completed() || alreadyDone(objective.get(), pos)) {
            return false;
        }

        int current = objective.get().progress().getInt("value");
        int next = Math.min(objective.get().targetCount(), current + 1);
        if (!QuestApi.progress(level, stationId, questId, next)) {
            return false;
        }
        markDone(level, stationId, questId, pos);
        if (next >= objective.get().targetCount()) {
            QuestApi.complete(level, stationId, questId);
        }
        return true;
    }

    private static boolean incrementCleanup(ServerLevel level, StationPieceContext context, BlockPos pos) {
        Optional<QuestObjectiveState> objective = QuestSavedData.get(level)
                .stationIfPresent(context.station().id())
                .flatMap(state -> state.objective(StationQuests.CLEAR_TRASH));
        if (objective.isEmpty() || objective.get().completed() || alreadyDone(objective.get(), pos)) {
            return false;
        }
        if (!cleanupPieceMatches(context.piece(), objective.get())) {
            return false;
        }

        int current = objective.get().progress().getInt("value");
        int next = Math.min(objective.get().targetCount(), current + 1);
        if (!QuestApi.progress(level, context.station().id(), StationQuests.CLEAR_TRASH, next)) {
            return false;
        }
        markDone(level, context.station().id(), StationQuests.CLEAR_TRASH, pos);
        if (next >= objective.get().targetCount()) {
            QuestApi.complete(level, context.station().id(), StationQuests.CLEAR_TRASH);
        }
        return true;
    }

    private static boolean incrementRepairDoor(ServerLevel level, StationPieceContext context, BlockPos pos) {
        Optional<QuestObjectiveState> objective = QuestSavedData.get(level)
                .stationIfPresent(context.station().id())
                .flatMap(state -> state.objective(StationQuests.REPAIR_DOORS));
        if (objective.isEmpty() || objective.get().completed() || alreadyDone(objective.get(), pos)) {
            return false;
        }
        if (!triggerZoneMatches(context.piece(), objective.get(), pos)) {
            return false;
        }

        int current = objective.get().progress().getInt("value");
        int next = Math.min(objective.get().targetCount(), current + 1);
        if (!QuestApi.progress(level, context.station().id(), StationQuests.REPAIR_DOORS, next)) {
            return false;
        }
        markDone(level, context.station().id(), StationQuests.REPAIR_DOORS, pos);
        if (next >= objective.get().targetCount()) {
            QuestApi.complete(level, context.station().id(), StationQuests.REPAIR_DOORS);
        }
        return true;
    }

    private static boolean cleanupPieceMatches(PlacedStationPiece piece, QuestObjectiveState objective) {
        if (objective.targetTriggerId().isBlank()) {
            return true;
        }
        return piece.triggerZones().stream().anyMatch(zone -> objective.targetTriggerId().equals(zone.id()));
    }

    private static boolean triggerZoneMatches(PlacedStationPiece piece, QuestObjectiveState objective, BlockPos pos) {
        if (objective.targetTriggerId().isBlank()) {
            return true;
        }
        return piece.triggerZones().stream()
                .filter(zone -> objective.targetTriggerId().equals(zone.id()))
                .anyMatch(zone -> contains(zone, pos));
    }

    private static boolean contains(PlacedTriggerZone zone, BlockPos pos) {
        return pos.getX() >= zone.min().getX() && pos.getX() <= zone.max().getX()
                && pos.getY() >= zone.min().getY() && pos.getY() <= zone.max().getY()
                && pos.getZ() >= zone.min().getZ() && pos.getZ() <= zone.max().getZ();
    }

    private static boolean alreadyDone(QuestObjectiveState objective, BlockPos pos) {
        return objective.progress().getList(KEY_DONE_POSITIONS, Tag.TAG_LONG).contains(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
    }

    private static void markDone(ServerLevel level, UUID stationId, String questId, BlockPos pos) {
        QuestSavedData data = QuestSavedData.get(level);
        QuestStationState station = data.stationIfPresent(stationId).orElse(null);
        if (station == null) {
            return;
        }
        station.objective(questId).ifPresent(objective -> {
            net.minecraft.nbt.CompoundTag progress = objective.progress();
            net.minecraft.nbt.ListTag positions = progress.getList(KEY_DONE_POSITIONS, Tag.TAG_LONG);
            positions.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
            progress.put(KEY_DONE_POSITIONS, positions);
            station.put(objective.withProgress(progress));
            data.station(station);
        });
    }

    private static Optional<StationInstance> stationAt(ServerLevel level, BlockPos pos) {
        return StationSavedData.get(level).stations().stream()
                .filter(station -> station.pieces().stream().anyMatch(piece -> contains(piece, pos)))
                .findFirst();
    }

    private static Optional<StationPieceContext> stationPieceAt(ServerLevel level, BlockPos pos) {
        for (StationInstance station : StationSavedData.get(level).stations()) {
            for (PlacedStationPiece piece : station.pieces()) {
                if (contains(piece, pos)) {
                    return Optional.of(new StationPieceContext(station, piece));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean contains(PlacedStationPiece piece, BlockPos pos) {
        return pos.getX() >= piece.selectionBounds().minX() && pos.getX() <= piece.selectionBounds().maxX()
                && pos.getY() >= piece.selectionBounds().minY() && pos.getY() <= piece.selectionBounds().maxY()
                && pos.getZ() >= piece.selectionBounds().minZ() && pos.getZ() <= piece.selectionBounds().maxZ();
    }

    private record StationPieceContext(StationInstance station, PlacedStationPiece piece) {
    }
}
