package dev.sixik.stationarenear.core.mixin.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {

    @Shadow
    public abstract void setGameMode(WorldCreationUiState.SelectedGameMode gameMode);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void stationarenear$defaultHardcore(CallbackInfo ci) {
        setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
    }
}
