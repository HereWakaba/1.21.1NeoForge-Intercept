package com.ryjs.intercept.client.model;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ryjs.intercept.util.TransformUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;


public abstract class WrappedItemModel implements PerspectiveModel {

    protected BakedModel wrapped;
    protected ModelState parentState;
    @Nullable
    protected LivingEntity entity;
    @Nullable
    protected ClientLevel world;

    private static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    protected ItemOverrides overrideList;

    public WrappedItemModel(BakedModel wrapped) {
        this.overrideList = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel originalModel, ItemStack stack,
                                      @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                WrappedItemModel.this.entity = entity;
                WrappedItemModel.this.world = level != null ? level : (entity == null ? null : (ClientLevel) entity.level());
                return WrappedItemModel.this.isCosmic()
                        ? WrappedItemModel.this.wrapped.getOverrides().resolve(originalModel, stack, level, entity, seed)
                        : originalModel;
            }
        };
        this.wrapped = wrapped;
        this.parentState = TransformUtils.stateFromItemTransforms(wrapped.getTransforms());
    }


    protected static List<BakedQuad> bakeItem(List<TextureAtlasSprite> sprites) {
        LinkedList<BakedQuad> quads = new LinkedList<>();
        int index = 0;
        for (TextureAtlasSprite sprite : sprites) {
            List<BlockElement> elements =
                    ITEM_MODEL_GENERATOR.processFrames(index, "layer" + index, sprite.contents());
            for (BlockElement element : elements) {
                for (var entry : element.faces.entrySet()) {
                    BlockElementFace face = entry.getValue();
                    quads.add(FACE_BAKERY.bakeQuad(element.from, element.to, face, sprite, entry.getKey(),
                            PerspectiveModelState.IDENTITY, element.rotation, element.shade));
                }
            }
            index++;
        }
        return quads;
    }

    public boolean isCosmic() {
        return false;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return Collections.emptyList();
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return this.wrapped.getParticleIcon();
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return this.wrapped.getParticleIcon(data);
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return this.overrideList;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.wrapped.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.wrapped.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.wrapped.usesBlockLight();
    }

    @Override
    public @NotNull ItemTransforms getTransforms() {
        return this.wrapped.getTransforms();
    }

    @Override
    public @NotNull List<RenderType> getRenderTypes(@NotNull ItemStack itemStack, boolean fabulous) {
        return this.wrapped.getRenderTypes(itemStack, fabulous);
    }


    protected void renderWrapped(ItemStack stack, PoseStack pStack, MultiBufferSource buffers,
                                 int packedLight, int packedOverlay) {
        BakedModel model = this.wrapped.getOverrides().resolve(this.wrapped, stack, this.world, this.entity, 0);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                VertexConsumer vc = buffers.getBuffer(renderType);
                itemRenderer.renderModelLists(pass, stack, packedLight, packedOverlay, pStack, vc);
            }
        }
    }
}
