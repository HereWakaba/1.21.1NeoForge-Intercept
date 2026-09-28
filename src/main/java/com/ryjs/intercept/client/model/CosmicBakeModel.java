package com.ryjs.intercept.client.model;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ryjs.intercept.Intercept;
import com.ryjs.intercept.client.InterceptRenderTypes;
import com.ryjs.intercept.client.InterceptShaders;
import com.ryjs.intercept.client.shader.InterceptCosmicShader;
import com.ryjs.intercept.init.InterceptItems;
import com.ryjs.intercept.util.TransformUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;


public final class CosmicBakeModel extends WrappedItemModel {


    public static boolean SUPPRESS_BACK_TAICHI = false;

    private final List<ResourceLocation> maskSprites;

    public CosmicBakeModel(BakedModel wrapped, List<ResourceLocation> maskSprites) {
        super(wrapped);
        this.maskSprites = maskSprites;
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public boolean isCosmic() {
        return true;
    }

    @Override
    @Nullable
    public PerspectiveModelState getModelState() {
        return (PerspectiveModelState) this.parentState;
    }

    public void renderItem(ItemStack stack, ItemDisplayContext context, PoseStack pStack,
                           MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (stack.is(InterceptItems.END_OF_TAI_CHI)) {
            this.parentState = TransformUtils.DEFAULT_TOOL;
        }

        if (stack.is(InterceptItems.END_OF_TAI_CHI)
                && !SUPPRESS_BACK_TAICHI
                && (context == ItemDisplayContext.GUI || context == ItemDisplayContext.FIXED)) {
            renderBackTaichi(pStack, buffers);
        }


        flush(buffers);

        renderWrapped(stack, pStack, buffers, packedLight, packedOverlay);
        flush(buffers);

        Minecraft mc = Minecraft.getInstance();
        List<TextureAtlasSprite> sprites = new ArrayList<>(this.maskSprites.size());
        for (ResourceLocation location : this.maskSprites) {
            sprites.add(mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(location));
        }
        List<BakedQuad> quads = bakeItem(sprites);

        VertexConsumer consumer = buffers.getBuffer(InterceptCosmicShader.COSMIC_RENDER_TYPE);
        try {
            applyCosmicUniforms(mc);
            mc.getItemRenderer().renderQuadList(pStack, consumer, quads, stack, packedLight, packedOverlay);
        } catch (Throwable t) {
            Intercept.getLogger().warn("cosmic 面片写入失败", t);
        }

        if (buffers instanceof MultiBufferSource.BufferSource bs) {
            try {
                bs.endBatch(InterceptCosmicShader.COSMIC_RENDER_TYPE);
            } catch (Throwable ignored) {
            }
        }
    }


    private static void flush(MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource bs) {
            try {
                bs.endBatch();
            } catch (Throwable ignored) {
            }
        }
    }


    private static void applyCosmicUniforms(Minecraft mc) {
        ShaderInstance shader = InterceptShaders.cosmicShader;
        if (shader == null) {
            return;
        }

        shader.safeGetUniform("time").set((System.currentTimeMillis() % 3_600_000L) / 1000.0F);
        Window window = mc.getWindow();
        shader.safeGetUniform("screenSize")
                .set((float) window.getWidth(), (float) window.getHeight());
    }


    private static void renderBackTaichi(PoseStack pStack, MultiBufferSource buffers) {
        try {
            ShaderInstance shader = InterceptShaders.taichiWorldShader;
            if (shader == null) {
                return;
            }
            if (shader.getUniform("time") != null) {
                shader.safeGetUniform("time")
                        .set((float) ((System.currentTimeMillis() / 1000.0 * 0.8) % (Math.PI * 2.0)));
            }
            MultiBufferSource.BufferSource bs = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer vc = bs.getBuffer(InterceptRenderTypes.BACK_TAICHI);
            Matrix4f mat = pStack.last().pose();
            float cx = 0.5F, cy = 0.5F, h = 0.585F, z = 0.5F;
            int a = 200;
            vc.addVertex(mat, cx - h, cy - h, z).setUv(0F, 0F).setColor(255, 255, 255, a);
            vc.addVertex(mat, cx - h, cy + h, z).setUv(0F, 1F).setColor(255, 255, 255, a);
            vc.addVertex(mat, cx + h, cy + h, z).setUv(1F, 1F).setColor(255, 255, 255, a);
            vc.addVertex(mat, cx + h, cy - h, z).setUv(1F, 0F).setColor(255, 255, 255, a);
            vc.addVertex(mat, cx - h, cy - h, z).setUv(0F, 0F).setColor(255, 255, 255, a);
            vc.addVertex(mat, cx + h, cy - h, z).setUv(1F, 0F).setColor(255, 255, 255, a);
            vc.addVertex(mat, cx + h, cy + h, z).setUv(1F, 1F).setColor(255, 255, 255, a);
            vc.addVertex(mat, cx - h, cy + h, z).setUv(0F, 1F).setColor(255, 255, 255, a);
            bs.endBatch(InterceptRenderTypes.BACK_TAICHI);
        } catch (Throwable ignored) {
        }
    }
}
