package com.bang.game;

import com.bang.BangItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 전용 맵 테이블 위에 공용 카드(뽑는 더미·버린 더미·좌석별 장비)를
 * 디스플레이 엔티티(눕힌 카드)로 표시한다.
 * 디스플레이 엔티티의 setter는 public이 아니라 NBT(load)로 설정한다.
 */
public class BangTable {

    private static ResourceKey<Level> dim;
    private static double ax, ay, az;
    private static boolean set = false;

    private static final List<Display.ItemDisplay> spawned = new ArrayList<>();

    private static final float CARD_SCALE = 0.5f;
    private static final double SEAT_RADIUS = 2.0;
    // X축 -90도 회전 쿼터니언 (카드를 평평하게 눕힘)
    private static final float[] FLAT_ROT = {-0.70710677f, 0f, 0f, 0.70710677f};

    public static boolean isSet() { return set; }

    public static void setAnchor(ServerPlayer p) {
        dim = p.serverLevel().dimension();
        ax = p.getX();
        ay = p.getY();
        az = p.getZ();
        set = true;
    }

    public static void clear(MinecraftServer server) {
        for (Display.ItemDisplay e : spawned) {
            if (e != null && !e.isRemoved()) e.discard();
        }
        spawned.clear();
    }

    public static void render(MinecraftServer server, BangGame game) {
        if (!set || dim == null) return;
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;
        clear(server);
        if (game == null || !game.isPlaying()) return;

        spawnCard(level, BangItems.CARD_BACK.get(), ax - 0.6, ay, az);
        Card top = game.deck() != null ? game.deck().peekTopDiscard() : null;
        if (top != null) spawnCard(level, BangItems.itemFor(top.type), ax + 0.6, ay, az);

        List<BangPlayer> seats = game.seating();
        int n = seats.size();
        for (int i = 0; i < n; i++) {
            BangPlayer bp = seats.get(i);
            if (!bp.alive) continue;
            double ang = 2 * Math.PI * i / Math.max(1, n);
            double sx = ax + Math.cos(ang) * SEAT_RADIUS;
            double sz = az + Math.sin(ang) * SEAT_RADIUS;
            for (int j = 0; j < bp.equipment.size(); j++) {
                Item it = BangItems.itemFor(bp.equipment.get(j).type);
                if (it != null) spawnCard(level, it, sx + j * 0.4, ay, sz);
            }
        }
    }

    private static void spawnCard(ServerLevel level, Item item, double x, double y, double z) {
        if (item == null) return;
        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);

        CompoundTag tag = new CompoundTag();
        // 위치
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        tag.put("Pos", pos);
        // 아이템
        Tag itemTag = new ItemStack(item).save(level.registryAccess());
        tag.put("item", itemTag);
        tag.putString("item_display", "fixed");
        // 변형: 평평하게 눕히고 축소
        CompoundTag tr = new CompoundTag();
        tr.put("translation", floatList(0f, 0f, 0f));
        tr.put("scale", floatList(CARD_SCALE, CARD_SCALE, CARD_SCALE));
        tr.put("left_rotation", floatList(FLAT_ROT));
        tr.put("right_rotation", floatList(0f, 0f, 0f, 1f));
        tag.put("transformation", tr);
        tag.putString("billboard", "fixed");
        tag.putFloat("view_range", 2.0f);

        d.load(tag);
        level.addFreshEntity(d);
        spawned.add(d);
    }

    private static ListTag floatList(float... vals) {
        ListTag l = new ListTag();
        for (float v : vals) l.add(FloatTag.valueOf(v));
        return l;
    }
}
