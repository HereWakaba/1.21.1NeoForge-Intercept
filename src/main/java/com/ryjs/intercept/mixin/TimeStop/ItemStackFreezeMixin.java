package com.ryjs.intercept.mixin.TimeStop;

import com.ryjs.intercept.util.timestop.AnimatedTextCache;
import com.ryjs.intercept.util.timestop.TimeStopState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;


@Mixin(ItemStack.class)
public class ItemStackFreezeMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void intercept$peekName(CallbackInfoReturnable<Component> cir) {
        if (TimeStopState.enabled) {
            Component cached = AnimatedTextCache.peekName((ItemStack) (Object) this);
            if (cached != null) {
                cir.setReturnValue(cached);
            }
        }
    }

    @Inject(method = "getHoverName", at = @At("RETURN"))
    private void intercept$storeName(CallbackInfoReturnable<Component> cir) {
        if (TimeStopState.enabled) {
            AnimatedTextCache.storeName((ItemStack) (Object) this, cir.getReturnValue());
        }
    }

    @Inject(method = "getTooltipLines", at = @At("HEAD"), cancellable = true)
    private void intercept$peekTooltip(CallbackInfoReturnable<List<Component>> cir) {
        if (TimeStopState.enabled) {
            List<Component> cached = AnimatedTextCache.peekTooltip((ItemStack) (Object) this);
            if (cached != null) {
                cir.setReturnValue(cached);
            }
        }
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void intercept$storeTooltip(CallbackInfoReturnable<List<Component>> cir) {
        if (TimeStopState.enabled) {
            AnimatedTextCache.storeTooltip((ItemStack) (Object) this, cir.getReturnValue());
        }
    }
}
