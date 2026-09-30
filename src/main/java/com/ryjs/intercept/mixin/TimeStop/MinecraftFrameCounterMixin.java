package com.ryjs.intercept.mixin.TimeStop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ryjs.intercept.util.timestop.ObfuscationSeed;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public class MinecraftFrameCounterMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void intercept$frameStart(CallbackInfo ci) {
        ObfuscationSeed.nextFrame();
    }
}
