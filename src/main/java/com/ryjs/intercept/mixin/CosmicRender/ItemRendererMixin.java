package com.ryjs.intercept.mixin.CosmicRender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ryjs.intercept.client.model.CosmicBakeModel;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;


@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void intercept$takeOverCosmicItemRender(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                                    PoseStack mStack, MultiBufferSource buffers,
                                                    int packedLight, int packedOverlay, BakedModel modelIn,
                                                    CallbackInfo ci) {
        if (!(modelIn instanceof CosmicBakeModel cosmic)) {
            return;
        }
        ci.cancel();
        mStack.pushPose();
        try {
            BakedModel handled = ClientHooks.handleCameraTransforms(mStack, cosmic, context, leftHand);
            CosmicBakeModel renderer = handled instanceof CosmicBakeModel cbm ? cbm : cosmic;
            mStack.translate(-0.5D, -0.5D, -0.5D);
            renderer.renderItem(stack, context, mStack, buffers, packedLight, packedOverlay);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            mStack.popPose();
        }
    }
}
