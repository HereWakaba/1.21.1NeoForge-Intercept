package com.ryjs.intercept.mixin.TimeStop;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ryjs.intercept.util.timestop.ObfuscationSeed;
import com.ryjs.intercept.util.timestop.TimeStopState;

import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.RandomSource;

@Mixin(FontSet.class)
public class FontSetObfuscationFreezeMixin {

    @Shadow
    @Final
    private static RandomSource RANDOM;

    @Inject(method = "getRandomGlyph", at = @At("HEAD"))
    private void intercept$reseedOncePerFrame(CallbackInfoReturnable<BakedGlyph> cir) {
        if (TimeStopState.enabled && ObfuscationSeed.claimReseed()) {
            RANDOM.setSeed(ObfuscationSeed.FROZEN_SEED);
        }
    }
}
