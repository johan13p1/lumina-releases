package dev.lumina.client.feature.hud;

import dev.lumina.client.module.TextHudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.util.CpsTracker;

public final class CpsModule extends TextHudModule {
	private final BoolSetting showRight;

	public CpsModule() {
		super("cps", "CPS", "Shows your clicks per second", Icons.MOUSE_CLICK, false, AnchorX.LEFT, AnchorY.TOP, 4, 22);
		showRight = add(new BoolSetting("show_right", "Show Right Clicks", true));
	}

	@Override
	protected String label() {
		return "CPS";
	}

	@Override
	protected String value(boolean preview) {
		return showRight.on() ? CpsTracker.left() + " | " + CpsTracker.right() : String.valueOf(CpsTracker.left());
	}
}
