package com.ryjs.intercept.util.kp.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;

import static com.ryjs.intercept.util.kp.EntityUtil.shouldDeath;

public class FakeEntityRenderDispatcher extends EntityRenderDispatcher {
    public FakeEntityRenderDispatcher(Minecraft client,
                                      TextureManager textureManager,
                                      ItemRenderer itemRenderer,
                                      BlockRenderDispatcher blockRenderManager,
                                      Font textRenderer,
                                      Options gameOptions,
                                      EntityModelSet modelLoader) {
        super(client, textureManager, itemRenderer, blockRenderManager, textRenderer, gameOptions, modelLoader);
    }

    @Override
    public boolean shouldRenderHitBoxes() {
        Entity target = this.crosshairPickEntity;
        if (target != null && shouldDeath(target)) {
            return false;
        }
        return super.shouldRenderHitBoxes();
    }

    @Override
    public <E extends Entity> boolean shouldRender(E entity, Frustum frustum,
                                                   double x, double y, double z) {
        if (shouldDeath(entity)) {
            return false;
        }
        return super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public <E extends Entity> void render(E entity,
                                          double x,
                                          double y,
                                          double z,
                                          float yaw,
                                          float partialTick,
                                          PoseStack poseStack,
                                          MultiBufferSource bufferSource,
                                          int packedLight) {
        if (shouldDeath(entity)) {
            return;
        }
        super.render(entity, x, y, z, yaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
