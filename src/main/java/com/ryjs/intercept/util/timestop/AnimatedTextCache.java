package com.ryjs.intercept.util.timestop;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class AnimatedTextCache {

    private static final Map<ItemStack, Component> NAMES =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ItemStack, List<Component>> TOOLTIPS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private AnimatedTextCache() {
    }

    private static boolean exempt(ItemStack stack) {
        return stack != null && stack.getItem() instanceof TimestopTextExempt;
    }

    public static Component peekName(ItemStack stack) {
        if (exempt(stack)) {
            return null;
        }
        return stack == null ? null : NAMES.get(stack);
    }

    public static void storeName(ItemStack stack, Component name) {
        if (!exempt(stack) && stack != null && name != null) {
            NAMES.putIfAbsent(stack, name);
        }
    }

    public static List<Component> peekTooltip(ItemStack stack) {
        if (exempt(stack)) {
            return null;
        }
        List<Component> cached = stack == null ? null : TOOLTIPS.get(stack);
        return cached == null ? null : new ArrayList<>(cached);
    }

    public static void storeTooltip(ItemStack stack, List<Component> lines) {
        if (!exempt(stack) && stack != null && lines != null) {
            TOOLTIPS.putIfAbsent(stack, new ArrayList<>(lines));
        }
    }

    public static void clear() {
        NAMES.clear();
        TOOLTIPS.clear();
    }
}
