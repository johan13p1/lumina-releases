package dev.lumina.client.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Closing the game while on a server that sent resource packs: on the way out Minecraft drops those
 * packs and reloads every resource first (all cores, lots of memory), just to quit afterwards. That
 * froze the game for seconds and, with a second game open, the whole PC. Nothing is shown again, so
 * the reload is skipped once the game is quitting.
 */
@Mixin(Minecraft.class)
public abstract class QuitReloadMixin {
	@Unique
	private boolean lumina$quitting;

	@Inject(method = "exitWorldAndClose", at = @At("HEAD"))
	private void lumina$quitting(CallbackInfo ci) {
		lumina$quitting = true;
	}

	@Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true)
	private void lumina$noReloadWhileQuitting(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		if (lumina$quitting) cir.setReturnValue(CompletableFuture.completedFuture(null));
	}
}
