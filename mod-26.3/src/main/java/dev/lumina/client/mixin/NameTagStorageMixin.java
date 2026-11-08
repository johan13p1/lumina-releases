package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.NameTagsModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name tag background, size and text shadow. Since 26.1 name tags are built in SubmitNodeCollection. */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagStorageMixin {
	@ModifyExpressionValue(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F"))
	private float lumina$background(float original) {
		NameTagsModule m = Lumina.modules().get(NameTagsModule.class);
		return m != null && m.enabled() ? m.background.getFloat() : original;
	}

	@WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void lumina$scale(PoseStack pose, float x, float y, float z, Operation<Void> original) {
		NameTagsModule m = Lumina.modules().get(NameTagsModule.class);
		float k = m != null && m.enabled() ? m.scale.getFloat() : 1;
		original.call(pose, x * k, y * k, z * k);
	}

	/** Text shadow for the normal (non see-through) name tag pass. */
	@ModifyReturnValue(method = "nameTag", at = @At("RETURN"))
	private static TextFeatureRenderer.Submit lumina$shadow(TextFeatureRenderer.Submit original) {
		if (Lumina.modules() == null || original.displayMode() != Font.DisplayMode.NORMAL) return original;
		NameTagsModule m = Lumina.modules().get(NameTagsModule.class);
		if (m == null || !m.enabled() || !(original.content() instanceof TextFeatureRenderer.Content.Text t) || t.dropShadow() == m.shadow.on()) return original;
		var text = new TextFeatureRenderer.Content.Text(t.x(), t.y(), t.string(), m.shadow.on(), t.color(), t.backgroundColor(), t.outlineColor());
		return new TextFeatureRenderer.Submit(original.pose(), original.displayMode(), original.lightCoords(), text);
	}
}
