package dev.sixik.stationarenear.structures.oxygen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class StationOxygenState {

    private final UUID stationId;
    private final Set<BlockPos> panelPositions = new LinkedHashSet<>();
    private boolean broken;
    private long nextDamageTick;

    public StationOxygenState(UUID stationId) {
        this.stationId = stationId;
    }

    public UUID stationId() {
        return stationId;
    }

    public Set<BlockPos> panelPositions() {
        return panelPositions;
    }

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
    }

    public long nextDamageTick() {
        return nextDamageTick;
    }

    public void setNextDamageTick(long nextDamageTick) {
        this.nextDamageTick = nextDamageTick;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("stationId", stationId);
        tag.putBoolean("broken", broken);
        tag.putLong("nextDamageTick", nextDamageTick);

        ListTag list = new ListTag();
        for (BlockPos pos : panelPositions) {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put("panelPositions", list);
        return tag;
    }

    public static StationOxygenState load(CompoundTag tag) {
        UUID id = tag.hasUUID("stationId") ? tag.getUUID("stationId") : UUID.randomUUID();
        StationOxygenState state = new StationOxygenState(id);
        state.broken = tag.getBoolean("broken");
        state.nextDamageTick = tag.getLong("nextDamageTick");

        ListTag list = tag.getList("panelPositions", Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            state.panelPositions.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
        return state;
    }
}