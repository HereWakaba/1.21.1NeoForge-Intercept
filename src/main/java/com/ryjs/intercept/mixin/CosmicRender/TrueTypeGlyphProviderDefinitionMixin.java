package com.ryjs.intercept.mixin.CosmicRender;

import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;


@Mixin(TrueTypeGlyphProviderDefinition.class)
public class TrueTypeGlyphProviderDefinitionMixin {

    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Get_Font_Format(Lorg/lwjgl/util/freetype/FT_Face;)Ljava/lang/String;"))
    private static String intercept$acceptCff(FT_Face face) {
        String format = FreeType.FT_Get_Font_Format(face);
        return switch (format) {
            case "CFF", "CID Font Type 0", "Type 1" -> "TrueType";
            default -> format;
        };
    }
}
