package dev.sixik.stationarenear.sam.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sixik.stationarenear.sam.SamRussianTransliterator;
import dev.sixik.stationarenear.sam.SamTextSanitizer;
import dev.sixik.stationarenear.sam.SamVoice;
import dev.sixik.stationarenear.sam.network.SamNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class SamCommands {
    private SamCommands() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SamCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("sam")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> speak(
                                context.getSource(),
                                StringArgumentType.getString(context, "text"),
                                false
                        ))));
        dispatcher.register(Commands.literal("sam_ru")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> speak(
                                context.getSource(),
                                StringArgumentType.getString(context, "text"),
                                true
                        ))));
    }

    private static int speak(CommandSourceStack source, String rawText, boolean russian) {
        String text = normalizeText(russian ? SamRussianTransliterator.transliterate(rawText) : rawText);
        if (text.isBlank()) {
            source.sendFailure(Component.literal("SAM text is empty."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        long seed = level.getGameTime()
                ^ Double.doubleToLongBits(position.x())
                ^ Long.rotateLeft(Double.doubleToLongBits(position.y()), 17)
                ^ Long.rotateLeft(Double.doubleToLongBits(position.z()), 31)
                ^ text.hashCode();
        SamVoice voice = SamVoice.random(seed);
        SamNetwork.play(level, position, text, voice);
        source.sendSuccess(() -> Component.literal((russian ? "SAM RU says: " : "SAM says: ") + text), false);
        return 1;
    }

    private static String normalizeText(String text) {
        return SamTextSanitizer.normalizeForNetwork(text);
    }
}
