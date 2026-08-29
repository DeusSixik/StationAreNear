package dev.sixik.stationarenear.terminal.shop;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StationAreNear.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShopEvents {

    private ShopEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ShopCatalog.init();
    }
}
