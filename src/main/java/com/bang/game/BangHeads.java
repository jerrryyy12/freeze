package com.bang.game;

import com.bang.BangItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 각 플레이어 머리 위에 캐릭터 카드(공개 정보)를 띄우고 따라다니게 한다. */
public class BangHeads {

    private static final Map<UUID, Display.ItemDisplay> heads = new HashMap<>();
    private static final float SCALE = 0.6f;
    private static final double HEIGHT = 2.3;

    public static void rebuild(MinecraftServer server, BangGame game) {
        clear();
        if (game == null || !game.isPlaying()) return;
        for (BangPlayer bp : game.seating()) {
            if (bp.isBot || !bp.alive) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(bp.id);
            if (sp != null) spawnHead(sp, bp);
        }
    }

    /** 매 틱 머리 위로 위치 갱신, 탈락/퇴장 시 제거 */
    public static void tick(MinecraftServer server, BangGame game) {
        if (game == null) return;
        Iterator<Map.Entry<UUID, Display.ItemDisplay>> it = heads.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Display.ItemDisplay> e = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            BangPlayer bp = game.get(e.getKey());
            Display.ItemDisplay d = e.getValue();
            if (sp == null || bp == null || !bp.alive || d == null || d.isRemoved()) {
                if (d != null && !d.isRemoved()) d.discard();
                it.remove();
                continue;
            }
            d.setPos(sp.getX(), sp.getY() + HEIGHT, sp.getZ());
        }
    }

    public static void clear() {
        for (Display.ItemDisplay d : heads.values()) {
            if (d != null && !d.isRemoved()) d.discard();
        }
        heads.clear();
    }

    private static void spawnHead(ServerPlayer sp, BangPlayer bp) {
        ServerLevel level = sp.serverLevel();
        Item item = BangItems.charItemFor(bp.character);
        if (item == null) return;

        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(sp.getX()));
        pos.add(DoubleTag.valueOf(sp.getY() + HEIGHT));
        pos.add(DoubleTag.valueOf(sp.getZ()));
        tag.put("Pos", pos);
        tag.put("item", new ItemStack(item).save(level.registryAccess()));
        CompoundTag tr = new CompoundTag();
        tr.put("translation", floatList(0f, 0f, 0f));
        tr.put("scale", floatList(SCALE, SCALE, SCALE));
        tr.put("left_rotation", floatList(0f, 0f, 0f, 1f));
        tr.put("right_rotation", floatList(0f, 0f, 0f, 1f));
        tag.put("transformation", tr);
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 4.0f);
        d.load(tag);
        level.addFreshEntity(d);
        heads.put(bp.id, d);
    }

    private static ListTag floatList(float... vals) {
        ListTag l = new ListTag();
        for (float v : vals) l.add(FloatTag.valueOf(v));
        return l;
    }
}
