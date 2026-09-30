package com.ryjs.intercept.mixin.TimeStop;

import com.ryjs.intercept.util.timestop.TimeStopState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.PostChain;

@Mixin(PostChain.class)
public class PostChainFreezeMixin {

    @ModifyVariable(method = "process", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float intercept$freezeTime(float partialTicks) {
        return TimeStopState.enabled ? 0.0F : partialTicks;
    }
}
