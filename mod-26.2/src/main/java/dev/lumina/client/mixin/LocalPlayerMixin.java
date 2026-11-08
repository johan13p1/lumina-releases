package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.ParticleChangerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "crit", at = @At("TAIL"))
	private void lumina$moreCrits(Entity entity, CallbackInfo ci) {
		ParticleChangerModule m = Lumina.modules().get(ParticleChangerModule.class);
		if (m == null || !m.enabled()) return;
		for (int i = 1; i < m.multiplier.getInt(); i++) Minecraft.getInstance().particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
	}

	@Inject(method = "magicCrit", at = @At("TAIL"))
	private void lumina$moreMagicCrits(Entity entity, CallbackInfo ci) {
		ParticleChangerModule m = Lumina.modules().get(ParticleChangerModule.class);
		if (m == null || !m.enabled()) return;
		for (int i = 1; i < m.multiplier.getInt(); i++) Minecraft.getInstance().particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
	}
}
