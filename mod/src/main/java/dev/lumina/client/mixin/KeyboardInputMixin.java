package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.FreeLookModule;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	/** The movement keys fly the spectator camera; your player stands still meanwhile. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void lumina$standStillWhileFlying(CallbackInfo ci) {
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		if (m != null && m.flying()) {
			keyPresses = Input.EMPTY;
			moveVector = Vec2.ZERO;
		}
	}
}
