package com.ryjs.intercept.init;

import com.ryjs.intercept.Intercept;
import com.ryjs.intercept.item.EndOfTaiChi;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class InterceptItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Intercept.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Intercept.MODID);
    public static final DeferredItem<Item> END_OF_TAI_CHI =
            ITEMS.register("end_of_taichi", EndOfTaiChi::new);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INTERCEPT_TAB = TABS.register("intercept", () ->
            CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup." + Intercept.MODID))
                    .icon(() -> END_OF_TAI_CHI.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(END_OF_TAI_CHI.get()))
                    .build());

    private InterceptItems() {}


    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
