package dev.lumina.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumina.client.Lumina;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;

/** macOS conventions: ⌘/⌥ shortcuts, trackpad clicks, Retina rendering and the Dock icon. */
public final class MacOS {
	public static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
	private static final int LEFT = 0, RIGHT = 1, MIDDLE = 2;

	private MacOS() {
	}

	/** Jumps or deletes word by word: ⌥ on macOS, Ctrl elsewhere. */
	public static boolean wordModifier(InputWithModifiers e) {
		return IS_MAC ? e.hasAltDown() : e.hasControlDown();
	}

	/** Jumps or deletes to the start/end of the line: ⌘ on macOS (nothing elsewhere, Home/End do that). */
	public static boolean lineModifier(InputWithModifiers e) {
		return IS_MAC && e.hasControlDownWithQuirk();
	}

	/**
	 * The mouse button a click means. A trackpad has no middle button and many Mac users right click
	 * with Control, so on macOS Control-click counts as right click and Option-click as middle click.
	 */
	public static int button(MouseButtonEvent e) {
		int button = e.button();
		if (!IS_MAC || button != LEFT) return button;
		if (e.hasControlDown()) return RIGHT;
		if (e.hasAltDown()) return MIDDLE;
		return button;
	}

	/** Human name of the middle click for hints. */
	public static String middleClickName() {
		return IS_MAC ? "Option-click" : "Middle click";
	}

	/**
	 * Whether the world renders at the full Retina resolution. Off by default: four times the pixels
	 * cost about half the frame rate for little visible gain (see RenderScale). Read every frame.
	 */
	public static boolean retina() {
		if (Boolean.getBoolean("lumina.retina")) return true;
		try {
			if (Lumina.modules() != null && Lumina.settings() != null) return Lumina.settings().retina.on();
		} catch (RuntimeException ignored) {
		}
		return configFlag("retina", false);
	}

	/**
	 * Whether the window gets a Retina framebuffer, so menus, HUD and text are drawn with all its
	 * pixels while the world keeps the faster resolution (see RenderScale). Off gives a 1x
	 * framebuffer: the most FPS, with a soft GUI. Read when the window is created.
	 */
	public static boolean sharpUi() {
		try {
			if (Lumina.modules() != null && Lumina.settings() != null) return Lumina.settings().sharpUi.on();
		} catch (RuntimeException ignored) {
		}
		return configFlag("sharp_ui", true);
	}

	/** A Lumina setting straight from the config file, for when the mod is not set up yet. */
	private static boolean configFlag(String key, boolean fallback) {
		try {
			Path file = dev.lumina.client.config.ConfigManager.dataDir().resolve("config.json");
			if (!Files.exists(file)) return fallback;
			JsonObject settings = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
				.getAsJsonObject("modules").getAsJsonObject("client").getAsJsonObject("settings");
			return settings != null && settings.has(key) ? settings.get(key).getAsBoolean() : fallback;
		} catch (Exception e) {
			return fallback;
		}
	}

	/** The Lumina icon for the Dock (512 px PNG on Apple's icon grid). */
	public static InputStream dockIcon() {
		return MacOS.class.getResourceAsStream("/assets/lumina/icons/dock.png");
	}
}
