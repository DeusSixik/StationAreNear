package dev.sixik.stationarenear.quest.network.packet;

import dev.sixik.stationarenear.quest.item.HeavyApplianceBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ForkInSocketSuccessPacket {

    private final BlockPos clickedPos;
    private final Direction clickedFace;
    private final InteractionHand hand;

    public ForkInSocketSuccessPacket(BlockPos clickedPos, Direction clickedFace, InteractionHand hand) {
        this.clickedPos = clickedPos;
        this.clickedFace = clickedFace;
        this.hand = hand;
    }

    public static void encode(ForkInSocketSuccessPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.clickedPos);
        buf.writeEnum(packet.clickedFace);
        buf.writeEnum(packet.hand);
    }

    public static ForkInSocketSuccessPacket decode(FriendlyByteBuf buf) {
        return new ForkInSocketSuccessPacket(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readEnum(InteractionHand.class));
    }

    public static void handle(ForkInSocketSuccessPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.isSpectator()) {
                return;
            }

            ItemStack stack = player.getItemInHand(packet.hand);
            if (stack.getItem() instanceof HeavyApplianceBlockItem blockItem) {
                BlockHitResult hitResult = new BlockHitResult(
                        Vec3.atCenterOf(packet.clickedPos),
                        packet.clickedFace,
                        packet.clickedPos,
                        false
                );
                UseOnContext useOnContext = new UseOnContext(player, packet.hand, hitResult);
                BlockPlaceContext placeContext = new BlockPlaceContext(useOnContext);
                if (placeContext.canPlace()) {
                    blockItem.place(placeContext);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
