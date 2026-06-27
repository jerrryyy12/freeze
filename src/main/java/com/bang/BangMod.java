package com.bang;

import com.bang.game.BangGame;
import com.bang.game.BangHeads;
import com.bang.game.BangInventory;
import com.bang.game.BangPlayer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BangMod.MOD_ID)
public class BangMod {
    public static final String MOD_ID = "bang";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 현재 진행 중인 게임 (서버당 1판). */
    public static BangGame game;

    public BangMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BangItems.register(modBus);
        BangMenus.register(modBus);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Bang 모드 로드 완료");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BangCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (game != null && game.isPlaying()) {
            BangHeads.tick(event.getServer(), game);
        }
    }

    /** 카드 아이템 우클릭(허공) = 시선 대상에게 사용 */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) return;
        if (game == null || !game.isPlaying()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!BangItems.isCard(event.getItemStack().getItem())) return;
        event.setCanceled(true);
        playHeld(sp, BangInventory.lookTarget(sp, game));
    }

    /** 카드 들고 다른 플레이어 우클릭 = 그 대상에게 사용 */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (game == null || !game.isPlaying()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!BangItems.isCard(event.getItemStack().getItem())) return;
        event.setCanceled(true);
        BangPlayer target = event.getTarget() instanceof ServerPlayer tp ? game.get(tp.getUUID()) : null;
        playHeld(sp, target);
    }

    private void playHeld(ServerPlayer sp, BangPlayer target) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        BangPlayer me = game.get(sp.getUUID());
        if (me == null) return;
        int idx = sp.getInventory().selected;
        game.playCard(server, me, idx, target != null ? target.name : null);
        BangInventory.syncAll(server, game);
    }

    /** 게임 중 카드 아이템 드롭 방지 */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (game == null || !game.isPlaying()) return;
        if (event.getPlayer() instanceof ServerPlayer sp && game.get(sp.getUUID()) != null
                && BangItems.isCard(event.getEntity().getItem().getItem())) {
            event.setCanceled(true);
            MinecraftServer server = sp.getServer();
            if (server != null) BangInventory.syncAll(server, game);
        }
    }
}
