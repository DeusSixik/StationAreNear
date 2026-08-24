package dev.sixik.stationarenear.ship.block.entity;

import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.registry.ShipBlocks;
import net.minecraft.core.BlockPos;
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

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean lastOpen;

    public PressureTightDoorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ShipBlocks.PRESSURE_TIGHT_DOOR_ENTITY.get(), pos, blockState);
        lastOpen = PressureTightDoorBlock.isOpen(blockState);
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
            if (open != lastOpen) {
                lastOpen = open;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(open ? OPEN_ANIMATION : CLOSE_ANIMATION);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
