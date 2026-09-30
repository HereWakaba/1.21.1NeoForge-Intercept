package com.ryjs.intercept.mixin.TimeStop;

import com.ryjs.intercept.util.timestop.TimeStopRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

@Mixin(Level.class)
public class LevelTimeFreezeMixin {

    @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true)
    private void intercept$freezeGameTime(CallbackInfoReturnable<Long> cir) {
        if (TimeStopRenderState.freezeTimeInRender()) {
            cir.setReturnValue(TimeStopRenderState.FROZEN_GAME_TIME);
        }
    }

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void intercept$freezeDayTime(CallbackInfoReturnable<Long> cir) {
        if (TimeStopRenderState.freezeTimeInRender()) {
            cir.setReturnValue(TimeStopRenderState.FROZEN_GAME_TIME);
        }
    }
}
