package dev.sixik.stationarenear.ship.block.entity;

import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.registry.ShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class PressureTightDoorBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation OPEN_ANIMATION = RawAnimation.begin().thenPlayAndHold("2");
    private static final RawAnimation CLOSE_ANIMATION = RawAnimation.begin().thenPlayAndHold("3");
    private static final RawAnimation BROKEN_ANIMATION = RawAnimation.begin().thenPlayAndHold("4");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean lastOpen;
    private boolean lastBroken;
    private String doorId = "";

    public PressureTightDoorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ShipBlocks.PRESSURE_TIGHT_DOOR_ENTITY.get(), pos, blockState);
        lastOpen = PressureTightDoorBlock.isOpen(blockState);
        lastBroken = PressureTightDoorBlock.isBroken(blockState);
    }

    public String doorId() {
        return doorId;
    }

    public void setDoorId(String doorId) {
        this.doorId = doorId == null ? "" : doorId.trim().toUpperCase(java.util.Locale.ROOT);
        setChanged();
    }

    public void markAnimationDirty() {
        if (level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "door", 0, state -> {
            boolean open = PressureTightDoorBlock.isOpen(getBlockState());
            boolean broken = PressureTightDoorBlock.isBroken(getBlockState());
            if (open != lastOpen || broken != lastBroken) {
                lastOpen = open;
                lastBroken = broken;
                state.getController().forceAnimationReset();
            }
            if (broken) {
                return state.setAndContinue(BROKEN_ANIMATION);
            }
            return state.setAndContinue(open ? OPEN_ANIMATION : CLOSE_ANIMATION);
        }));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!doorId.isBlank()) {
            tag.putString("doorId", doorId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        doorId = tag.contains("doorId") ? tag.getString("doorId") : "";
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
