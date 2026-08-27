package dev.sixik.stationarenear.quest;

import dev.sixik.stationarenear.quest.command.QuestCommands;
import dev.sixik.stationarenear.quest.runtime.QuestAnnouncementHandler;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.quest.registry.QuestItems;
import dev.sixik.stationarenear.quest.registry.StationQuests;
import dev.sixik.stationarenear.quest.runtime.QuestTaskInteractionHandler;
import dev.sixik.stationarenear.quest.runtime.QuestTimerManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class QuestModule {

    private QuestModule() {
    }

    public static void register(IEventBus modEventBus) {
        QuestBlocks.register(modEventBus);
        QuestItems.register(modEventBus);
        StationQuests.register();
        QuestCommands.register();
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(QuestAnnouncementHandler::onQuestStarted);
        MinecraftForge.EVENT_BUS.register(QuestTaskInteractionHandler.class);
    }
}
