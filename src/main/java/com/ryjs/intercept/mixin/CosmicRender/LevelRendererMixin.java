package com.ryjs.intercept.mixin.CosmicRender;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ryjs.intercept.client.effect.TaiChiChargeEffect;
import com.ryjs.intercept.client.effect.TaiChiGroundArray;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;


@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;"
            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("TAIL"))
    private void intercept$renderTaiChiWorld(DeltaTracker deltaTracker, boolean renderBlockLayer, Camera camera,
                                             GameRenderer gameRenderer, LightTexture lightTexture,
                                             Matrix4f cameraMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        try {
            TaiChiChargeEffect.onRenderLevelFrame();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(cameraMatrix);
        RenderSystem.applyModelViewMatrix();
        try {
            TaiChiGroundArray.onRenderLevelFrame(new PoseStack(),
                    deltaTracker.getGameTimeDeltaPartialTick(false), camera);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
