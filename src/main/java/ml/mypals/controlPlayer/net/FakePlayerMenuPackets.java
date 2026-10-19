package ml.mypals.controlPlayer.net;

import carpet.patches.EntityPlayerMPFake;
import ml.mypals.controlPlayer.PlayerControl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/**
 * Server-authoritative list: no hard-coded bot names and no client-side guessing.
 * The keybind is client-only; the menu is also available through /controlPlayer menu.
 */
public final class FakePlayerMenuPackets {
    private FakePlayerMenuPackets() {}

    public record Request() implements CustomPacketPayload {
        public static final Request INSTANCE = new Request();
        public static final Type<Request> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(PlayerControl.MOD_ID, "fake_player_menu_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                CustomPacketPayload.codec(Request::write, Request::read);

        private void write(RegistryFriendlyByteBuf buf) {}
        private static Request read(RegistryFriendlyByteBuf buf) { return INSTANCE; }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ListResponse(List<String> playerNames, boolean swapped, boolean maySwap)
            implements CustomPacketPayload {
        public static final Type<ListResponse> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(PlayerControl.MOD_ID, "fake_player_menu_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ListResponse> CODEC =
                CustomPacketPayload.codec(ListResponse::write, ListResponse::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(playerNames.size());
            for (String name : playerNames) {
                buf.writeUtf(name, 16);
            }
            buf.writeBoolean(swapped);
            buf.writeBoolean(maySwap);
        }

        private static ListResponse read(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 256) {
                throw new IllegalArgumentException("Invalid fake-player list size: " + count);
            }
            java.util.ArrayList<String> players = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                players.add(buf.readUtf(16));
            }
            return new ListResponse(List.copyOf(players), buf.readBoolean(), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ListResponse.TYPE, ListResponse.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) ->
                context.server().execute(() -> sendTo(context.player())));
    }

    public static void sendTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ListResponse.TYPE)) {
            return;
        }
        List<String> onlineFakes = player.level().getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p instanceof EntityPlayerMPFake)
                .map(p -> p.getGameProfile().name())
                .sorted(Comparator.naturalOrder())
                .limit(256)
                .toList();
        boolean canSwap = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        boolean isSwapped = PlayerControl.getSwapManager().isSwapped(player.getUUID());
        ServerPlayNetworking.send(player, new ListResponse(onlineFakes, isSwapped, canSwap));
    }
}
