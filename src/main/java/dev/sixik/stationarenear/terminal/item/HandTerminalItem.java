package dev.sixik.stationarenear.terminal.item;

import dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Set;

public class HandTerminalItem extends Item {

    public HandTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos terminalPos = resolveTerminalPos(serverPlayer);
            TerminalNetwork.openTerminal(serverPlayer, terminalPos);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static BlockPos resolveTerminalPos(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Set<BlockPos> terminals = ShipIntegrityScanner.terminalsForBlock(level, player.blockPosition());
        if (!terminals.isEmpty()) {
            return terminals.iterator().next();
        }
        return BlockPos.ZERO;
    }
}
