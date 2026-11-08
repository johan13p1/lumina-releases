package dev.lumina.client.feature.world;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;

/** Client-side weather and time of day. Other players are not affected. */
public final class WeatherChangerModule extends Module {
	public final ModeSetting weather;
	public final ModeSetting time;
	private final NumberSetting customTime;

	public WeatherChangerModule() {
		super("weather_changer", "Weather Changer", "Set your own weather and time of day", Category.WORLD, Icons.CLOUD_RAIN, false);
		add(new HeaderSetting("Weather"));
		weather = add(new ModeSetting("weather", "Weather", "Clear", "Server", "Clear", "Rain", "Thunder", "Snow"));
		add(new HeaderSetting("Time"));
		time = add(new ModeSetting("time", "Time Of Day", "Server", "Server", "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom"));
		customTime = add(new NumberSetting("custom_time", "Custom Time", 6000, 0, 23999, 100).visibleWhen(() -> time.is("Custom")));
	}

	/** Rain strength override, or NaN to keep the server's. */
	public float rain() {
		if (!enabled()) return Float.NaN;
		return switch (weather.get()) {
			case "Clear" -> 0;
			case "Rain", "Thunder", "Snow" -> 1;
			default -> Float.NaN;
		};
	}

	public float thunder() {
		if (!enabled()) return Float.NaN;
		return switch (weather.get()) {
			case "Thunder" -> 1;
			case "Clear", "Rain", "Snow" -> 0;
			default -> Float.NaN;
		};
	}

	public boolean forceSnow() {
		return enabled() && weather.is("Snow");
	}

	/** Day time override or -1. */
	public long dayTime(long original) {
		if (!enabled()) return -1;
		long base = original - Math.floorMod(original, 24000L);
		long t = switch (time.get()) {
			case "Sunrise" -> 23000;
			case "Day" -> 1000;
			case "Noon" -> 6000;
			case "Sunset" -> 12000;
			case "Night" -> 14000;
			case "Midnight" -> 18000;
			case "Custom" -> customTime.getInt();
			default -> -1;
		};
		return t < 0 ? -1 : base + t;
	}
}
