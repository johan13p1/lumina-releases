package dev.lumina.client.feature.hud;

import dev.lumina.client.module.TextHudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.ModeSetting;
import java.util.Locale;

public final class MemoryModule extends TextHudModule {
	private final ModeSetting format;

	public MemoryModule() {
		super("memory", "Memory Usage", "Shows how much RAM the game uses", Icons.MEMORY, false, AnchorX.LEFT, AnchorY.TOP, 4, 100);
		format = add(new ModeSetting("format", "Format", "Percent", "Percent", "Used / Max", "Both"));
	}

	@Override
	protected String defaultLayout() {
		return "Before";
	}

	@Override
	protected String label() {
		return "MEM";
	}

	@Override
	protected String value(boolean preview) {
		Runtime rt = Runtime.getRuntime();
		long max = rt.maxMemory(), used = rt.totalMemory() - rt.freeMemory();
		int pct = (int) (used * 100 / Math.max(1, max));
		String usedStr = String.format(Locale.ROOT, "%.1f", used / 1073741824.0);
		String maxStr = String.format(Locale.ROOT, "%.1f", max / 1073741824.0);
		return switch (format.get()) {
			case "Used / Max" -> usedStr + " / " + maxStr + " GB";
			case "Both" -> pct + "%  " + usedStr + " / " + maxStr + " GB";
			default -> pct + "%";
		};
	}
}
