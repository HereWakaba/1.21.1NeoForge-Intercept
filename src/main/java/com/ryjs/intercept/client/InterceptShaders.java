package com.ryjs.intercept.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.ryjs.intercept.Intercept;
import com.ryjs.intercept.client.model.CosmicModelLoader;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;


@SuppressWarnings("removal")
@EventBusSubscriber(modid = Intercept.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InterceptShaders {

    public static ShaderInstance taichiTooltipShader;
    public static ShaderInstance starNestShader;
    public static ShaderInstance taichiWorldShader;
    public static ShaderInstance cosmicShader;

    private InterceptShaders() {}

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(Intercept.rl("cosmic"), CosmicModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), Intercept.rl("taichi_tooltip"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> taichiTooltipShader = shader
        );
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), Intercept.rl("star_nest"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> starNestShader = shader
        );
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), Intercept.rl("taichi_world"),
                        DefaultVertexFormat.POSITION_TEX_COLOR),
                shader -> taichiWorldShader = shader
        );
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), Intercept.rl("cosmic"),
                        DefaultVertexFormat.BLOCK),
                shader -> cosmicShader = shader
        );
    }
}
