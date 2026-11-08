package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.ParticleChangerModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("TAIL"))
	private void lumina$alwaysParticles(Player player, Entity target, CallbackInfo ci) {
		ParticleChangerModule m = Lumina.modules().get(ParticleChangerModule.class);
		if (m == null || !m.enabled() || !(target instanceof LivingEntity)) return;
		if (m.alwaysSharpness.on()) player.magicCrit(target);
		if (m.alwaysCrits.on()) player.crit(target);
	}
}
