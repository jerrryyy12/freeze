package com.chameleon;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 술래의 위장 탐지총. (임시 텍스처 = 블레이즈 막대 모델) */
public class ChameleonItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ChameleonMod.MOD_ID);

    public static final RegistryObject<Item> GUN =
            ITEMS.register("gun", () -> new Item(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
