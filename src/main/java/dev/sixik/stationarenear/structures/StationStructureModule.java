package dev.sixik.stationarenear.structures;

import dev.sixik.stationarenear.structures.command.StationStructureCommands;
import dev.sixik.stationarenear.structures.config.StationStructureConfigManager;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorEvents;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.registry.StationStructureItems;
import dev.sixik.stationarenear.structures.trigger.StationTriggerHandlers;
import dev.sixik.stationarenear.structures.trigger.StationTriggerManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class StationStructureModule {

    private StationStructureModule() {
    }

    public static void register(IEventBus modEventBus) {
        StationStructureConfigManager.init();
        StationStructureNetwork.register();
        StationStructureItems.register(modEventBus);
        StationStructureCommands.register();
        StationStructureEditorEvents.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.sixik.stationarenear.structures.client.StationStructureEditorClientEvents.register());
        StationTriggerManager.register();
        StationTriggerHandlers.register();
    }
}
