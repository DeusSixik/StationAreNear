package dev.sixik.stationarenear.ship;

import dev.sixik.stationarenear.ship.command.ShipCommands;
import dev.sixik.stationarenear.ship.registry.ShipBlocks;
import dev.sixik.stationarenear.ship.runtime.ShipDecompressionEffects;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import software.bernie.geckolib.GeckoLib;

public final class ShipModule {

    private ShipModule() {
    }

    public static void register(IEventBus modEventBus) {
        GeckoLib.initialize();
        ShipBlocks.register(modEventBus);
        ShipCommands.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.sixik.stationarenear.ship.client.ShipClientEvents.register(modEventBus));
        MinecraftForge.EVENT_BUS.addListener(ShipManager::onAsteroidCollision);
        MinecraftForge.EVENT_BUS.addListener(ShipManager::onHullBlockDamage);
        MinecraftForge.EVENT_BUS.addListener(ShipDecompressionEffects::onShipDecompression);
        MinecraftForge.EVENT_BUS.addListener(ShipDecompressionEffects::onPlayerTick);
    }
}
