package dev.lumina.client.feature.hud;

import dev.lumina.client.module.TextHudModule;
import dev.lumina.client.render.Icons;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class PingModule extends TextHudModule {
	public PingModule() {
		super("ping", "Ping", "Shows your latency to the server", Icons.WIFI, false, AnchorX.LEFT, AnchorY.TOP, 4, 82);
	}

	@Override
	protected String label() {
		return "ms";
	}

	@Override
	protected String value(boolean preview) {
		var conn = mc().getConnection();
		if (conn == null || mc().player == null) return preview ? "24" : "0";
		PlayerInfo info = conn.getPlayerInfo(mc().player.getUUID());
		return info == null ? "0" : String.valueOf(info.getLatency());
	}

	@Override
	protected String widthTemplate() {
		return "000";
	}
}
