package ml.mypals.controlPlayer.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import ml.mypals.controlPlayer.core.PlayerSwapManager;
import ml.mypals.controlPlayer.net.FakePlayerMenuPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


public class PlayerControlCommand {

    private final PlayerSwapManager manager;

    public PlayerControlCommand(PlayerSwapManager manager) {
        this.manager = manager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("controlPlayer")
                .then(Commands.literal("swap")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::swapPlayers)))
                .then(Commands.literal("release")
                        .executes(this::releaseSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(this::releaseTarget)))
                .then(Commands.literal("list")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(this::listSwaps))
                .then(Commands.literal("menu")
                        .executes(this::openMenu))
                .then(Commands.literal("status")
                        .executes(this::showStatus))
        );
    }

    private int swapPlayers(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer executor = ctx.getSource().getPlayer();
        if (executor == null) {
            ctx.getSource().sendFailure(Component.translatable("controlplayer.error.player_only").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("controlplayer.error.target_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        PlayerSwapManager.SwapResult result = manager.swap(executor, target);
        if (result.success()) {
            executor.sendSystemMessage(Component.translatable("controlplayer.swap.success", target.getName(), executor.getName()).withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.translatable("controlplayer.swap.success", executor.getName(), target.getName()).withStyle(ChatFormatting.GREEN));
            ctx.getSource().sendSuccess(() -> Component.translatable("controlplayer.swap.done").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(result.message().copy().withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private int releaseSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("controlplayer.error.player_only").withStyle(ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        PlayerSwapManager.SwapResult result = manager.release(player, server);
        if (result.success()) {
            player.sendSystemMessage(Component.translatable("controlplayer.release.success").withStyle(ChatFormatting.GREEN));
            return 1;
        } else {
            ctx.getSource().sendFailure(result.message().copy().withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private int releaseTarget(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("controlplayer.error.target_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        PlayerSwapManager.SwapResult result = manager.forceRelease(target.getUUID(), server);
        if (result.success()) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("controlplayer.release.force_success", target.getName()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(result.message().copy().withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private int listSwaps(CommandContext<CommandSourceStack> ctx) {
        var pairs = manager.getAllSwapPairs();
        MinecraftServer server = ctx.getSource().getServer();

        if (pairs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("controlplayer.list.empty").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("controlplayer.list.header").withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<UUID, UUID> pair : pairs) {
            String nameA = resolveName(server, pair.getKey());
            String nameB = resolveName(server, pair.getValue());
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("controlplayer.list.entry", nameA, nameB).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private int openMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("controlplayer.error.player_only"));
            return 0;
        }
        FakePlayerMenuPackets.sendTo(player);
        return 1;
    }

    private int showStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("controlplayer.error.player_only").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!manager.isSwapped(player.getUUID())) {
            ctx.getSource().sendSuccess(() -> Component.translatable("controlplayer.status.none").withStyle(ChatFormatting.GRAY), false);
        } else {
            manager.getSwapPartner(player.getUUID()).ifPresent(partnerUid -> {
                String partnerName = resolveName(player.level().getServer(), partnerUid);
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("controlplayer.status.partner", partnerName).withStyle(ChatFormatting.AQUA), false);
            });
        }
        return 1;
    }

    private static String resolveName(MinecraftServer server, UUID uid) {
        ServerPlayer p = server.getPlayerList().getPlayer(uid);
        if (p != null) return p.getName().getString();

        return uid.toString();
    }
}