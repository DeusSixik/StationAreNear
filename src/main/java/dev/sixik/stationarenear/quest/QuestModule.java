package dev.sixik.stationarenear.quest;

import dev.sixik.stationarenear.quest.command.QuestCommands;
import dev.sixik.stationarenear.quest.config.director.DirectorConfigManager;
import dev.sixik.stationarenear.quest.director.DirectorStationSpawnHandler;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
import dev.sixik.stationarenear.quest.runtime.QuestAnnouncementHandler;
import dev.sixik.stationarenear.quest.runtime.QuestFurniturePickupManager;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.quest.registry.QuestItems;
import dev.sixik.stationarenear.quest.registry.StationQuests;
import dev.sixik.stationarenear.quest.runtime.QuestTaskInteractionHandler;
import dev.sixik.stationarenear.quest.runtime.QuestTimerManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.IEventBus;

public final class QuestModule {

    private QuestModule() {
    }

    public static void register(IEventBus modEventBus) {
        QuestBlocks.register(modEventBus);
        QuestItems.register(modEventBus);
        dev.sixik.stationarenear.quest.registry.StationSounds.register(modEventBus);
        QuestNetwork.register();
        StationQuests.register();
        DirectorConfigManager.init();
        DirectorStationSpawnHandler.register();
        QuestCommands.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.sixik.stationarenear.quest.client.QuestClientEvents.register(modEventBus));
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, dev.sixik.stationarenear.quest.config.QuestConfig.SPEC, "stationarenear-quests.toml");
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.sixik.stationarenear.quest.runtime.AutoQuestManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(QuestFurniturePickupManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.sixik.stationarenear.structures.lamps.StationLampManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(dev.sixik.stationarenear.quest.runtime.AutoQuestManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(QuestFurniturePickupManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(QuestAnnouncementHandler::onQuestStarted);
        MinecraftForge.EVENT_BUS.addListener(dev.sixik.stationarenear.structures.gravity.StationGravitationManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.sixik.stationarenear.structures.oxygen.StationOxygenManager::onLivingBreathe);
        MinecraftForge.EVENT_BUS.addListener(dev.sixik.stationarenear.quest.runtime.StationRadiationManager::onPlayerTick);
        MinecraftForge.EVENT_BUS.register(QuestTaskInteractionHandler.class);
    }
}
