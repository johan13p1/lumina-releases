package dev.lumina.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.config.ConfigManager;
import dev.lumina.client.feature.Modules;
import dev.lumina.client.feature.utility.ClientSettingsModule;
import dev.lumina.client.gui.LuminaScreen;
import dev.lumina.client.hud.HudRenderer;
import dev.lumina.client.module.ModuleManager;
import dev.lumina.client.online.LuminaOnline;
import dev.lumina.client.render.font.Fonts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lumina implements ClientModInitializer {
	public static final String MOD_ID = "lumina";
	public static final Logger LOG = LoggerFactory.getLogger("Lumina");
	public static final boolean DEV = Boolean.getBoolean("lumina.dev");
	public static String VERSION = "1.0.0";
	public static KeyMapping.Category KEY_CATEGORY;
	public static KeyMapping MENU_KEY;

	private static ModuleManager modules;
	private static ConfigManager config;

	/** How the game is called everywhere: Lumina plus the Minecraft version, e.g. "Lumina 1.21.11". */
	public static String gameName() {
		return "Lumina " + net.minecraft.SharedConstants.getCurrentVersion().name();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static ModuleManager modules() {
		return modules;
	}

	public static ConfigManager config() {
		return config;
	}

	@Override
	public void onInitializeClient() {
		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(c -> VERSION = c.getMetadata().getVersion().getFriendlyString());
		KEY_CATEGORY = KeyMapping.Category.register(id("main"));
		MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lumina.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KEY_CATEGORY));

		modules = new ModuleManager();
		Modules.registerAll(modules);
		config = new ConfigManager(modules);
		config.load();

		HudRenderer.register();
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			Fonts.init();
			modules.ready();
			dev.lumina.client.feature.world.DefaultServers.addMissing(client);
			dev.lumina.client.feature.cosmetics.WaveyCapesBridge.install();
			if (DEV) DevHooks.start();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		ClientTickEvents.END_CLIENT_TICK.register(Lumina::tick);
		LOG.info("Lumina {} initialized with {} modules", VERSION, modules.all().size());
	}

	private static void tick(Minecraft mc) {
		Fonts.trim();
		while (MENU_KEY.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new LuminaScreen(null));
		}
		modules.tick();
		LuminaOnline.tick(mc);
		config.saveIfDirty();
	}

	public static ClientSettingsModule settings() {
		return modules.get(ClientSettingsModule.class);
	}

	public static boolean menuBlurEnabled() {
		var blur = modules.get(dev.lumina.client.feature.visual.MenuBlurModule.class);
		return blur != null && blur.enabled() && blur.luminaMenu.on();
	}

	/** Dev builds only: frame timing for the "frames" step of the shot script. */
	public static void devFrame() {
		DevHooks.frame();
	}

	public static boolean hideHudInDebug() {
		ClientSettingsModule s = settings();
		return s != null && s.hideInDebug.on();
	}
}
