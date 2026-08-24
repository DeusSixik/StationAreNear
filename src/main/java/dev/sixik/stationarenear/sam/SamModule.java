package dev.sixik.stationarenear.sam;

import dev.sixik.stationarenear.sam.client.SamClientAudio;
import dev.sixik.stationarenear.sam.command.SamCommands;
import dev.sixik.stationarenear.sam.network.SamNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;

public final class SamModule {
    private SamModule() {
    }

    public static void register(IEventBus modEventBus) {
        SamNetwork.register();
        SamCommands.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> SamClientAudio::register);
    }
}
