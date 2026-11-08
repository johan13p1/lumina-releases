package dev.lumina.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumina.client.Lumina;
import dev.lumina.client.module.Module;
import dev.lumina.client.module.ModuleManager;
import dev.lumina.client.setting.SettingChanges;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path dir = dataDir();
	private final Path file = dir.resolve("config.json");
	private final ModuleManager modules;
	/** The modules as the file held them, so a save keeps what another Lumina version knows and this one doesn't. */
	private JsonObject loadedModules = new JsonObject();

	/**
	 * Lumina's own folder: settings, waypoints, capes and skins. The launcher gives every Minecraft
	 * version the same one (-Dlumina.data), so a setting changed in one version is there in all of them.
	 * Dev clients and older launchers use the game folder's lumina/.
	 */
	public static Path dataDir() {
		String shared = System.getProperty("lumina.data", "");
		return shared.isBlank() ? FabricLoader.getInstance().getGameDir().resolve("lumina") : Path.of(shared);
	}
	/** UI state that isn't tied to a module (menu position, last tab, ...). */
	public final JsonObject ui = new JsonObject();

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
	}

	public Path dir() {
		return dir;
	}

	public void load() {
		if (!Files.exists(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
			loadedModules = mods.deepCopy();
			for (Module m : modules.all()) {
				if (mods.has(m.id()) && mods.get(m.id()).isJsonObject()) m.load(mods.getAsJsonObject(m.id()));
			}
			if (root.has("ui") && root.get("ui").isJsonObject()) {
				for (var e : root.getAsJsonObject("ui").entrySet()) ui.add(e.getKey(), e.getValue());
			}
		} catch (Exception e) {
			Lumina.LOG.error("Failed to read Lumina config, keeping defaults", e);
			try {
				Files.copy(file, dir.resolve("config.broken.json"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
		}
		SettingChanges.clear();
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject mods = loadedModules.deepCopy();
		for (Module m : modules.all()) mods.add(m.id(), keepUnknown(m, m.save(), loadedModules.get(m.id())));
		root.add("modules", mods);
		root.add("ui", ui);
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve("config.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			SettingChanges.clear();
		} catch (IOException e) {
			Lumina.LOG.error("Failed to save Lumina config", e);
		}
	}

	/** Settings of a module that this Lumina version doesn't have stay in the file for the versions that do. */
	private static JsonObject keepUnknown(Module m, JsonObject saved, JsonElement loaded) {
		if (loaded == null || !loaded.isJsonObject() || !saved.has("settings")) return saved;
		JsonElement old = loaded.getAsJsonObject().get("settings");
		if (old == null || !old.isJsonObject()) return saved;
		JsonObject settings = saved.getAsJsonObject("settings");
		for (var e : old.getAsJsonObject().entrySet()) {
			boolean known = m.settings().stream().anyMatch(s -> s.id().equals(e.getKey()));
			if (!known && !settings.has(e.getKey())) settings.add(e.getKey(), e.getValue());
		}
		return saved;
	}

	public void saveIfDirty() {
		if (SettingChanges.shouldSave()) save();
	}

	public float uiFloat(String key, float def) {
		JsonElement e = ui.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsFloat() : def;
	}

	public String uiString(String key, String def) {
		JsonElement e = ui.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}
}
