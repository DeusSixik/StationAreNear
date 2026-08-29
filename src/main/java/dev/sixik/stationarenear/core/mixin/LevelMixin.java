package dev.sixik.stationarenear.core.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelTimeAccess {

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void disableRaining(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void disableThundering(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void disableRainLevel(float delta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0F);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void disableThunderLevel(float delta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0F);
    }

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void alwaysNightDayTime(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(18000L);
    }

    @Override
    public float getTimeOfDay(float delta) {
        return 0.5F;
    }

    @Inject(method = "isNight", at = @At("HEAD"), cancellable = true)
    private void forceIsNight(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "isDay", at = @At("HEAD"), cancellable = true)
    private void forceIsDay(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
