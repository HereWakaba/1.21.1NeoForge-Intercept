package com.ryjs.intercept.mixin.TimeStop;

import com.ryjs.intercept.util.timestop.TimeStopRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.Util;

@Mixin(Util.class)
public class UtilMillisFreezeMixin {

    @Inject(method = "getMillis", at = @At("HEAD"), cancellable = true)
    private static void intercept$freezeMillis(CallbackInfoReturnable<Long> cir) {
        if (TimeStopRenderState.freezeTimeInRender()) {
            cir.setReturnValue(TimeStopRenderState.FROZEN_MILLIS);
        }
    }
}
