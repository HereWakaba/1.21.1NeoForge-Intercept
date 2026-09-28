package com.ryjs.intercept.client.model;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;


public interface PerspectiveModel extends BakedModel {

    @Nullable
    PerspectiveModelState getModelState();

    @Override
    default BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftFlip) {
        PerspectiveModelState modelState = getModelState();
        if (modelState == null) {
            return BakedModel.super.applyTransform(context, poseStack, leftFlip);
        }
        Transformation transform = modelState.getTransform(context);
        Vector3f trans = transform.getTranslation();
        Vector3f scale = transform.getScale();
        poseStack.translate(trans.x(), trans.y(), trans.z());
        poseStack.mulPose(transform.getLeftRotation());
        poseStack.scale(scale.x(), scale.y(), scale.z());
        poseStack.mulPose(transform.getRightRotation());
        if (leftFlip) {
            poseStack.mulPose(Axis.YN.rotationDegrees(180.0F));
        }
        return this;
    }
}
