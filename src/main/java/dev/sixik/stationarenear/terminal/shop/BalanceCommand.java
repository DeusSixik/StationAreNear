package dev.sixik.stationarenear.terminal.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.UUID;

/**
 * Registers the {@code /balance} family of Forge server commands.
 *
 * <ul>
 *   <li>{@code /balance} — shows your own balance</li>
 *   <li>{@code /balance <player>} — shows another player's balance (OP only)</li>
 *   <li>{@code /balance set <player> <amount>} — sets balance (OP only)</li>
 *   <li>{@code /balance add <player> <amount>} — adds credits (OP only, negative to subtract)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = StationAreNear.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BalanceCommand {

    private BalanceCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("balance")
                .executes(BalanceCommand::showOwnBalance)

                .then(Commands.argument("player", EntityArgument.player())
                    .requires(src -> src.hasPermission(2))
                    .executes(BalanceCommand::showPlayerBalance)
                )

                .then(Commands.literal("set")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                            .executes(BalanceCommand::setPlayerBalance)
                        )
                    )
                )

                .then(Commands.literal("add")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                            .executes(BalanceCommand::addPlayerBalance)
                        )
                    )
                )
        );
    }

    private static int showOwnBalance(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            double balance = getBalance(player);
            src.sendSuccess(() -> Component.literal(
                    String.format(Locale.ROOT, "Your balance: %.2f credits", balance)), false);
        } catch (Exception ex) {
            src.sendFailure(Component.literal("This command can only be used by a player."));
        }
        return 1;
    }

    private static int showPlayerBalance(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            double balance = getBalance(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format(Locale.ROOT, "%s's balance: %.2f credits",
                            target.getDisplayName().getString(), balance)), false);
        } catch (Exception ex) {
            ctx.getSource().sendFailure(Component.literal("Player not found."));
        }
        return 1;
    }

    private static int setPlayerBalance(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            PlayerBalanceSavedData data = PlayerBalanceSavedData.get(target.serverLevel());
            data.setBalance(target.getUUID(), amount);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format(Locale.ROOT, "Set %s's balance to %.2f credits.",
                            target.getDisplayName().getString(), amount)), true);
        } catch (Exception ex) {
            ctx.getSource().sendFailure(Component.literal("Player not found."));
        }
        return 1;
    }

    private static int addPlayerBalance(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            PlayerBalanceSavedData data = PlayerBalanceSavedData.get(target.serverLevel());
            double newBalance = data.addBalance(target.getUUID(), amount);
            String sign = amount >= 0 ? "+" : "";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format(Locale.ROOT, "Balance of %s changed by %s%.2f → %.2f credits.",
                            target.getDisplayName().getString(), sign, amount, newBalance)), true);
        } catch (Exception ex) {
            ctx.getSource().sendFailure(Component.literal("Player not found."));
        }
        return 1;
    }

    private static double getBalance(ServerPlayer player) {
        return PlayerBalanceSavedData.get(player.serverLevel())
                .getBalance(player.getUUID());
    }
}
