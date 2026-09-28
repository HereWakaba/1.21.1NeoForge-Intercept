package com.ryjs.intercept.client.effect;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ryjs.intercept.Intercept;
import com.ryjs.intercept.client.InterceptRenderTypes;
import com.ryjs.intercept.client.InterceptShaders;
import com.ryjs.intercept.init.InterceptItems;
import com.ryjs.intercept.util.TaiChiName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.awt.Color;


public final class TaiChiTooltipRenderer {

    private static final String[] DESC = {
            " 伪·太极终焉 ",
            " 阴不再生阳 阳不再化阴 ",
            " 在时间与空间的尽头 阴与阳终于停止无止境的推手 ",
            " 一生二 二生三 三生万物 而今 万象自三归二 自二归一 直至返归那一未分的静默 ",
            " 太极无极 ",
            " Belongs to 若叶姬色 ",
    };
    private static final long TYPE_MS = 900L;
    private static final long HOLD_MS = 5000L;
    private static final long FADE_MS = 2000L;
    private static final ResourceLocation DESC_FONT = Intercept.rl("reflection");

    private static long descLastRenderMs = 0L;
    private static long descSessionStart = 0L;
    private static int descStartLine = 0;

    private static float fadeAlpha = 0f;

    private static final long TIME_ANCHOR_MS = System.currentTimeMillis();

    private TaiChiTooltipRenderer() {}

    public static boolean isTaiChi(ItemStack stack) {
        return stack.is(InterceptItems.END_OF_TAI_CHI);
    }


    public static void renderCustomTooltip(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!isTaiChi(stack)) return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        graphics.flush();


        long now = System.currentTimeMillis();
        if (now - descLastRenderMs > 200L) {
            fadeAlpha = 0f;
            descSessionStart = now;
            descStartLine = (int) (Math.random() * DESC.length);
        }

        fadeAlpha += (1f - fadeAlpha) * 0.15f;
        if (fadeAlpha > 0.99f) fadeAlpha = 1f;
        int fadeA = (int) (255 * fadeAlpha);

        renderStarNest(graphics, mc, screenWidth, screenHeight, fadeA);
        renderTaiChiDisc(graphics, mc, screenWidth, screenHeight, fadeA);

