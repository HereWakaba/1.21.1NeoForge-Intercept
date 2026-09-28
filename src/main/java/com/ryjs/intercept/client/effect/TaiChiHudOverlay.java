package com.ryjs.intercept.client.effect;

import com.ryjs.intercept.init.InterceptItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;


public final class TaiChiHudOverlay {

    private static final String TEXT = "-太极终焉-";

    private TaiChiHudOverlay() {}


    public static void onGuiRender(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;
        if (!hasSword(player)) return;

        long now = System.currentTimeMillis();
        boolean flicker = isFlickerHidden(now);

        Font font = mc.font;
        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();

        int totalW = 0;
        for (int i = 0; i < TEXT.length(); i++) {
            totalW += font.width(String.valueOf(TEXT.charAt(i)));
        }

        float penX = (sw - totalW) / 2.0f;
        int baseY = sh - 32;

        for (int i = 0; i < TEXT.length(); i++) {
            char cc = TEXT.charAt(i);
            String ch = String.valueOf(cc);
            int adv = font.width(ch);
            boolean isDash = (cc == '-');

            if (!isDash && flicker) {
                penX += adv;
                continue;
            }
            int dx = isDash ? 0 : twitch(now, i, 11L);
            int dy = isDash ? 0 : twitch(now, i, 37L);
            int x = (int) penX + dx;
            int yy = baseY + dy;
            int color = isDash ? 0xFFFFFFFF : charColor(now, i);

            graphics.drawString(font, ch, x - 1, yy, 0xFF000000, false);
            graphics.drawString(font, ch, x + 1, yy, 0xFF000000, false);
            graphics.drawString(font, ch, x, yy - 1, 0xFF000000, false);
            graphics.drawString(font, ch, x, yy + 1, 0xFF000000, false);

            graphics.drawString(font, ch, x, yy, color, false);
            graphics.drawString(font, ch, x + 1, yy, color, false);
            penX += adv;
        }
    }

    private static boolean hasSword(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(InterceptItems.END_OF_TAI_CHI)) {
                return true;
            }
        }
        return false;
    }

    private static long hash(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x & 0x7fffffffffffffffL;
    }


    private static boolean isFlickerHidden(long ms) {
        long window = 500L;
        long cycle = ms / window;
        if (hash(cycle * 2654435761L) % 100L < 40L) {
            return (ms % window) < 50L;
        }
        return false;
    }


    private static int twitch(long ms, int i, long salt) {
        long h = hash((ms / 60L) * 131L + i * 977L + salt);
        return (int) (h % 5L) - 2;
    }


    private static int charColor(long ms, int i) {
        if (hash((ms / 60L) * 17L + i * 101L) % 100L < 10L) {
            return 0xFFFFFFFF;
        }
        float hue = (((ms * 0.22f) + i * 68f) % 360f) / 360f;
        int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF;
        return 0xFF000000 | rgb;
    }
}
