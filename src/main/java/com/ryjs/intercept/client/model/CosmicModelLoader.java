package com.ryjs.intercept.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;


public final class CosmicModelLoader implements IGeometryLoader<CosmicModelLoader.CosmicGeometry> {

    public static final CosmicModelLoader INSTANCE = new CosmicModelLoader();

    private CosmicModelLoader() {}

    @Override
    public CosmicGeometry read(JsonObject modelContents, JsonDeserializationContext context) throws JsonParseException {
        JsonObject cosmicObj = modelContents.getAsJsonObject("cosmic");
        if (cosmicObj == null) {
            throw new IllegalStateException("Missing 'cosmic' object.");
        }

        List<String> masks = new ArrayList<>();
        if (cosmicObj.has("mask") && cosmicObj.get("mask").isJsonArray()) {
            JsonArray array = cosmicObj.getAsJsonArray("mask");
            for (int i = 0; i < array.size(); i++) {
                masks.add(array.get(i).getAsString());
            }
        } else {
            masks.add(GsonHelper.getAsString(cosmicObj, "mask"));
        }

        JsonObject clean = modelContents.deepCopy();
        clean.remove("cosmic");
        clean.remove("loader");
        return new CosmicGeometry(context.deserialize(clean, BlockModel.class), masks);
    }

    public static class CosmicGeometry implements IUnbakedGeometry<CosmicGeometry> {

        private final BlockModel baseModel;
        private final List<String> maskTextures;

        public CosmicGeometry(BlockModel baseModel, List<String> maskTextures) {
            this.baseModel = baseModel;
            this.maskTextures = maskTextures;
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            BakedModel bakedBase = this.baseModel.bake(baker, this.baseModel, spriteGetter, modelState, true);
            List<ResourceLocation> locations = new ArrayList<>(this.maskTextures.size());
            for (String mask : this.maskTextures) {
                locations.add(ResourceLocation.parse(mask));
            }
            return new CosmicBakeModel(bakedBase, locations);
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                                   IGeometryBakingContext context) {
            this.baseModel.resolveParents(modelGetter);
        }
    }
}
