package com.freeze;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class FreezeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("freeze")
                .requires(Commands.hasPermission(2))
                .then(Commands.literal("pos1").executes(ctx -> setPos(ctx, 1)))
                .then(Commands.literal("pos2").executes(ctx -> setPos(ctx, 2)))
                .then(Commands.literal("on").executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> toggle(ctx, false)))
                .then(Commands.literal("info").executes(FreezeCommand::info))
        );
    }

    private static int setPos(CommandContext<CommandSourceStack> ctx, int index) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FreezeMod.AREA.setCorner(index, player);
        BlockInfo info = new BlockInfo(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("§a%d번 모서리 설정됨: (%d, %d, %d) @ %s",
                        index, info.x, info.y, info.z, info.dim)), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean on) {
        if (on && !FreezeMod.AREA.isConfigured()) {
            ctx.getSource().sendFailure(Component.literal("§c영역이 설정되지 않았습니다. pos1, pos2를 먼저 사용하세요."));
            return 0;
        }
        FreezeMod.AREA.setEnabled(on);
        ctx.getSource().sendSuccess(() -> Component.literal(
                on ? "§aFreeze 활성화됨." : "§eFreeze 비활성화됨."), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Freeze] " + FreezeMod.AREA.describe()), false);
        return 1;
    }

    private record BlockInfo(int x, int y, int z, String dim) {
        BlockInfo(ServerPlayer p) {
            this(p.blockPosition().getX(), p.blockPosition().getY(), p.blockPosition().getZ(),
                    ((ServerLevel) p.level()).dimension().location().toString());
        }
    }
}
