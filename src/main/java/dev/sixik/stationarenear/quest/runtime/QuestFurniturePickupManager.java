package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.block.EnergyPanelBlock;
import dev.sixik.stationarenear.quest.block.QuestPickupBlock;
import dev.sixik.stationarenear.quest.data.QuestDefinition;
import dev.sixik.stationarenear.quest.data.QuestObjectiveKind;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.lamps.StationLampManager;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestFurniturePickupManager {

    private static final int HOLD_TIMEOUT_TICKS = 3;
    private static final double MAX_DISTANCE_SQR = 36.0D;
    private static final String KEY_DONE_POSITIONS = "donePositions";
    private static final Map<UUID, PickupProgress> PICKUPS = new HashMap<>();

    public enum HoldTargetType {
        FURNITURE(35),
        ENERGY_PANEL(30);

        private final int requiredTicks;

        HoldTargetType(int requiredTicks) {
            this.requiredTicks = requiredTicks;
        }

        public int requiredTicks() {
            return requiredTicks;
        }
    }

    private QuestFurniturePickupManager() {
    }

    public static void hold(ServerPlayer player, BlockPos hitPos) {
        if (player.isSpectator()) {
            stop(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<HoldTarget> target = targetAt(level, hitPos);
        if (target.isEmpty()) {
            stop(player);
            return;
        }
        if (target.get().type() == HoldTargetType.FURNITURE && isQuestLocked(level, target.get().masterPos())) {
            player.displayClientMessage(Component.literal("Предмет закреплён заданием"), true);
            stop(player);
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(target.get().masterPos())) > MAX_DISTANCE_SQR) {
            stop(player);
            return;
        }

        long now = level.getGameTime();
        PickupProgress current = PICKUPS.get(player.getUUID());
        if (current != null && current.sameTarget(level.dimension(), target.get().masterPos())) {
            PICKUPS.put(player.getUUID(), current.withLastHoldTick(now));
        } else {
            PICKUPS.put(player.getUUID(), new PickupProgress(level.dimension(), target.get().masterPos(), target.get().type(), now, now));
        }
    }

    public static void stop(ServerPlayer player) {
        if (PICKUPS.remove(player.getUUID()) != null) {
            hideProgress(player);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PICKUPS.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            PICKUPS.clear();
            return;
        }

        Iterator<Map.Entry<UUID, PickupProgress>> iterator = PICKUPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PickupProgress> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            PickupProgress progress = entry.getValue();
            if (!progress.dimension().equals(player.level().dimension())) {
                iterator.remove();
                hideProgress(player);
                continue;
            }

            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            if (now - progress.lastHoldTick() > HOLD_TIMEOUT_TICKS) {
                iterator.remove();
                hideProgress(player);
                continue;
            }
            Optional<HoldTarget> target = targetAt(level, progress.masterPos());
            if (target.isEmpty() || (target.get().type() == HoldTargetType.FURNITURE && isQuestLocked(level, target.get().masterPos()))
                    || player.distanceToSqr(Vec3.atCenterOf(target.get().masterPos())) > MAX_DISTANCE_SQR) {
                iterator.remove();
                hideProgress(player);
                continue;
            }

            long heldTicks = now - progress.startedTick();
            int requiredTicks = progress.type().requiredTicks();
            float ratio = Math.max(0.0F, Math.min(1.0F, heldTicks / (float) requiredTicks));
            if (heldTicks >= requiredTicks) {
                if (progress.type() == HoldTargetType.FURNITURE) {
                    finishPickup(player, level, target.get());
                } else if (progress.type() == HoldTargetType.ENERGY_PANEL) {
                    finishEnergyPanelToggle(player, level, target.get().masterPos());
                }
                iterator.remove();
                hideProgress(player);
            } else {
                String title;
                if (progress.type() == HoldTargetType.FURNITURE) {
                    title = "Подбор " + Math.round(ratio * 100.0F) + "%";
                } else {
                    boolean currentPowered = target.get().masterState().getValue(EnergyPanelBlock.POWERED);
                    title = (currentPowered ? "Выключение щитка " : "Включение щитка ") + Math.round(ratio * 100.0F) + "%";
                }
                syncProgress(player, ratio, title);
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        PICKUPS.clear();
    }

    private static void finishPickup(ServerPlayer player, ServerLevel level, HoldTarget target) {
        if (target.pickup() == null || isQuestLocked(level, target.masterPos())) {
            player.displayClientMessage(Component.literal("Предмет закреплён заданием"), true);
            return;
        }
        ItemStack stack = target.pickup().pickupStack(target.masterState()).copy();
        target.pickup().pickupRemove(level, target.masterPos(), target.masterState());
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.literal("Поднято"), true);
    }

    private static void finishEnergyPanelToggle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        if (!currentState.is(QuestBlocks.ENERGY_PANEL.get()) || currentState.getValue(EnergyPanelBlock.BROKEN)) {
            return;
        }
        boolean nextPowered = !currentState.getValue(EnergyPanelBlock.POWERED);
        level.setBlock(pos, currentState.setValue(EnergyPanelBlock.POWERED, nextPowered), 3);
        float pitch = nextPowered ? 0.6F : 0.5F;
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, pitch);
        StationLampManager.onPanelToggled(level, pos, nextPowered);
    }

    private static void syncProgress(ServerPlayer player, float progress, String title) {
        QuestNetwork.syncFurniturePickupProgress(player, Math.max(0.0F, Math.min(1.0F, progress)), true, title);
    }

    private static void hideProgress(ServerPlayer player) {
        QuestNetwork.syncFurniturePickupProgress(player, 0.0F, false, "");
    }

    private static Optional<HoldTarget> targetAt(ServerLevel level, BlockPos hitPos) {
        BlockState state = level.getBlockState(hitPos);
        if (state.getBlock() instanceof QuestPickupBlock pickup) {
            BlockPos masterPos = pickup.pickupMasterPos(hitPos, state);
            BlockState masterState = level.getBlockState(masterPos);
            if (!(masterState.getBlock() instanceof QuestPickupBlock masterPickup)) {
                return Optional.empty();
            }
            return Optional.of(new HoldTarget(HoldTargetType.FURNITURE, masterPos, masterState, masterPickup));
        }

        if (state.is(QuestBlocks.ENERGY_PANEL.get()) && !state.getValue(EnergyPanelBlock.BROKEN)) {
            return Optional.of(new HoldTarget(HoldTargetType.ENERGY_PANEL, hitPos, state, null));
        }

        return Optional.empty();
    }

    private static boolean isQuestLocked(ServerLevel level, BlockPos pos) {
        Optional<StationInstance> station = stationAt(level, pos);
        if (station.isEmpty()) {
            return false;
        }
        Optional<QuestStationState> questState = QuestSavedData.get(level).stationIfPresent(station.get().id());
        if (questState.isEmpty()) {
            return false;
        }
        for (QuestObjectiveState objective : questState.get().objectives()) {
            if (isPlaceItemObjective(objective) && wasPlacedForQuest(objective, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlaceItemObjective(QuestObjectiveState objective) {
        return QuestApi.definition(objective.id())
                .map(QuestDefinition::kind)
                .map(kind -> kind == QuestObjectiveKind.PLACE_ITEM)
                .orElse(false);
    }

    private static boolean wasPlacedForQuest(QuestObjectiveState objective, BlockPos pos) {
        return objective.progress().getList(KEY_DONE_POSITIONS, Tag.TAG_LONG).contains(LongTag.valueOf(pos.asLong()));
    }

    private static Optional<StationInstance> stationAt(ServerLevel level, BlockPos pos) {
        return StationSavedData.get(level).stations().stream()
                .filter(station -> station.pieces().stream().anyMatch(piece -> contains(piece, pos)))
                .findFirst();
    }

    private static boolean contains(PlacedStationPiece piece, BlockPos pos) {
        return pos.getX() >= piece.selectionBounds().minX() && pos.getX() <= piece.selectionBounds().maxX()
                && pos.getY() >= piece.selectionBounds().minY() && pos.getY() <= piece.selectionBounds().maxY()
                && pos.getZ() >= piece.selectionBounds().minZ() && pos.getZ() <= piece.selectionBounds().maxZ();
    }

    private record HoldTarget(HoldTargetType type, BlockPos masterPos, BlockState masterState, @Nullable QuestPickupBlock pickup) {
    }

    private record PickupProgress(ResourceKey<Level> dimension, BlockPos masterPos, HoldTargetType type, long startedTick, long lastHoldTick) {

        private boolean sameTarget(ResourceKey<Level> dimension, BlockPos masterPos) {
            return this.dimension.equals(dimension) && this.masterPos.equals(masterPos);
        }

        private PickupProgress withLastHoldTick(long lastHoldTick) {
            return new PickupProgress(dimension, masterPos, type, startedTick, lastHoldTick);
        }
    }
}