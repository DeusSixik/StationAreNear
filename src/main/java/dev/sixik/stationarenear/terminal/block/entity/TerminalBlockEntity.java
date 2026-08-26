package dev.sixik.stationarenear.terminal.block.entity;

import dev.sixik.stationarenear.terminal.registry.TerminalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TerminalBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("0");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TerminalBlockEntity(BlockPos pos, BlockState blockState) {
        super(TerminalBlocks.TERMINAL_ENTITY.get(), pos, blockState);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "terminal", 0, state -> state.setAndContinue(IDLE_ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
