package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.FullbrightModule;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	private static FullbrightModule lumina$fullbright() {
		if (Lumina.modules() == null) return null;
		FullbrightModule m = Lumina.modules().get(FullbrightModule.class);
		return m != null && m.enabled() ? m : null;
	}

	/** Fullbright takes the night vision path of the lightmap shader... */
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
	private boolean lumina$nightVision(boolean original) {
		return original || lumina$fullbright() != null;
	}

	/** ...plus a gamma far above the options slider, so even dark corners are lit. */
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", ordinal = 1, target = "Ljava/lang/Double;floatValue()F"))
	private float lumina$gamma(float original) {
		FullbrightModule m = lumina$fullbright();
		return m == null ? original : Math.max(original, 1f + 9f * m.strength.getFloat());
	}

	/** ...with its own strength, or the real effect's if that is brighter. */
	@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
	private float lumina$nightVisionScale(LivingEntity entity, float partialTick, Operation<Float> original) {
		FullbrightModule m = lumina$fullbright();
		if (m == null) return original.call(entity, partialTick);
		// the vanilla scale reads the effect instance, which doesn't exist without real night vision
		float real = entity.hasEffect(MobEffects.NIGHT_VISION) ? original.call(entity, partialTick) : 0;
		return Math.max(m.strength.getFloat(), real);
	}
}
