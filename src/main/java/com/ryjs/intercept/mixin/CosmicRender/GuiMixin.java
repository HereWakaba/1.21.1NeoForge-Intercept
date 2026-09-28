package com.ryjs.intercept.mixin.CosmicRender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ryjs.intercept.client.effect.TaiChiHudOverlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;


@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void intercept$drawTaiChiHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            TaiChiHudOverlay.onGuiRender(guiGraphics);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
