package dev.sixik.stationarenear.mob;

import dev.sixik.stationarenear.mob.network.LivingTrashNetwork;
import dev.sixik.stationarenear.mob.registry.StationMobEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;

public final class StationMobModule {

    private StationMobModule() {
    }

    public static void register(IEventBus modEventBus) {
        LivingTrashNetwork.register();
        StationMobEntities.register(modEventBus);
        modEventBus.addListener(StationMobEntities::registerAttributes);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.sixik.stationarenear.mob.client.StationMobClientEvents.register(modEventBus));
    }
}
