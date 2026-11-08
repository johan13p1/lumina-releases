package dev.lumina.client.feature.hud;

import dev.lumina.client.LuminaKeys;
import dev.lumina.client.module.TextHudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.KeySetting;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class StopwatchModule extends TextHudModule {
	private final KeyMapping startStop = LuminaKeys.register("stopwatch", GLFW.GLFW_KEY_UNKNOWN);
	private final KeyMapping reset = LuminaKeys.register("stopwatch_reset", GLFW.GLFW_KEY_UNKNOWN);
	private long startedAt;
	private long accumulated;
	private boolean running;

	public StopwatchModule() {
		super("stopwatch", "Stopwatch", "A simple stopwatch with hotkeys", Icons.TIMER, false, AnchorX.RIGHT, AnchorY.BOTTOM, 4, 24);
		add(new KeySetting("key_start", "Start / Stop Key", startStop));
		add(new KeySetting("key_reset", "Reset Key", reset));
	}

	@Override
	public void tick() {
		while (startStop.consumeClick()) {
			if (!enabled()) continue;
			if (running) {
				accumulated += System.currentTimeMillis() - startedAt;
				running = false;
			} else {
				startedAt = System.currentTimeMillis();
				running = true;
			}
		}
		while (reset.consumeClick()) {
			if (!enabled()) continue;
			running = false;
			accumulated = 0;
		}
	}

	private long elapsed() {
		return accumulated + (running ? System.currentTimeMillis() - startedAt : 0);
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
		long ms = elapsed();
		long m = ms / 60000, s = (ms / 1000) % 60, cs = (ms / 10) % 100;
		return String.format(Locale.ROOT, "%02d:%02d.%02d", m, s, cs);
	}

	@Override
	protected String widthTemplate() {
		return "00:00.00";
	}
}
