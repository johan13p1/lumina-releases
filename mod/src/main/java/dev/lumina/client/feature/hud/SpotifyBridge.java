package dev.lumina.client.feature.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.lumina.client.Lumina;
import dev.lumina.client.render.Sprites;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Talks to spotify-bridge.ps1, a small PowerShell helper that reads Spotify from Windows' media session,
 * the same one the volume flyout shows. That way no Spotify login or API key is needed.
 */
final class SpotifyBridge {
	static final Identifier COVER = Lumina.id("spotify_cover");
	static final int COVER_SIZE = 128;
	private static final String SCRIPT = "spotify-bridge.ps1";
	/** If the helper dies, don't restart it more often than this. */
	private static final long RESTART_DELAY_MS = 30_000;

	/** What Spotify is doing; the reader thread replaces it as a whole. */
	record State(String title, String artist, boolean playing, double position, double duration, boolean canSeek, long receivedAt) {
		/** Spotify reports once a second; in between the position is counted forward. */
		double positionNow() {
			double now = playing ? position + (System.currentTimeMillis() - receivedAt) / 1000.0 : position;
			return duration > 0 ? Math.min(now, duration) : now;
		}
	}

	private volatile @Nullable State state;
	private volatile boolean hasCover;
	private volatile @Nullable Process process;
	private volatile @Nullable BufferedWriter commands;
	private @Nullable Thread worker;
	private long nextStart;

	@Nullable State state() {
		return state;
	}

	boolean hasCover() {
		return hasCover;
	}

	/** Called every tick while the widget is on; starts the helper off the render thread. */
	void keepRunning() {
		if (worker != null && worker.isAlive()) return;
		long now = System.currentTimeMillis();
		if (now < nextStart) return;
		nextStart = now + RESTART_DELAY_MS;
		worker = new Thread(this::run, "Lumina Spotify");
		worker.setDaemon(true);
		worker.start();
	}

	/** Called every tick while the widget is off. */
	void stop() {
		Process running = process;
		if (running != null) running.destroy();
		nextStart = 0;
	}

	private void run() {
		try {
			Process started = new ProcessBuilder(powershell(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", writeScript().toString())
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			process = started;
			commands = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
			try (BufferedReader in = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) handle(line);
			}
			int exit = started.waitFor();
			if (exit != 0) Lumina.LOG.warn("The Spotify helper ended with exit code {}", exit);
		} catch (IOException | RuntimeException e) {
			Lumina.LOG.warn("Could not reach Spotify", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			process = null;
			commands = null;
			state = null;
			hasCover = false;
		}
	}

	private static String powershell() {
		String windows = System.getenv("SystemRoot");
		return windows == null ? "powershell.exe" : windows + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
	}

	/** The helper runs from a real file: PowerShell can't run a script from inside the mod's jar. */
	private static Path writeScript() throws IOException {
		Path file = dev.lumina.client.config.ConfigManager.dataDir().resolve(SCRIPT);
		try (InputStream in = SpotifyBridge.class.getResourceAsStream("/assets/lumina/" + SCRIPT)) {
			if (in == null) throw new IOException(SCRIPT + " is missing from the mod");
			Files.createDirectories(file.getParent());
			Files.write(file, in.readAllBytes());
		}
		return file;
	}

	private void handle(String line) {
		JsonObject o;
		try {
			o = JsonParser.parseString(line).getAsJsonObject();
		} catch (RuntimeException e) {
			Lumina.LOG.warn("Unexpected line from the Spotify helper: {}", line);
			return;
		}
		if (!o.get("open").getAsBoolean()) {
			state = null;
			hasCover = false;
			return;
		}
		state = new State(text(o, "title"), text(o, "artist"), o.get("playing").getAsBoolean(), o.get("position").getAsDouble(),
			o.get("duration").getAsDouble(), o.get("canSeek").getAsBoolean(), System.currentTimeMillis());
		if (o.has("cover")) decodeCover(o.get("cover").getAsString());
	}

	private static String text(JsonObject o, String key) {
		return o.get(key).isJsonNull() ? "" : o.get(key).getAsString();
	}

	private void decodeCover(String base64) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
			if (image == null) return;
			BufferedImage small = new BufferedImage(COVER_SIZE, COVER_SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = small.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.drawImage(image, 0, 0, COVER_SIZE, COVER_SIZE, null);
			g.dispose();
			NativeImage texture = new NativeImage(COVER_SIZE, COVER_SIZE, true);
			for (int y = 0; y < COVER_SIZE; y++) {
				for (int x = 0; x < COVER_SIZE; x++) texture.setPixel(x, y, small.getRGB(x, y));
			}
			// textures belong to the render thread; registering the same name replaces and frees the old cover
			Minecraft.getInstance().execute(() -> {
				Sprites.register(COVER, texture);
				hasCover = true;
			});
		} catch (IOException | IllegalArgumentException e) {
			Lumina.LOG.warn("Could not read the Spotify cover", e);
		}
	}

	void playPause() {
		send("play_pause");
	}

	void next() {
		send("next");
	}

	void previous() {
		send("previous");
	}

	void seek(double seconds) {
		send(String.format(Locale.ROOT, "seek %.1f", seconds));
	}

	private void send(String command) {
		BufferedWriter out = commands;
		if (out == null) return;
		try {
			out.write(command);
			out.newLine();
			out.flush();
		} catch (IOException e) {
			Lumina.LOG.warn("Could not send {} to Spotify", command, e);
		}
	}
}
