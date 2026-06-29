package com.chameleon;

import com.chameleon.game.CamoGame;
import com.chameleon.net.CamoSyncPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("hide").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                .executes(ctx -> setTime(ctx.getSource(), "hide", IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("reveal").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                .executes(ctx -> setTime(ctx.getSource(), "reveal", IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("seek").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> setTime(ctx.getSource(), "seek", IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("mode").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("infection").executes(ctx -> setMode(ctx.getSource(), true)))
                        .then(Commands.literal("normal").executes(ctx -> setMode(ctx.getSource(), false))))
                .then(Commands.literal("game")
                        .then(Commands.literal("start").requires(s -> s.hasPermission(2))
                                .executes(ctx -> gameStart(ctx.getSource(), CamoGame.getDefaultSeekSeconds()))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                        .executes(ctx -> gameStart(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("stop").requires(s -> s.hasPermission(2))
                                .executes(ctx -> gameStop(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> gameStatus(ctx.getSource())))));
    }

    private static int gameStart(CommandSourceStack src, int seconds) {
        if (src.getServer() == null) return 0;
        if (CamoGame.isActive()) {
            src.sendFailure(Component.literal("이미 게임이 진행 중입니다. (/camo game stop)"));
            return 0;
        }
        int[] c = CamoGame.start(src.getServer(), seconds);
        src.sendSuccess(() -> Component.literal("§a게임 시작! 숨는 사람 " + c[0] + "명, 술래 " + c[1] + "명 · 숨기 3분 + 찾기 " + seconds + "초"), true);
        if (c[1] == 0) src.sendSuccess(() -> Component.literal("§e※ 술래(빨간 양털) 없음 — 빨간 양털 위에서 시작하세요."), false);
        return 1;
    }

    private static int setMode(CommandSourceStack src, boolean infection) {
        CamoGame.setInfectionMode(infection);
        src.sendSuccess(() -> Component.literal(infection
                ? "§a감염 모드 ON — 잡히면 술래가 됩니다. 숨는 사람끼리는 서로 안 보여요."
                : "§7일반 모드 — 잡히면 탈락(관전)."), true);
        return 1;
    }

    private static int gameStop(CommandSourceStack src) {
        if (src.getServer() == null || !CamoGame.isActive()) {
            src.sendFailure(Component.literal("진행 중인 게임이 없습니다."));
            return 0;
        }
        CamoGame.stop(src.getServer());
        src.sendSuccess(() -> Component.literal("§7게임 종료."), true);
        return 1;
    }

    private static int gameStatus(CommandSourceStack src) {
        if (!CamoGame.isActive()) {
            src.sendSuccess(() -> Component.literal("게임 없음. 파란 양털=숨기, 빨간 양털=술래 위에서 /camo game start"), false);
        } else {
            src.sendSuccess(() -> Component.literal("게임 진행 중 · 남은 시간 " + CamoGame.secondsLeft() + "초"), false);
        }
        src.sendSuccess(() -> Component.literal("§7설정: 숨기 " + CamoGame.getHideSeconds()
                + "초 · 찾기 " + CamoGame.getDefaultSeekSeconds() + "초 · 공개 " + CamoGame.getRevealSeconds() + "초"), false);
        return 1;
    }

    /** /camo set hide|reveal|seek <초> */
    private static int setTime(CommandSourceStack src, String which, int seconds) {
        switch (which) {
            case "hide" -> CamoGame.setHideSeconds(seconds);
            case "reveal" -> CamoGame.setRevealSeconds(seconds);
            case "seek" -> CamoGame.setDefaultSeekSeconds(seconds);
            default -> { return 0; }
        }
        src.sendSuccess(() -> Component.literal("§a시간 설정 — 숨기 " + CamoGame.getHideSeconds()
                + "초 · 찾기 " + CamoGame.getDefaultSeekSeconds() + "초 · 공개 " + CamoGame.getRevealSeconds()
                + "초 (다음 게임부터 적용)"), true);
        return 1;
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