        TaiChiCubeRenderer.render(graphics, screenWidth, screenHeight);
        renderSpinningSword(graphics, stack, screenWidth, screenHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 2000.0F);
        renderTitle(graphics, font, screenWidth, screenHeight);
        renderTypewriterDesc(graphics, font, screenWidth, screenHeight);
        graphics.pose().popPose();
    }

    private static void renderStarNest(GuiGraphics graphics, Minecraft mc,
                                       int screenWidth, int screenHeight, int fadeA) {
        if (InterceptShaders.starNestShader == null) return;

        Window window = mc.getWindow();
        double guiScale = window.getGuiScale();
        float physW = (float) (screenWidth * guiScale);
        float physH = (float) (screenHeight * guiScale);

        ShaderInstance nest = InterceptShaders.starNestShader;
        if (nest.getUniform("time") != null)
            nest.safeGetUniform("time").set((System.currentTimeMillis() - TIME_ANCHOR_MS) / 1000.0F);
        if (nest.getUniform("screenSize") != null)
            nest.safeGetUniform("screenSize").set(physW, physH);

        drawScreenQuad(graphics, InterceptRenderTypes.STAR_NEST_TOOLTIP, 1000.0F, screenWidth, screenHeight, fadeA);
    }

    private static void renderTaiChiDisc(GuiGraphics graphics, Minecraft mc,
                                         int screenWidth, int screenHeight, int fadeA) {
        ShaderInstance shader = InterceptShaders.taichiTooltipShader;
        if (shader == null) return;

        Window window = mc.getWindow();
        double guiScale = window.getGuiScale();
        float physW = (float) (screenWidth * guiScale);
        float physH = (float) (screenHeight * guiScale);

        if (shader.getUniform("time") != null) {
            shader.safeGetUniform("time").set((float) ((System.currentTimeMillis() / 1000.0) % (Math.PI * 2.0)));
        }
        if (shader.getUniform("screenSize") != null)
            shader.safeGetUniform("screenSize").set(physW, physH);
        if (shader.getUniform("yaw") != null)
            shader.safeGetUniform("yaw").set(0.0F);
        if (shader.getUniform("pitch") != null)
            shader.safeGetUniform("pitch").set(0.0F);

        drawScreenQuad(graphics, InterceptRenderTypes.TAICHI_TOOLTIP, 1900.0F, screenWidth, screenHeight, fadeA);
    }

    private static void drawScreenQuad(GuiGraphics graphics, RenderType renderType, float z,
                                       int screenWidth, int screenHeight, int fadeA) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bufferSource.getBuffer(renderType);
        Matrix4f matrix = graphics.pose().last().pose();

        vc.addVertex(matrix, 0.0F, (float) screenHeight, z).setColor(255, 255, 255, fadeA);
        vc.addVertex(matrix, (float) screenWidth, (float) screenHeight, z).setColor(255, 255, 255, fadeA);
        vc.addVertex(matrix, (float) screenWidth, 0.0F, z).setColor(255, 255, 255, fadeA);
        vc.addVertex(matrix, 0.0F, 0.0F, z).setColor(255, 255, 255, fadeA);

        bufferSource.endBatch(renderType);
    }


    private static void renderTitle(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        String str = TaiChiName.currentText(now);
        int total = str.length();
        int totalWidth = 0;
        for (int i = 0; i < total; i++) {
            totalWidth += font.width(String.valueOf(str.charAt(i)));
        }
        float centerY = screenHeight * 0.18f;
        float penX = (screenWidth - totalWidth) / 2.0f;
        for (int i = 0; i < total; i++) {
            String ch = String.valueOf(str.charAt(i));
            int adv = font.width(ch);
            float s = 1.0f + 0.35f * (float) Math.sin(now * 0.006 + i * 0.9);
            int color = TaiChiName.charColor(i, total, now);
            graphics.pose().pushPose();
            graphics.pose().translate(penX + adv / 2.0f, centerY, 0.0F);
            graphics.pose().scale(s, s, 1.0F);
            int ox = -adv / 2;
            int oy = -font.lineHeight / 2;

            int outline = 0xFF000000;
            graphics.drawString(font, ch, ox - 1, oy, outline, false);
            graphics.drawString(font, ch, ox + 1, oy, outline, false);
            graphics.drawString(font, ch, ox, oy - 1, outline, false);
            graphics.drawString(font, ch, ox, oy + 1, outline, false);

            graphics.drawString(font, ch, ox, oy, color, false);
            graphics.pose().popPose();
            penX += adv;
        }
    }


    private static void renderTypewriterDesc(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (DESC.length == 0) return;
        long now = System.currentTimeMillis();
        descLastRenderMs = now;

        long cycle = TYPE_MS + HOLD_MS + FADE_MS;
        long elapsed = now - descSessionStart;
        int lineIndex = (int) ((descStartLine + elapsed / cycle) % DESC.length);
        long t = elapsed % cycle;
        String line = DESC[lineIndex];

        float reveal;
        float alpha;
        if (t < TYPE_MS) {
            reveal = t / (float) TYPE_MS;
            alpha = reveal;
        } else if (t < TYPE_MS + HOLD_MS) {
            reveal = 1f;
            alpha = 1f;
        } else {
            reveal = 1f;
            alpha = 1f - (t - TYPE_MS - HOLD_MS) / (float) FADE_MS;
        }
        if (alpha <= 0.02f) return;

        int len = line.length();
        float center = (len - 1) / 2.0f;
        float maxDist = Math.max(center, 0.0001f);

        Component[] glyphs = new Component[len];
        int[] adv = new int[len];
        int totalWidth = 0;
        for (int i = 0; i < len; i++) {
            glyphs[i] = Component.literal(String.valueOf(line.charAt(i)))
                    .setStyle(Style.EMPTY.withFont(DESC_FONT));
            adv[i] = font.width(glyphs[i]);
            totalWidth += adv[i];
        }

        float penX = (screenWidth - totalWidth) / 2.0f;
        float baseY = screenHeight - 42f;
        final float band = 0.28f;
        float front = reveal * (1f + band);
        float ts = elapsed / 1000.0f;

        for (int i = 0; i < len; i++) {
            float dist = Math.abs(i - center) / maxDist;
            float charReveal = Math.max(0f, Math.min(1f, (front - dist) / band));
            float ca = alpha * charReveal;
            int a8 = (int) (ca * 255f);
            if (a8 >= 6) {
                float hue = (((ts * 50f) + i * 20f) % 360f) / 360f;
                int rgb = Color.HSBtoRGB(hue, 0.55f, 1.0f) & 0xFFFFFF;
                int col = (a8 << 24) | rgb;
                int outline = a8 << 24;

                float wave = (float) (Math.sin(ts * 4.0 + i * 0.6) * 1.6);
                graphics.pose().pushPose();
                graphics.pose().translate(penX, baseY + wave, 0f);

                graphics.drawString(font, glyphs[i], -1, 0, outline, false);
                graphics.drawString(font, glyphs[i], 1, 0, outline, false);
                graphics.drawString(font, glyphs[i], 0, -1, outline, false);
                graphics.drawString(font, glyphs[i], 0, 1, outline, false);

                graphics.drawString(font, glyphs[i], 0, 0, col, false);
                graphics.drawString(font, glyphs[i], 1, 0, col, false);
                graphics.pose().popPose();
            }
            penX += adv[i];
        }
    }


    private static void renderSpinningSword(GuiGraphics graphics, ItemStack stack, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float angle = (now % 4000L) / 4000.0f * 360.0f;
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;
        float size = Math.min(screenWidth, screenHeight) * 0.55f;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 2460.0F);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.YP.rotationDegrees(angle));

        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        com.ryjs.intercept.client.model.CosmicBakeModel.SUPPRESS_BACK_TAICHI = true;
        try {
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
            buffers.endBatch();
        } finally {
            com.ryjs.intercept.client.model.CosmicBakeModel.SUPPRESS_BACK_TAICHI = false;
        }
        Lighting.setupForFlatItems();
        pose.popPose();
    }
}
