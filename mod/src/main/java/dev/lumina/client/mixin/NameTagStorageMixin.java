package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.NameTagsModule;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NameTagFeatureRenderer.Storage.class)
public abstract class NameTagStorageMixin {
	@ModifyExpressionValue(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundOpacity(F)F"))
	private float lumina$background(float original) {
		NameTagsModule m = Lumina.modules().get(NameTagsModule.class);
		return m != null && m.enabled() ? m.background.getFloat() : original;
	}

	@WrapOperation(method = "add", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void lumina$scale(PoseStack pose, float x, float y, float z, Operation<Void> original) {
		NameTagsModule m = Lumina.modules().get(NameTagsModule.class);
		float k = m != null && m.enabled() ? m.scale.getFloat() : 1;
		original.call(pose, x * k, y * k, z * k);
	}
}
