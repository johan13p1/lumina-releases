package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.hud.SpotifyModule;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	/** The chat frees the mouse, so the Spotify widget's buttons can be clicked while it is open. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void lumina$spotifyButtons(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		SpotifyModule spotify = Lumina.modules().get(SpotifyModule.class);
		if (spotify != null && spotify.click(event.x(), event.y())) cir.setReturnValue(true);
	}
}
