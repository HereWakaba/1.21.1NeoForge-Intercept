package com.ryjs.intercept.mixin.TimeStop;

import com.ryjs.intercept.util.timestop.TimeStopState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LevelRenderer;

@Mixin(LevelRenderer.class)
public class LevelRendererFreezeMixin {

    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void intercept$freezeRain(net.minecraft.client.Camera camera, CallbackInfo ci) {
        if (TimeStopState.enabled) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V"),
            index = 1
    )
    private float intercept$freezeSnowRainPartial(float partialTick) {
        return TimeStopState.enabled ? 0.0F : partialTick;
    }
}
