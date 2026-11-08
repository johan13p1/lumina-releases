package dev.lumina.client.feature.hud;

import dev.lumina.client.module.TextHudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ModeSetting;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ClockModule extends TextHudModule {
	private final ModeSetting format;
	private final BoolSetting seconds;
	private final BoolSetting date;

	public ClockModule() {
		super("clock", "Clock", "Shows the real time", Icons.CLOCK, false, AnchorX.RIGHT, AnchorY.BOTTOM, 4, 4);
		format = add(new ModeSetting("format", "Format", "24h", "24h", "12h"));
		seconds = add(new BoolSetting("seconds", "Show Seconds", false));
		date = add(new BoolSetting("date", "Show Date", false));
	}

	@Override
	protected boolean defaultShowLabel() {
		return false;
	}

	@Override
	protected String label() {
		return "";
	}

	@Override
	protected String value(boolean preview) {
		String pattern = format.is("24h") ? (seconds.on() ? "HH:mm:ss" : "HH:mm") : (seconds.on() ? "h:mm:ss a" : "h:mm a");
		if (date.on()) pattern = "dd.MM.  " + pattern;
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
	}
}
