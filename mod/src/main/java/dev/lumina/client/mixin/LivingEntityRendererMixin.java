package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.NameTagsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
	private boolean lumina$ownNameTag(boolean original, @Local(argsOnly = true) LivingEntity entity) {
		if (original) return true;
		Minecraft mc = Minecraft.getInstance();
		NameTagsModule m = Lumina.modules().get(NameTagsModule.class);
		return m != null && m.enabled() && m.showOwn.on() && entity == mc.player && entity == mc.getCameraEntity()
			&& !mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui && !entity.isInvisible();
	}
}
