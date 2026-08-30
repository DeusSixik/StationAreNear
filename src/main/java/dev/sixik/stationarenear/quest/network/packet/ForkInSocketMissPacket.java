package dev.sixik.stationarenear.quest.network.packet;

import dev.sixik.stationarenear.quest.registry.StationSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ForkInSocketMissPacket {

    public ForkInSocketMissPacket() {
    }

    public static void encode(ForkInSocketMissPacket packet, FriendlyByteBuf buf) {
    }

    public static ForkInSocketMissPacket decode(FriendlyByteBuf buf) {
        return new ForkInSocketMissPacket();
    }

    public static void handle(ForkInSocketMissPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && !player.isCreative() && !player.isSpectator()) {
                player.hurt(player.damageSources().lightningBolt(), 3.0F);
                player.serverLevel().playSound(null, player.blockPosition(), StationSounds.ELECTRIC_SHOCK.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        });
        context.setPacketHandled(true);
    }
}
