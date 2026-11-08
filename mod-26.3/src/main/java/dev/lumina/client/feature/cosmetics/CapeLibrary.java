package dev.lumina.client.feature.cosmetics;

import dev.lumina.client.Lumina;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * All capes you can pick: the ones shipped with Lumina (assets/lumina/capes in the mod jar) and
 * your own files in lumina/capes. Textures are decoded in the background on first use and freed
 * again when nothing has shown them for a while.
 */
public final class CapeLibrary {
	private static final long UNUSED_MS = 30_000;

	/** {@code id} is "lumina:file.png" for built-in capes and "user:file.png" for your own. */
	public record Entry(String id, String name, boolean bundled, Path file) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private final Map<String, Slot> slots = new HashMap<>();

	private static final class Slot {
		CompletableFuture<CapeTexture.Decoded> decoding;
		CapeTexture texture;
		boolean failed;
		long lastUsed;
	}

	public Path userDir() {
		return Lumina.config().dir().resolve("capes");
	}

	private static @Nullable Path bundledDir() {
		return FabricLoader.getInstance().getModContainer(Lumina.MOD_ID).flatMap(c -> c.findPath("assets/lumina/capes")).orElse(null);
	}

	private static boolean isCapeFile(Path p) {
		String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
		return n.endsWith(".png") || n.endsWith(".apng") || n.endsWith(".gif");
	}

	public void reload() {
		entries.clear();
		Path bundled = bundledDir();
		if (bundled != null) scan(bundled, true);
		try {
			Files.createDirectories(userDir());
		} catch (IOException ignored) {
		}
		scan(userDir(), false);
		// forget textures of capes that are gone
		slots.keySet().removeIf(id -> {
			if (find(id) != null) return false;
			Slot s = slots.get(id);
			if (s.texture != null) s.texture.release();
			return true;
		});
	}

	private void scan(Path dir, boolean bundled) {
		if (!Files.isDirectory(dir)) return;
		Map<String, Integer> order = bundled ? order(dir) : Map.of();
		Comparator<Path> listedFirst = Comparator.comparingInt(p -> order.getOrDefault(p.getFileName().toString(), Integer.MAX_VALUE));
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(CapeLibrary::isCapeFile).sorted(listedFirst.thenComparing(Comparator.<Path>naturalOrder())).forEach(p -> {
				String file = p.getFileName().toString();
				entries.add(new Entry((bundled ? "lumina:" : "user:") + file, prettyName(file), bundled, p));
			});
		} catch (IOException e) {
			Lumina.LOG.warn("Could not list capes in {}", dir, e);
		}
	}

	/** order.txt next to the built-in capes: one file name per line, listed capes come first (the launcher reads it too). */
	private static Map<String, Integer> order(Path dir) {
		Map<String, Integer> order = new HashMap<>();
		try {
			for (String line : Files.readAllLines(dir.resolve("order.txt"))) {
				line = line.strip();
				if (!line.isEmpty() && !line.startsWith("#")) order.putIfAbsent(line, order.size());
			}
		} catch (IOException ignored) {
			// no order file: alphabetical
		}
		return order;
	}

	static String prettyName(String file) {
		String base = file.replaceFirst("\\.[^.]+$", "").replace('_', ' ').replace('-', ' ').trim();
		StringBuilder sb = new StringBuilder();
		for (String word : base.split("\\s+")) {
			if (word.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return sb.isEmpty() ? file : sb.toString();
	}

	public List<Entry> all() {
		return entries;
	}

	public @Nullable Entry find(String id) {
		for (Entry e : entries) if (e.id.equals(id)) return e;
		return null;
	}

	/**
	 * The cape's textures, or null while they are still being decoded (or the file is broken).
	 * Render thread only.
	 */
	public @Nullable CapeTexture texture(Entry entry) {
		Slot slot = slots.computeIfAbsent(entry.id, k -> new Slot());
		slot.lastUsed = Util.getMillis();
		if (slot.texture != null || slot.failed) return slot.texture;
		startDecoding(slot, entry);
		if (!slot.decoding.isDone()) return null;
		upload(slot, entry);
		return slot.texture;
	}

	/**
	 * Starts decoding every cape in the background right away, so the Cosmetics page and other
	 * players' capes show at once instead of after the first look. {@link #uploadDecoded} then
	 * hands them to the GPU one per tick.
	 */
	public void preload() {
		for (Entry e : entries) startDecoding(slots.computeIfAbsent(e.id, k -> new Slot()), e);
	}

	/** Uploads one cape that finished decoding, if any. Render thread, once per tick. */
	public void uploadDecoded() {
		for (Entry e : entries) {
			Slot slot = slots.get(e.id);
			if (slot != null && slot.texture == null && !slot.failed && slot.decoding != null && slot.decoding.isDone()) {
				upload(slot, e);
				return;
			}
		}
	}

	private static void startDecoding(Slot slot, Entry entry) {
		if (slot.decoding != null || slot.texture != null || slot.failed) return;
		slot.decoding = CompletableFuture.supplyAsync(() -> {
			try (InputStream in = Files.newInputStream(entry.file)) {
				return CapeTexture.decode(in, entry.file.getFileName().toString());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, Util.backgroundExecutor());
	}

	private static void upload(Slot slot, Entry entry) {
		try {
			slot.texture = CapeTexture.upload(slot.decoding.join());
		} catch (RuntimeException e) {
			Lumina.LOG.warn("Could not load cape {}", entry.file, e);
			slot.failed = true;
		}
		slot.decoding = null;
	}

	public boolean failed(Entry entry) {
		Slot slot = slots.get(entry.id);
		return slot != null && slot.failed;
	}

	/** Frees textures nothing has used lately; the equipped cape keeps its own. */
	public void releaseUnused(@Nullable String keepId) {
		long now = Util.getMillis();
		for (Map.Entry<String, Slot> e : slots.entrySet()) {
			Slot s = e.getValue();
			if (s.texture != null && !e.getKey().equals(keepId) && now - s.lastUsed > UNUSED_MS) {
				s.texture.release();
				s.texture = null;
			}
		}
	}

	/** Copies dropped PNG/APNG/GIF files into lumina/capes. Returns how many were added. */
	public int importFiles(List<Path> files) {
		int added = 0;
		for (Path src : files) {
			if (!Files.isRegularFile(src) || !isCapeFile(src)) continue;
			try {
				Files.createDirectories(userDir());
				String name = src.getFileName().toString();
				Path dest = userDir().resolve(name);
				for (int i = 2; Files.exists(dest); i++) dest = userDir().resolve(name.replaceFirst("(\\.[^.]+)$", " " + i + "$1"));
				Files.copy(src, dest);
				added++;
			} catch (IOException e) {
				Lumina.LOG.warn("Could not import cape {}", src, e);
			}
		}
		if (added > 0) reload();
		return added;
	}

	/** Deletes one of your own capes. */
	public void delete(Entry entry) {
		if (entry.bundled) return;
		try {
			Files.deleteIfExists(entry.file);
		} catch (IOException e) {
			Lumina.LOG.warn("Could not delete cape {}", entry.file, e);
		}
		reload();
	}
}
