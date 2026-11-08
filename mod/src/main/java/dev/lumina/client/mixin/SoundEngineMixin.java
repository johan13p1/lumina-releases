package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.BetterSoundsModule;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	private static float lumina$multiplier(SoundInstance sound) {
		if (Lumina.modules() == null) return 1;
		BetterSoundsModule m = Lumina.modules().get(BetterSoundsModule.class);
		return m == null ? 1 : m.multiplier(sound);
	}

	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void lumina$mute(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (lumina$multiplier(sound) <= 0) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
	}

	@ModifyExpressionValue(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
	private float lumina$volume(float original, @Local(argsOnly = true) SoundInstance sound) {
		return Math.min(1f, original * lumina$multiplier(sound));
	}

	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
	private float lumina$tickVolume(float original, @Local(argsOnly = true) SoundInstance sound) {
		return Math.min(1f, original * lumina$multiplier(sound));
	}
}
