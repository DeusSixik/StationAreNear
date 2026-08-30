package dev.sixik.stationarenear.navigation;

import dev.sixik.stationarenear.navigation.event.SolarNavigationAsteroidCollisionEvent;
import dev.sixik.stationarenear.navigation.network.SolarNavigationNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.server.SolarNavigationControlManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SolarNavigationModule {

    private SolarNavigationModule() {
    }

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SolarNavigationConfig.SPEC, "stationarenear-navigation.toml");
        SolarNavigationNetwork.register();
        SolarNavigationBlocks.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(SolarNavigationControlManager::tick);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.addListener(dev.sixik.stationarenear.navigation.client.StationDockingOverlay::onRenderGui));
    }


//    private static void test(SolarNavigationAsteroidCollisionEvent event) {
//        System.out.println("Мы вьебались со скоростью: " +   event.impactSpeed());
//    }
}
