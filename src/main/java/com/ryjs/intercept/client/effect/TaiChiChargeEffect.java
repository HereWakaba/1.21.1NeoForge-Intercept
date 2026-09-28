package com.ryjs.intercept.client.effect;

import com.ryjs.intercept.Intercept;
import com.ryjs.intercept.init.InterceptItems;
import com.ryjs.intercept.util.timestop.TimeStopState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;


public final class TaiChiChargeEffect {


    private static final float MAX_CHARGE_TICKS = 200f;
    private static final ResourceLocation EFFECT = Intercept.rl("shaders/post/taichi_charge.json");
    private static final String EFFECT_NAME = EFFECT.toString();

    private static float chargeI = 0f;
    private static float invertI = 0f;
    private static float timeStopI = 0f;
    private static float effectTime = 0f;
    private static long lastMs = 0L;
    private static boolean fullPause = false;

    private TaiChiChargeEffect() {}


    public static void setFullPause(boolean value) {
        fullPause = value;
    }

    public static boolean isFullPause() {
        return fullPause;
    }


    public static void onRenderLevelFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (currentOurs(mc) != null) mc.gameRenderer.shutdownEffect();
            chargeI = 0f;
            invertI = 0f;
            timeStopI = 0f;
            lastMs = 0L;
            return;
        }

        boolean pausing = fullPause;
        float charge = chargeProgress(mc.player);

        float chargeTarget = pausing ? 0f : charge;
        if (chargeTarget >= chargeI) {
            chargeI = chargeTarget;
        } else {
            chargeI += (chargeTarget - chargeI) * 0.1f;
            if (chargeI < 0.01f) chargeI = 0f;
        }

        float invertTarget = pausing ? 1f : 0f;
        invertI += (invertTarget - invertI) * 0.1f;
        if (invertTarget == 0f && invertI < 0.01f) invertI = 0f;



        float tsTarget = TimeStopState.enabled ? 1f : 0f;
        timeStopI += (tsTarget - timeStopI) * 0.14f;
        if (tsTarget == 0f && timeStopI < 0.01f) timeStopI = 0f;

        long now = System.currentTimeMillis();
        float dt = lastMs == 0L ? 0f : Math.min(0.1f, (now - lastMs) / 1000.0f);
        lastMs = now;
        effectTime += dt * (1.0f + randSpeed(now) * 4.0f * chargeI);

        boolean active = Math.max(Math.max(chargeI, invertI), timeStopI) > 0.01f;
        try {
            if (active) {
                if (currentOurs(mc) == null && mc.gameRenderer.currentEffect() == null) {
                    mc.gameRenderer.loadEffect(EFFECT);
                }
                PostChain pe = currentOurs(mc);
                if (pe != null) {
                    pe.setUniform("ChargeProgress", chargeI);
                    pe.setUniform("InvertAmount", invertI);
                    pe.setUniform("TimeStopAmount", timeStopI);
                    pe.setUniform("EffectTime", effectTime);
                }
            } else if (currentOurs(mc) != null) {
                mc.gameRenderer.shutdownEffect();
            }
        } catch (Exception ignored) {
        }
    }

    private static PostChain currentOurs(Minecraft mc) {
        PostChain pe = mc.gameRenderer.currentEffect();
        return (pe != null && EFFECT_NAME.equals(pe.getName())) ? pe : null;
    }

    private static float randSpeed(long ms) {
        long q = ms / 150L;
        long h = q * 6364136223846793005L + 1442695040888963407L;
        h ^= (h >>> 33);
        return (h & 0xFFFF) / 65535.0f;
    }

    private static float chargeProgress(Player p) {
        if (p != null && p.isUsingItem()
                && p.getUseItem().is(InterceptItems.END_OF_TAI_CHI.get())) {
            return Math.min(1f, p.getTicksUsingItem() / MAX_CHARGE_TICKS);
        }
        return 0f;
    }
}
