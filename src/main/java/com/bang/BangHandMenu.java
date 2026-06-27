package com.bang;

import com.bang.game.BangGame;
import com.bang.game.BangPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 손패를 카드 아이템으로 보여주는 메뉴. 슬롯 클릭 시 그 카드를 사용. */
public class BangHandMenu extends AbstractContainerMenu {

    public static final int CARD_SLOTS = 27; // 3줄 x 9칸
    private final Container cards;
    private final Player owner;

    /** 클라이언트 측 생성자 (MenuType 팩토리) */
    public BangHandMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(CARD_SLOTS), inv.player);
    }

    /** 서버 측 생성자 */
    public BangHandMenu(int id, Inventory inv, Container cards, Player owner) {
        super(BangMenus.HAND.get(), id);
        this.cards = cards;
        this.owner = owner;
        for (int i = 0; i < CARD_SLOTS; i++) {
            int sx = 8 + (i % 9) * 18;
            int sy = 18 + (i / 9) * 18;
            addSlot(new Slot(cards, i, sx, sy));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CARD_SLOTS) {
            if (cards.getItem(slotId).isEmpty()) return;
            if (owner instanceof ServerPlayer sp && sp.getServer() != null) {
                BangGame g = BangMod.game;
                if (g != null && g.isPlaying()) {
                    BangPlayer me = g.get(sp.getUUID());
                    if (me != null) {
                        g.playCard(sp.getServer(), me, slotId, null);
                        refresh(me);
                    }
                }
            }
            return; // 아이템 이동 금지
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** 손패 변화 후 슬롯 갱신 */
    public void refresh(BangPlayer me) {
        for (int i = 0; i < CARD_SLOTS; i++) {
            if (i < me.hand.size()) {
                cards.setItem(i, new ItemStack(BangItems.itemFor(me.hand.get(i).type)));
            } else {
                cards.setItem(i, ItemStack.EMPTY);
            }
        }
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
