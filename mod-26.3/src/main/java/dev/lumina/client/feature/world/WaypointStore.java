package dev.lumina.client.feature.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.lumina.client.Lumina;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Waypoints per server / singleplayer world, saved in lumina/waypoints.json. */
public final class WaypointStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<Map<String, List<Waypoint>>>() {}.getType();
	private Map<String, List<Waypoint>> data = new HashMap<>();
	private boolean loaded;

	private Path file() {
		return Lumina.config().dir().resolve("waypoints.json");
	}

	private void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		try {
			if (Files.exists(file())) {
				Map<String, List<Waypoint>> read = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), TYPE);
				if (read != null) data = new HashMap<>(read);
			}
		} catch (Exception e) {
			Lumina.LOG.error("Could not read waypoints", e);
		}
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(data, TYPE), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Lumina.LOG.error("Could not save waypoints", e);
		}
	}

	/** Identifies the current server or singleplayer world, or null when not in a world. */
	public static String worldKey() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		if (mc.getSingleplayerServer() != null) return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		if (mc.getCurrentServer() != null) return "server:" + mc.getCurrentServer().ip.toLowerCase();
		return "unknown";
	}

	public static String dimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	public List<Waypoint> current() {
		ensureLoaded();
		String key = worldKey();
		if (key == null) return new ArrayList<>();
		return data.computeIfAbsent(key, k -> new ArrayList<>());
	}

	public List<Waypoint> currentDimension() {
		String dim = dimension();
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : current()) if (dim.equals(w.dimension)) out.add(w);
		return out;
	}
}
