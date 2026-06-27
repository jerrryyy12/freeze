package com.chameleon;

import com.chameleon.net.CamoSyncPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;

/**
 * 2-a 검증용 명령어.
 * /camo sample  — 바라보는 블록 색으로 전신 위장 텍스처 채우기
 * /camo clear   — 위장 해제
 */
public class CamoCommands {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("camo")
                .then(Commands.literal("paint").executes(ctx -> paint(ctx.getSource())))
                .then(Commands.literal("sample").executes(ctx -> sample(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource()))));
    }

    private static int paint(CommandSourceStack src) {
        ServerPlayer sp;
        try {
            sp = src.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        com.chameleon.net.ChameleonNet.sendOpenPaint(sp);
        return 1;
    }

    private static int sample(CommandSourceStack src) {
        ServerPlayer sp;
        try {
            sp = src.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        Level level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 end = eye.add(sp.getViewVector(1.0f).scale(20.0));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sp));
        if (hit.getType() != HitResult.Type.BLOCK) {
            src.sendFailure(Component.literal("바라보는 곳에 블록이 없습니다."));
            return 0;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        MapColor mc = state.getMapColor(level, pos);
        int color = 0xFF000000 | (mc.col & 0xFFFFFF);

        int[] px = new int[CamoSyncPacket.LEN];
        Arrays.fill(px, color);
        CamoStore.set(sp.getUUID(), px);
        src.sendSuccess(() -> Component.literal("위장색 적용: #" + String.format("%06X", color & 0xFFFFFF)), false);
        return 1;
    }

    private static int clear(CommandSourceStack src) {
        ServerPlayer sp;
        try {
            sp = src.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        CamoStore.set(sp.getUUID(), null);
        src.sendSuccess(() -> Component.literal("위장 해제"), false);
        return 1;
    }
}
