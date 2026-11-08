package dev.lumina.client.feature.hud;

import dev.lumina.client.module.TextHudModule;
import dev.lumina.client.render.Icons;

public final class FpsModule extends TextHudModule {
	public FpsModule() {
		super("fps", "FPS", "Shows your frames per second", Icons.GAUGE, true, AnchorX.LEFT, AnchorY.TOP, 4, 4);
	}

	@Override
	protected String label() {
		return "FPS";
	}

	@Override
	protected String value(boolean preview) {
		return String.valueOf(mc().getFps());
	}

	@Override
	protected String widthTemplate() {
		return "000";
	}
}
