package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.NameTagsModule;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagFeatureRendererMixin {
	/** Text shadow for the normal (non see-through) name tag pass. */
	@ModifyArg(method = "render", index = 4, at = @At(value = "INVOKE", ordinal = 1,
		target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
	private boolean lumina$shadow(boolean original) {
		NameTagsModule m = Lumina.modules().get(NameTagsModule.class);
		return m != null && m.enabled() ? m.shadow.on() : original;
	}
}
