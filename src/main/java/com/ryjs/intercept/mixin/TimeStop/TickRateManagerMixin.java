package com.ryjs.intercept.mixin.TimeStop;

import com.ryjs.intercept.util.timestop.TimeStopState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.TickRateManager;


@Mixin(TickRateManager.class)
public class TickRateManagerMixin {


    @Inject(method = "runsNormally", at = @At("HEAD"), cancellable = true)
    private void intercept$timeStop(CallbackInfoReturnable<Boolean> cir) {
        if (TimeStopState.enabled) {
            cir.setReturnValue(false);
        }
    }
}
