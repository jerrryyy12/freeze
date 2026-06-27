package com.bang.game;

import com.bang.BangItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 손패를 플레이어 인벤토리에 카드 아이템으로 동기화하고, 원래 인벤은 보관/복원한다. */
public class BangInventory {

    private static final Map<UUID, List<ItemStack>> saved = new HashMap<>();
    private static final Map<UUID, GameType> savedMode = new HashMap<>();

    /** 게임 시작: 원래 인벤 저장 → 비우고 카드로 채움 */
    public static void beginGame(MinecraftServer server, BangGame game) {
        for (BangPlayer bp : game.seating()) {
            if (bp.isBot) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(bp.id);
            if (sp == null) continue;
            Inventory inv = sp.getInventory();
            List<ItemStack> backup = new ArrayList<>();
            for (int i = 0; i < inv.getContainerSize(); i++) backup.add(inv.getItem(i).copy());
            saved.put(bp.id, backup);
            savedMode.put(bp.id, sp.gameMode.getGameModeForPlayer());
            inv.clearContent();
            syncPlayer(sp, bp);
        }
    }

    /** 모든 사람 플레이어의 손패를 인벤에 다시 반영 */
    public static void syncAll(MinecraftServer server, BangGame game) {
        if (game == null || !game.isPlaying()) return;
        for (BangPlayer bp : game.seating()) {
            if (bp.isBot) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(bp.id);
            if (sp != null && saved.containsKey(bp.id)) syncPlayer(sp, bp);
        }
    }

    private static void syncPlayer(ServerPlayer sp, BangPlayer bp) {
        Inventory inv = sp.getInventory();
        inv.clearContent();
        for (int i = 0; i < bp.hand.size() && i < 36; i++) {
            Item it = BangItems.itemFor(bp.hand.get(i).type);
            if (it != null) inv.setItem(i, new ItemStack(it));
        }
        sp.inventoryMenu.broadcastChanges();
        // 하트 = BANG 목숨 (총알 1 = 하트 1), 게임 중 마크 데미지 무시
        AttributeInstance maxH = sp.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(Math.max(2, bp.maxHp * 2));
        if (bp.alive) sp.setHealth(Math.max(1f, bp.hp * 2f));
        sp.setInvulnerable(true);
    }

    /** 게임 종료: 카드 제거 후 원래 인벤 복원 */
    public static void endGame(MinecraftServer server, BangGame game) {
        for (BangPlayer bp : game.seating()) {
            if (bp.isBot) continue;
            restore(server, bp.id);
        }
        saved.clear();
        savedMode.clear();
    }

    public static void restore(MinecraftServer server, UUID id) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        List<ItemStack> backup = saved.get(id);
        if (sp == null) return;
        // 체력 원복
        AttributeInstance maxH = sp.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(20.0);
        sp.setHealth(20.0f);
        sp.setInvulnerable(false);
        // 게임모드 원복
        GameType mode = savedMode.get(id);
        if (mode != null) sp.setGameMode(mode);
        // 인벤 원복
        Inventory inv = sp.getInventory();
        inv.clearContent();
        if (backup != null) {
            for (int i = 0; i < backup.size() && i < inv.getContainerSize(); i++) {
                inv.setItem(i, backup.get(i));
            }
        }
        sp.inventoryMenu.broadcastChanges();
    }

    /** 플레이어가 바라보는 게임 참가자(있으면) */
    public static BangPlayer lookTarget(ServerPlayer sp, BangGame game) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0f);
        double reach = 40.0;
        Vec3 end = eye.add(look.scale(reach));
        AABB box = sp.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                sp, eye, end, box,
                e -> e instanceof ServerPlayer && e != sp,
                reach * reach);
        if (hit != null && hit.getEntity() instanceof ServerPlayer tp) {
            return game.get(tp.getUUID());
        }
        return null;
    }
}
