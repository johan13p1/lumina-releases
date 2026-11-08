package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.NameTagsModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.2 still draws name tags in their own feature renderer (26.3 moved them into TextFeatureRenderer). */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagFeatureRendererMixin {
	/** Text shadow for the normal (non see-through) name tag pass. */
	@WrapOperation(method = "prepareText", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
	private static Font.PreparedText lumina$shadow(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, boolean includeEmpty, int background,
		Operation<Font.PreparedText> original, @Local(argsOnly = true) NameTagFeatureRenderer.Submit nameTag) {
		NameTagsModule m = Lumina.modules() == null ? null : Lumina.modules().get(NameTagsModule.class);
		if (m != null && m.enabled() && nameTag.displayMode() == Font.DisplayMode.NORMAL) shadow = m.shadow.on();
		return original.call(font, text, x, y, color, shadow, includeEmpty, background);
	}
}
