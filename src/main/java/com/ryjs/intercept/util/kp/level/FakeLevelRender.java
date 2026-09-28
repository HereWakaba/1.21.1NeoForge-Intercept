package com.ryjs.intercept.util.kp.level;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;

public class FakeLevelRender extends LevelRenderer {
    public FakeLevelRender(Minecraft minecraft, EntityRenderDispatcher entityRenderDispatcher, BlockEntityRenderDispatcher blockEntityRenderDispatcher, RenderBuffers renderBuffers) {
        super(minecraft, entityRenderDispatcher, blockEntityRenderDispatcher, renderBuffers);
    }



}
