package dev.lumina.client.mixin;

import dev.lumina.client.online.LuminaOnline;
import dev.lumina.client.render.Logo;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	/** The Lumina mark in front of the name tag of every Lumina player, you included (F5). Always on. */
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void lumina$logoNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
		if (entity instanceof Player player && cir.getReturnValue() != null && LuminaOnline.showsLogo(player.getUUID())) {
			cir.setReturnValue(Logo.withLogo(cir.getReturnValue()));
		}
	}
}
