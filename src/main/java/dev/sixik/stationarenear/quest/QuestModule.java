package dev.sixik.stationarenear.quest;

import dev.sixik.stationarenear.quest.command.QuestCommands;
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
        QuestNetwork.register();
        StationQuests.register();
        QuestCommands.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> dev.sixik.stationarenear.quest.client.QuestClientEvents::register);
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(QuestFurniturePickupManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(QuestFurniturePickupManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(QuestAnnouncementHandler::onQuestStarted);
        MinecraftForge.EVENT_BUS.register(QuestTaskInteractionHandler.class);
    }
}
