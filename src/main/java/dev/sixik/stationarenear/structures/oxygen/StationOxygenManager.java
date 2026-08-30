package dev.sixik.stationarenear.structures.oxygen;

import dev.sixik.stationarenear.quest.block.WallMountedPanelBlock;
import dev.sixik.stationarenear.quest.config.director.StationOfferType;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;

import java.util.Locale;
import java.util.Optional;

public final class StationOxygenManager {

    private static final double ZONE_RADIUS_SQR = 144.0D;

    private StationOxygenManager() {
    }

    public static void onStationGenerated(ServerLevel level, StationInstance station) {
        StationOxygenSavedData data = StationOxygenSavedData.get(level);
        StationOxygenState state = data.getOrCreate(station.id());

        boolean brokenFound = hasOxygenFailureOffer(station);

        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                if ((zone.data().contains("brokenOxygen") && zone.data().getBoolean("brokenOxygen"))
                        || (zone.data().contains("broken") && zone.data().getBoolean("broken"))
                        || hasTriggerTag(zone, "broken_oxygen")) {
                    brokenFound = true;
                }
            }
            var bounds = piece.selectionBounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        BlockPos checkPos = new BlockPos(x, y, z);
                        BlockState checkState = level.getBlockState(checkPos);
                        if (checkState.is(QuestBlocks.OXYGEN_PANEL.get())) {
                            state.panelPositions().add(checkPos);
                            if (checkState.getValue(WallMountedPanelBlock.BROKEN)) {
                                brokenFound = true;
                            }
                        }
                    }
                }
            }
        }

        state.setBroken(brokenFound);
        data.setDirty();
    }

    public static void onQuestStarted(ServerLevel level, StationInstance station) {
        if (hasOxygenFailureQuest(level, station) || hasOxygenFailureOffer(station)) {
            StationOxygenSavedData data = StationOxygenSavedData.get(level);
            StationOxygenState state = data.getOrCreate(station.id());
            state.setBroken(true);
            data.setDirty();
        }
    }

    public static void onPanelPlaced(ServerLevel level, BlockPos pos, boolean broken) {
        Optional<StationInstance> stationOpt = stationAt(level, pos);
        if (stationOpt.isEmpty()) {
            return;
        }
        StationOxygenSavedData data = StationOxygenSavedData.get(level);
        StationOxygenState state = data.getOrCreate(stationOpt.get().id());
        state.panelPositions().add(pos);
        if (broken) {
            state.setBroken(true);
        }
        data.setDirty();
    }

    public static void onPanelRepaired(ServerLevel level, BlockPos pos, Player player) {
        Optional<StationInstance> stationOpt = stationAt(level, pos);
        if (stationOpt.isEmpty()) {
            return;
        }
        StationInstance station = stationOpt.get();
        StationOxygenSavedData data = StationOxygenSavedData.get(level);
        Optional<StationOxygenState> stateOpt = data.getStation(station.id());
        if (stateOpt.isEmpty()) {
            return;
        }
        StationOxygenState state = stateOpt.get();
        state.setBroken(false);
        data.setDirty();

        for (ServerPlayer serverPlayer : level.players()) {
            if (isInsideStation(station, serverPlayer.blockPosition())) {
                serverPlayer.setAirSupply(serverPlayer.getMaxAirSupply());
            }
        }
    }

    public static void onLivingBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.getAbilities().invulnerable) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<StationInstance> stationOpt = stationAt(level, player.blockPosition());
        if (stationOpt.isEmpty()) {
            return;
        }
        StationInstance station = stationOpt.get();
        StationOxygenSavedData data = StationOxygenSavedData.get(level);
        Optional<StationOxygenState> stateOpt = data.getStation(station.id());
        if (stateOpt.isEmpty() || !stateOpt.get().isBroken()) {
            return;
        }

        StationOxygenState state = stateOpt.get();
        boolean inHazardZone = state.isBroken();

        if (!inHazardZone) {
            for (BlockPos panelPos : state.panelPositions()) {
                BlockState panelState = level.getBlockState(panelPos);
                if (panelState.is(QuestBlocks.OXYGEN_PANEL.get()) && panelState.getValue(WallMountedPanelBlock.BROKEN)) {
                    inHazardZone = true;
                    break;
                }
            }
        }

        if (inHazardZone) {
            event.setCanBreathe(false);
            event.setConsumeAirAmount(1);
        }
    }

    public static boolean hasOxygenFailureOffer(StationInstance station) {
        CompoundTag customData = station.customData();
        if (customData.contains("stationOffers", Tag.TAG_LIST)) {
            ListTag offers = customData.getList("stationOffers", Tag.TAG_COMPOUND);
            for (int i = 0; i < offers.size(); i++) {
                CompoundTag offer = offers.getCompound(i);
                if (StationOfferType.from(offer.getString("type")) == StationOfferType.OXYGEN_FAILURE) {
                    return true;
                }
            }
        }
        return customData.contains("oxygenFailure") && customData.getBoolean("oxygenFailure");
    }

    private static boolean hasOxygenFailureQuest(ServerLevel level, StationInstance station) {
        Optional<QuestStationState> questState = QuestSavedData.get(level).stationIfPresent(station.id());
        if (questState.isEmpty()) {
            return false;
        }
        for (QuestObjectiveState objective : questState.get().objectives()) {
            String path = objective.id().toLowerCase(Locale.ROOT);
            if (path.contains("oxygen")) {
                return true;
            }
        }
        return false;
    }

    private static Optional<StationInstance> stationAt(ServerLevel level, BlockPos pos) {
        for (StationInstance station : StationSavedData.get(level).stations()) {
            if (isInsideStation(station, pos)) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    private static boolean isInsideStation(StationInstance station, BlockPos pos) {
        for (PlacedStationPiece piece : station.pieces()) {
            if (contains(piece, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(PlacedStationPiece piece, BlockPos pos) {
        return (pos.getX() >= piece.selectionBounds().minX() && pos.getX() <= piece.selectionBounds().maxX()
                && pos.getY() >= piece.selectionBounds().minY() && pos.getY() <= piece.selectionBounds().maxY()
                && pos.getZ() >= piece.selectionBounds().minZ() && pos.getZ() <= piece.selectionBounds().maxZ())
                || piece.bounds().isInside(pos);
    }

    private static boolean hasTriggerTag(PlacedTriggerZone zone, String requiredTag) {
        String tags = zone.data().contains(dev.sixik.stationarenear.structures.util.TagsConstants.Keys.TAGS)
                ? zone.data().getString(dev.sixik.stationarenear.structures.util.TagsConstants.Keys.TAGS)
                : zone.data().getString(dev.sixik.stationarenear.structures.util.TagsConstants.Keys.TAG);
        if (tags == null || tags.isBlank()) {
            return false;
        }
        for (String tag : tags.split(",")) {
            if (requiredTag.equalsIgnoreCase(tag.trim())) {
                return true;
            }
        }
        return false;
    }
}