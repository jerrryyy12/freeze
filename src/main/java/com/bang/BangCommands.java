package com.bang;

import com.bang.game.BangGame;
import com.bang.game.BangHeads;
import com.bang.game.BangInventory;
import com.bang.game.BangPlayer;
import com.bang.game.BangTable;
import com.bang.game.Card;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

public class BangCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bang")
                .then(Commands.literal("create").executes(BangCommands::create))
                .then(Commands.literal("join").executes(BangCommands::join))
                .then(Commands.literal("addbot").executes(BangCommands::addbot))
                .then(Commands.literal("leave").executes(BangCommands::leave))
                .then(Commands.literal("start").executes(BangCommands::start))
                .then(Commands.literal("stop").executes(BangCommands::stop))
                .then(Commands.literal("settable").executes(BangCommands::settable))
                .then(Commands.literal("status").executes(BangCommands::status))
                .then(Commands.literal("hand").executes(BangCommands::hand))
                .then(Commands.literal("list").executes(BangCommands::list))
                .then(Commands.literal("end").executes(BangCommands::end))
                .then(Commands.literal("play")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> play(ctx, null))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "target"))))))
        );
    }

    private static void announce(MinecraftServer server, String text) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(text));
        }
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (BangMod.game != null && (BangMod.game.isInLobby() || BangMod.game.isPlaying())) {
            ctx.getSource().sendFailure(Component.literal("§c이미 진행 중인 방이 있습니다. /bang join 또는 /bang stop"));
            return 0;
        }
        BangGame g = new BangGame(sp.getUUID());
        g.addPlayer(sp.getUUID(), sp.getName().getString());
        BangMod.game = g;
        announce(ctx.getSource().getServer(),
                "§6[BANG] §f" + sp.getName().getString() + " 님이 방을 만들었습니다! §7/bang join 으로 참가 (4~7명)");
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.isInLobby()) { ctx.getSource().sendFailure(Component.literal("§c참가할 수 있는 방이 없습니다.")); return 0; }
        if (g.contains(sp.getUUID())) { ctx.getSource().sendFailure(Component.literal("§c이미 참가했습니다.")); return 0; }
        if (!g.addPlayer(sp.getUUID(), sp.getName().getString())) { ctx.getSource().sendFailure(Component.literal("§c정원이 찼습니다(최대 7명).")); return 0; }
        announce(ctx.getSource().getServer(), "§6[BANG] §f" + sp.getName().getString() + " 참가 §7(" + g.size() + "명)");
        return 1;
    }

    private static int addbot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.isInLobby()) { ctx.getSource().sendFailure(Component.literal("§c먼저 /bang create 로 방을 만드세요.")); return 0; }
        if (!g.host.equals(sp.getUUID())) { ctx.getSource().sendFailure(Component.literal("§c방장만 봇을 추가할 수 있습니다.")); return 0; }
        if (!g.addBot("봇" + g.size())) { ctx.getSource().sendFailure(Component.literal("§c정원이 찼습니다(최대 7명).")); return 0; }
        announce(ctx.getSource().getServer(), "§6[BANG] §7테스트 봇 추가됨 §7(" + g.size() + "명)");
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.contains(sp.getUUID())) { ctx.getSource().sendFailure(Component.literal("§c참가한 방이 없습니다.")); return 0; }
        if (!g.isInLobby()) { ctx.getSource().sendFailure(Component.literal("§c진행 중에는 나갈 수 없습니다.")); return 0; }
        g.removePlayer(sp.getUUID());
        announce(ctx.getSource().getServer(), "§6[BANG] §f" + sp.getName().getString() + " 퇴장 §7(" + g.size() + "명)");
        if (g.size() == 0) BangMod.game = null;
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.isInLobby()) { ctx.getSource().sendFailure(Component.literal("§c시작할 방이 없습니다.")); return 0; }
        if (!g.host.equals(sp.getUUID())) { ctx.getSource().sendFailure(Component.literal("§c방장만 시작할 수 있습니다.")); return 0; }
        if (g.size() < 4) { ctx.getSource().sendFailure(Component.literal("§c최소 4명이 필요합니다 (현재 " + g.size() + "명).")); return 0; }
        if (!g.start(ctx.getSource().getServer())) { ctx.getSource().sendFailure(Component.literal("§c시작에 실패했습니다.")); return 0; }
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null) { ctx.getSource().sendFailure(Component.literal("§c진행 중인 게임이 없습니다.")); return 0; }
        if (!g.host.equals(sp.getUUID())) { ctx.getSource().sendFailure(Component.literal("§c방장만 종료할 수 있습니다.")); return 0; }
        announce(ctx.getSource().getServer(), "§6[BANG] 게임이 종료되었습니다.");
        BangTable.clear(ctx.getSource().getServer());
        BangHeads.clear();
        BangInventory.endGame(ctx.getSource().getServer(), g);
        BangMod.game = null;
        return 1;
    }

    private static int settable(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangTable.setAnchor(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a테이블 위치 등록됨 (현재 선 자리). §7게임 시작/진행 시 이 자리에 카드가 표시됩니다."), false);
        BangGame g = BangMod.game;
        if (g != null && g.isPlaying()) BangTable.render(ctx.getSource().getServer(), g);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        CommandSourceStack src = ctx.getSource();
        if (g == null) { src.sendFailure(Component.literal("§c진행 중인 게임이 없습니다.")); return 0; }
        if (g.isInLobby()) {
            StringBuilder sb = new StringBuilder("§6[BANG] 로비 (" + g.size() + "명): §f");
            for (BangPlayer p : g.seating().isEmpty() ? java.util.List.<BangPlayer>of() : g.seating()) sb.append(p.name).append(" ");
            // 로비에서는 seating 비어있으므로 별도 출력
            src.sendSuccess(() -> Component.literal("§6[BANG] 로비 인원: " + g.size() + "명. 방장만 /bang start (4~7명)"), false);
            return 1;
        }
        BangPlayer cur = g.current();
        src.sendSuccess(() -> Component.literal("§b▶ 현재 턴: " + (cur != null ? cur.name : "?")), false);
        for (BangPlayer p : g.seating()) {
            src.sendSuccess(() -> Component.literal("§7- " + p.name + " : " + (p.alive ? "체력 " + p.hp + "/" + p.maxHp : "§8탈락")
                    + " §8(손패 " + p.hand.size() + ")"), false);
        }
        BangPlayer me = g.get(sp.getUUID());
        if (me != null) {
            src.sendSuccess(() -> Component.literal("§7[나] 역할: " + me.role.kr + " / 캐릭터: " + me.character.kr
                    + " / 사정거리 " + me.weaponRange()), false);
        }
        return 1;
    }

    private static int hand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.isPlaying()) { ctx.getSource().sendFailure(Component.literal("§c진행 중인 게임이 없습니다.")); return 0; }
        BangPlayer me = g.get(sp.getUUID());
        if (me == null) { ctx.getSource().sendFailure(Component.literal("§c당신은 이 게임에 없습니다.")); return 0; }
        // 손패 GUI 열기
        sp.openMenu(new SimpleMenuProvider((id, inv, player) -> {
            SimpleContainer c = new SimpleContainer(BangHandMenu.CARD_SLOTS);
            for (int i = 0; i < me.hand.size() && i < BangHandMenu.CARD_SLOTS; i++) {
                c.setItem(i, new ItemStack(BangItems.itemFor(me.hand.get(i).type)));
            }
            return new BangHandMenu(id, inv, c, sp);
        }, Component.literal("내 손패 (체력 " + me.hp + "/" + me.maxHp + ")")));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.isPlaying()) { ctx.getSource().sendFailure(Component.literal("§c진행 중인 게임이 없습니다.")); return 0; }
        BangPlayer me = g.get(sp.getUUID());
        if (me == null) { ctx.getSource().sendFailure(Component.literal("§c당신은 이 게임에 없습니다.")); return 0; }
        sp.sendSystemMessage(Component.literal("§6===== 내 손패 (체력 " + me.hp + "/" + me.maxHp + ") ====="));
        if (me.hand.isEmpty()) sp.sendSystemMessage(Component.literal("§8(손패 없음)"));
        for (int i = 0; i < me.hand.size(); i++) {
            Card c = me.hand.get(i);
            sp.sendSystemMessage(Component.literal("§e" + (i + 1) + ". §f" + c.label()));
        }
        if (!me.equipment.isEmpty()) {
            StringBuilder eq = new StringBuilder("§7장비: ");
            for (Card c : me.equipment) eq.append(c.type.kr).append(" ");
            sp.sendSystemMessage(Component.literal(eq.toString()));
        }
        sp.sendSystemMessage(Component.literal("§8사용: /bang play <번호> [대상이름]"));
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.isPlaying()) { ctx.getSource().sendFailure(Component.literal("§c진행 중인 게임이 없습니다.")); return 0; }
        BangPlayer me = g.get(sp.getUUID());
        if (me == null || !g.isCurrent(me.id)) { ctx.getSource().sendFailure(Component.literal("§c당신의 턴이 아닙니다.")); return 0; }
        g.endTurn(ctx.getSource().getServer(), me);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, String target) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BangGame g = BangMod.game;
        if (g == null || !g.isPlaying()) { ctx.getSource().sendFailure(Component.literal("§c진행 중인 게임이 없습니다.")); return 0; }
        BangPlayer me = g.get(sp.getUUID());
        if (me == null) { ctx.getSource().sendFailure(Component.literal("§c당신은 이 게임에 없습니다.")); return 0; }
        int index = IntegerArgumentType.getInteger(ctx, "index");
        g.playCard(ctx.getSource().getServer(), me, index - 1, target);
        return 1;
    }
}
