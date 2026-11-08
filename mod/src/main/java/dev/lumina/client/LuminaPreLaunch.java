package dev.lumina.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Runs before any mod starts: sets up other mods' first config so they match Lumina. */
public final class LuminaPreLaunch implements PreLaunchEntrypoint {
	/** Wavey Capes as Lumina wants it: one smooth cloth, 3D physics, no extra wind. Only written once. */
	private static final String WAVEY_CAPES = """
		{
		  "configVersion": 2,
		  "windMode": "NONE",
		  "capeStyle": "SMOOTH",
		  "capeMovement": "BASIC_SIMULATION_3D",
		  "gravity": 25,
		  "heightMultiplier": 6,
		  "straveMultiplier": 2,
		  "computeGravityVector": false
		}
		""";

	@Override
	public void onPreLaunch() {
		writeOnce("waveycapes", "waveycapes.json", WAVEY_CAPES);
	}

	private static void writeOnce(String modId, String fileName, String content) {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded(modId)) return;
		Path file = loader.getConfigDir().resolve(fileName);
		if (Files.exists(file)) return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException ignored) {
			// the mod then starts with its own defaults
		}
	}
}
