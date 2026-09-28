package com.ryjs.intercept.mixin.TimeStop;

import com.ryjs.intercept.util.timestop.TimeStopState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;


@Mixin(GameRenderer.class)
public class GameRendererFovFreezeMixin {

    @Inject(method = "tickFov", at = @At("HEAD"), cancellable = true)
    private void intercept$freezeFov(CallbackInfo ci) {
        if (TimeStopState.enabled) {
            ci.cancel();
        }
    }
}
