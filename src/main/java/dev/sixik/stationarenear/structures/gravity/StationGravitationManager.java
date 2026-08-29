package dev.sixik.stationarenear.structures.gravity;

import dev.sixik.stationarenear.quest.config.director.StationOfferType;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Locale;
import java.util.Optional;

public final class StationGravitationManager {

    private StationGravitationManager() {
    }

    public static void onStationGenerated(ServerLevel level, StationInstance station) {
        StationGravitationSavedData data = StationGravitationSavedData.get(level);
        StationGravitationState state = data.getOrCreate(station.id());

        boolean brokenFound = hasGravitationFailureOffer(station);

        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                if (zone.data().contains("brokenGravitation") && zone.data().getBoolean("brokenGravitation")) {
                    brokenFound = true;
                }
            }
        }

        state.setBroken(brokenFound);
        if (brokenFound) {
            state.setNextSurgeTick(level.getGameTime() + 120 + level.random.nextInt(120));
        }
        data.setDirty();
    }

    public static void onQuestStarted(ServerLevel level, StationInstance station) {
        if (hasGravitationFailureQuest(level, station) || hasGravitationFailureOffer(station)) {
            StationGravitationSavedData data = StationGravitationSavedData.get(level);
            StationGravitationState state = data.getOrCreate(station.id());
            state.setBroken(true);
            state.setNextSurgeTick(level.getGameTime() + 100 + level.random.nextInt(100));
            data.setDirty();
        }
    }

    public static void onPanelPlaced(ServerLevel level, BlockPos pos, boolean broken) {
        Optional<StationInstance> stationOpt = stationAt(level, pos);
        if (stationOpt.isEmpty()) {
            return;
        }
        StationGravitationSavedData data = StationGravitationSavedData.get(level);
        StationGravitationState state = data.getOrCreate(stationOpt.get().id());
        state.panelPositions().add(pos);
        if (broken) {
            state.setBroken(true);
            if (state.nextSurgeTick() <= 0) {
                state.setNextSurgeTick(level.getGameTime() + 100);
            }
        }
        data.setDirty();
    }

    public static void onPanelRepaired(ServerLevel level, BlockPos pos, Player player) {
        Optional<StationInstance> stationOpt = stationAt(level, pos);
        if (stationOpt.isEmpty()) {
            return;
        }
        StationInstance station = stationOpt.get();
        StationGravitationSavedData data = StationGravitationSavedData.get(level);
        Optional<StationGravitationState> stateOpt = data.getStation(station.id());
        if (stateOpt.isEmpty()) {
            return;
        }
        StationGravitationState state = stateOpt.get();
        state.setBroken(false);
        data.setDirty();

        for (ServerPlayer serverPlayer : level.players()) {
            if (isInsideStation(station, serverPlayer.blockPosition())) {
                serverPlayer.removeEffect(MobEffects.SLOW_FALLING);
                serverPlayer.removeEffect(MobEffects.LEVITATION);
                serverPlayer.removeEffect(MobEffects.JUMP);
            }
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            Optional<StationInstance> stationOpt = stationAt(level, player.blockPosition());
            if (stationOpt.isEmpty()) {
                continue;
            }
            StationInstance station = stationOpt.get();
            StationGravitationSavedData data = StationGravitationSavedData.get(level);
            Optional<StationGravitationState> stateOpt = data.getStation(station.id());
            if (stateOpt.isEmpty() || !stateOpt.get().isBroken()) {
                continue;
            }

            StationGravitationState state = stateOpt.get();
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false, false));

            long now = level.getGameTime();
            if (state.nextSurgeTick() <= 0) {
                state.setNextSurgeTick(now + 160 + player.getRandom().nextInt(140));
                data.setDirty();
            }

            if (now >= state.nextSurgeTick() && now < state.nextSurgeTick() + 50) {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 0, false, false, false));
                if (now == state.nextSurgeTick()) {
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.6F);
                }
            } else if (now >= state.nextSurgeTick() + 50) {
                state.setNextSurgeTick(now + 200 + player.getRandom().nextInt(200));
                data.setDirty();
            }
        }
    }

    private static boolean hasGravitationFailureOffer(StationInstance station) {
        CompoundTag customData = station.customData();
        if (customData.contains("stationOffers", Tag.TAG_LIST)) {
            ListTag offers = customData.getList("stationOffers", Tag.TAG_COMPOUND);
            for (int i = 0; i < offers.size(); i++) {
                CompoundTag offer = offers.getCompound(i);
                if (StationOfferType.from(offer.getString("type")) == StationOfferType.GRAVITATION_FAILURE) {
                    return true;
                }
            }
        }
        return customData.contains("gravitationFailure") && customData.getBoolean("gravitationFailure");
    }

    private static boolean hasGravitationFailureQuest(ServerLevel level, StationInstance station) {
        Optional<QuestStationState> questState = QuestSavedData.get(level).stationIfPresent(station.id());
        if (questState.isEmpty()) {
            return false;
        }
        for (QuestObjectiveState objective : questState.get().objectives()) {
            String path = objective.id().toLowerCase(Locale.ROOT);
            if (path.contains("gravitation") || path.contains("gravity")) {
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
        return pos.getX() >= piece.selectionBounds().minX() && pos.getX() <= piece.selectionBounds().maxX()
                && pos.getY() >= piece.selectionBounds().minY() && pos.getY() <= piece.selectionBounds().maxY()
                && pos.getZ() >= piece.selectionBounds().minZ() && pos.getZ() <= piece.selectionBounds().maxZ();
    }
}