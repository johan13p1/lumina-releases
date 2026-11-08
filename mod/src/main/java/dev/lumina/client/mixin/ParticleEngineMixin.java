package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.ParticleChangerModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void lumina$hideParticles(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
		ParticleChangerModule m = Lumina.modules().get(ParticleChangerModule.class);
		if (m != null && m.hidden(options)) cir.setReturnValue(null);
	}
}
