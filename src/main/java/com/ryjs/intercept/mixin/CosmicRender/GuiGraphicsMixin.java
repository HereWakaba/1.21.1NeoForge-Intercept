package com.ryjs.intercept.mixin.CosmicRender;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ryjs.intercept.client.effect.TaiChiTooltipRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;


@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Shadow
    private ItemStack tooltipStack;

    @Inject(method = "renderTooltipInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("HEAD"), cancellable = true)
    private void intercept$takeOverTaiChiTooltip(Font font, List<ClientTooltipComponent> components,
                                                 int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                                 CallbackInfo ci) {
        if (!TaiChiTooltipRenderer.isTaiChi(this.tooltipStack)) {
            return;
        }
        ci.cancel();
        try {
            TaiChiTooltipRenderer.renderCustomTooltip((GuiGraphics) (Object) this, font, this.tooltipStack,
                    mouseX, mouseY);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
