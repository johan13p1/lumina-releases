package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.lumina.client.render.LoadingArt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Lumina loading screen: dark background, Lumina logo and a progress bar like the menu's instead of Mojang Studios. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;

	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int lumina$background(int original) {
		return LoadingArt.BACKGROUND;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void lumina$logo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h,
		int uw, int vh, int tw, int th, int color, Operation<Void> original) {
		LoadingArt.drawLogo(g, ARGB.alpha(color) / 255f);
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", ordinal = 1,
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void lumina$logoSecondHalf(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h,
		int uw, int vh, int tw, int th, int color, Operation<Void> original) {
		// the Mojang logo is drawn in two halves; Lumina's is one image
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
	private void lumina$progress(LoadingOverlay self, GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float alpha, Operation<Void> original) {
		LoadingArt.drawProgress(g, currentProgress, alpha);
	}
}
