package com.bang.game;

import com.bang.BangItems;
import com.mojang.math.Transformation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 전용 맵의 테이블 위에 공용 카드(뽑는 더미·버린 더미·좌석별 장비)를
 * 디스플레이 엔티티(눕힌 카드)로 표시한다.
 */
public class BangTable {

    private static ResourceKey<Level> dim;
    private static double ax, ay, az;
    private static boolean set = false;

    private static final List<Display.ItemDisplay> spawned = new ArrayList<>();

    private static final float CARD_SCALE = 0.5f;
    private static final double SEAT_RADIUS = 2.0;

    public static boolean isSet() { return set; }

    /** 플레이어가 선 위치를 테이블 중심으로 등록 (테이블 윗면 위에서 실행 권장) */
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

    /** 현재 게임 상태를 테이블에 다시 그린다. */
    public static void render(MinecraftServer server, BangGame game) {
        if (!set || dim == null) return;
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;
        clear(server);
        if (game == null || !game.isPlaying()) return;

        // 뽑는 더미(뒷면) / 버린 더미(맨 위)
        spawnCard(level, BangItems.CARD_BACK.get(), ax - 0.6, ay, az);
        Card top = game.deck() != null ? game.deck().peekTopDiscard() : null;
        if (top != null) spawnCard(level, BangItems.itemFor(top.type), ax + 0.6, ay, az);

        // 좌석별 장비를 원형으로 배치
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
        d.setPos(x, y, z);
        d.setItemStack(new ItemStack(item));
        // 카드를 평평하게 눕힘 (X축 -90도) + 축소
        Quaternionf flat = new Quaternionf().rotationX((float) (-Math.PI / 2));
        d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f), flat,
                new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE), new Quaternionf()));
        d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        d.setViewRange(2.0f);
        level.addFreshEntity(d);
        spawned.add(d);
    }
}
