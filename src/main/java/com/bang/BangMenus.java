package com.bang;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 손패 GUI 메뉴 타입 등록. */
public class BangMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, BangMod.MOD_ID);

    public static final RegistryObject<MenuType<BangHandMenu>> HAND =
            MENUS.register("hand", () -> new MenuType<>(BangHandMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
