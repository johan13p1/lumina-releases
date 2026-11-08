package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.NameTagsModule;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name tag background and size. Since 26.1 name tags are built in SubmitNodeCollection; the shadow is in NameTagFeatureRendererMixin. */
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
}
