package dev.lumina.client.mixin;

import java.util.Comparator;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	/** The tab list's own order, so Lumina looks up the players it actually shows. */
	@Accessor("PLAYER_COMPARATOR")
	static Comparator<PlayerInfo> lumina$order() {
		throw new AssertionError();
	}
}
