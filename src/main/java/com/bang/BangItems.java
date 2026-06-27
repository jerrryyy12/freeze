package com.bang;

import com.bang.game.CardType;
import com.bang.game.CharacterCard;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** 카드별 아이템 등록 (임시 텍스처). */
public class BangItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BangMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BangMod.MOD_ID);

    public static final Map<CardType, RegistryObject<Item>> CARDS = new EnumMap<>(CardType.class);
    public static final Map<CharacterCard, RegistryObject<Item>> CHARS = new EnumMap<>(CharacterCard.class);
    public static final RegistryObject<Item> CARD_BACK =
            ITEMS.register("card_back", () -> new Item(new Item.Properties()));

    static {
        for (CardType t : CardType.values()) {
            String name = "card_" + t.name().toLowerCase();
            CARDS.put(t, ITEMS.register(name, () -> new Item(new Item.Properties())));
        }
        for (CharacterCard ch : CharacterCard.values()) {
            String name = "char_" + ch.name().toLowerCase();
            CHARS.put(ch, ITEMS.register(name, () -> new Item(new Item.Properties())));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("bang_cards", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("BANG! 카드"))
                    .icon(() -> new ItemStack(CARDS.get(CardType.BANG).get()))
                    .displayItems((params, output) -> {
                        for (RegistryObject<Item> ro : CARDS.values()) output.accept(ro.get());
                        output.accept(CARD_BACK.get());
                        for (RegistryObject<Item> ro : CHARS.values()) output.accept(ro.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** 카드 종류에 해당하는 아이템 */
    public static Item itemFor(CardType type) {
        RegistryObject<Item> ro = CARDS.get(type);
        return ro == null ? null : ro.get();
    }

    /** 캐릭터에 해당하는 아이템 */
    public static Item charItemFor(CharacterCard ch) {
        RegistryObject<Item> ro = CHARS.get(ch);
        return ro == null ? null : ro.get();
    }
}
