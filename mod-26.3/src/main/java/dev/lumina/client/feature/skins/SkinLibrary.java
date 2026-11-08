package dev.lumina.client.feature.skins;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.lumina.client.Lumina;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * Your skins in lumina/skins: PNG files in the Minecraft skin layout (64x64, or the old 64x32).
 * Each one is loaded once into a small texture; slim (Alex) arms are detected from the pixels and
 * can be overridden per file (kept in skins/variants.json).
 */
public final class SkinLibrary {
	/** {@code hash} is the SHA-1 of the file, so the same skin is recognized under any name. */
	public record Entry(Path file, String name, String hash) {
	}

	/** A loaded skin texture. */
	public record Texture(Identifier id, PlayerModelType detected) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private final Map<String, Texture> textures = new HashMap<>();
	private final Map<String, String> failed = new HashMap<>();
	private final Map<String, PlayerModelType> variants = new HashMap<>();

	public Path dir() {
		return Lumina.config().dir().resolve("skins");
	}

	private Path variantsFile() {
		return dir().resolve("variants.json");
	}

	public static boolean isSkinFile(Path p) {
		return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
	}

	public void reload() {
		entries.clear();
		try {
			Files.createDirectories(dir());
		} catch (IOException ignored) {
		}
		try (Stream<Path> files = Files.list(dir())) {
			files.filter(p -> Files.isRegularFile(p) && isSkinFile(p)).sorted().forEach(p -> {
				try {
					String file = p.getFileName().toString();
					entries.add(new Entry(p, prettyName(file), sha1(Files.readAllBytes(p))));
				} catch (IOException e) {
					Lumina.LOG.warn("Could not read skin {}", p, e);
				}
			});
		} catch (IOException e) {
			Lumina.LOG.warn("Could not list skins in {}", dir(), e);
		}
		loadVariants();
	}

	public List<Entry> all() {
		return entries;
	}

	public @Nullable Entry byHash(@Nullable String hash) {
		if (hash == null) return null;
		for (Entry e : entries) if (e.hash.equals(hash)) return e;
		return null;
	}

	public @Nullable Entry byFile(String fileName) {
		for (Entry e : entries) if (e.file.getFileName().toString().equals(fileName)) return e;
		return null;
	}

	static String prettyName(String file) {
		String base = file.replaceFirst("\\.[^.]+$", "").replace('_', ' ').trim();
		return base.isEmpty() ? file : base;
	}

	static String sha1(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------
	// textures (render thread)
	// ------------------------------------------------------------------

	/** The skin's texture, or null when the file is not a valid skin ({@link #error} says why). */
	public @Nullable Texture texture(Entry entry) {
		Texture t = textures.get(entry.hash);
		if (t != null || failed.containsKey(entry.hash)) return t;
		try {
			t = load(Files.readAllBytes(entry.file), entry.hash);
			textures.put(entry.hash, t);
		} catch (IOException | RuntimeException e) {
			failed.put(entry.hash, e.getMessage());
			Lumina.LOG.warn("Could not load skin {}: {}", entry.file, e.getMessage());
		}
		return t;
	}

	public @Nullable String error(Entry entry) {
		return failed.get(entry.hash);
	}

	/** Slim or classic arms: what you picked for this file, else what the pixels say. */
	public PlayerModelType model(Entry entry) {
		PlayerModelType picked = variants.get(entry.file.getFileName().toString());
		if (picked != null) return picked;
		Texture t = texture(entry);
		return t == null ? PlayerModelType.WIDE : t.detected;
	}

	public void setModel(Entry entry, PlayerModelType model) {
		Texture t = texture(entry);
		String key = entry.file.getFileName().toString();
		if (t != null && t.detected == model) variants.remove(key);
		else variants.put(key, model);
		saveVariants();
	}

	private static Texture load(byte[] png, String hash) throws IOException {
		NativeImage image = NativeImage.read(png);
		int w = image.getWidth(), h = image.getHeight();
		if (w != 64 || (h != 64 && h != 32)) {
			image.close();
			throw new IOException("A skin must be 64x64 pixels (this one is " + w + "x" + h + ")");
		}
		PlayerModelType detected = h == 64 && isSlim(image) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		image = processLikeVanilla(image);
		Identifier id = Lumina.id("skins/" + hash);
		NativeImage finalImage = image;
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, finalImage));
		return new Texture(id, detected);
	}

	/** Slim skins leave the last two columns of the arm's back face empty. */
	private static boolean isSlim(NativeImage image) {
		for (int y = 20; y < 32; y++) {
			for (int x = 54; x < 56; x++) if (ARGB.alpha(image.getPixel(x, y)) != 0) return false;
		}
		return true;
	}

	/** What the game does to downloaded skins: old 64x32 skins get their left limbs, the base layer is opaque. */
	private static NativeImage processLikeVanilla(NativeImage image) {
		boolean legacy = image.getHeight() == 32;
		if (legacy) {
			NativeImage full = new NativeImage(64, 64, true);
			full.copyFrom(image);
			image.close();
			image = full;
			full.fillRect(0, 32, 64, 32, 0);
			full.copyRect(4, 16, 16, 32, 4, 4, true, false);
			full.copyRect(8, 16, 16, 32, 4, 4, true, false);
			full.copyRect(0, 20, 24, 32, 4, 12, true, false);
			full.copyRect(4, 20, 16, 32, 4, 12, true, false);
			full.copyRect(8, 20, 8, 32, 4, 12, true, false);
			full.copyRect(12, 20, 16, 32, 4, 12, true, false);
			full.copyRect(44, 16, -8, 32, 4, 4, true, false);
			full.copyRect(48, 16, -8, 32, 4, 4, true, false);
			full.copyRect(40, 20, 0, 32, 4, 12, true, false);
			full.copyRect(44, 20, -8, 32, 4, 12, true, false);
			full.copyRect(48, 20, -16, 32, 4, 12, true, false);
			full.copyRect(52, 20, -8, 32, 4, 12, true, false);
		}
		opaque(image, 0, 0, 32, 16);
		opaque(image, 0, 16, 64, 32);
		opaque(image, 16, 48, 48, 64);
		return image;
	}

	private static void opaque(NativeImage image, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) image.setPixel(x, y, ARGB.opaque(image.getPixel(x, y)));
		}
	}

	// ------------------------------------------------------------------
	// files
	// ------------------------------------------------------------------

	/** Copies dropped or picked PNG files into lumina/skins. Returns the new entries. */
	public List<Entry> importFiles(List<Path> files) {
		List<Path> added = new ArrayList<>();
		for (Path src : files) {
			if (!Files.isRegularFile(src) || !isSkinFile(src)) continue;
			try {
				byte[] data = Files.readAllBytes(src);
				Entry existing = byHash(sha1(data));
				if (existing != null) {
					added.add(existing.file);
					continue;
				}
				added.add(write(data, src.getFileName().toString()));
			} catch (IOException e) {
				Lumina.LOG.warn("Could not import skin {}", src, e);
			}
		}
		reload();
		List<Entry> result = new ArrayList<>();
		for (Path p : added) {
			Entry e = byFile(p.getFileName().toString());
			if (e != null) result.add(e);
		}
		return result;
	}

	/** Stores a skin under {@code name} (made unique) unless the same skin is already there. */
	public Entry save(byte[] png, String name) throws IOException {
		Entry existing = byHash(sha1(png));
		if (existing != null) return existing;
		Path file = write(png, name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".png");
		reload();
		Entry e = byFile(file.getFileName().toString());
		if (e == null) throw new IOException("Could not save " + file);
		return e;
	}

	private Path write(byte[] data, String fileName) throws IOException {
		Files.createDirectories(dir());
		Path dest = dir().resolve(fileName);
		for (int i = 2; Files.exists(dest); i++) dest = dir().resolve(fileName.replaceFirst("(\\.[^.]+)$", " " + i + "$1"));
		Files.write(dest, data);
		return dest;
	}

	public void delete(Entry entry) {
		try {
			Files.deleteIfExists(entry.file);
		} catch (IOException e) {
			Lumina.LOG.warn("Could not delete skin {}", entry.file, e);
		}
		variants.remove(entry.file.getFileName().toString());
		saveVariants();
		reload();
	}

	public byte[] read(Entry entry) throws IOException {
		return Files.readAllBytes(entry.file);
	}

	private void loadVariants() {
		variants.clear();
		Path file = variantsFile();
		if (!Files.isRegularFile(file)) return;
		try {
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String key : json.keySet()) variants.put(key, "slim".equals(json.get(key).getAsString()) ? PlayerModelType.SLIM : PlayerModelType.WIDE);
		} catch (IOException | RuntimeException e) {
			Lumina.LOG.warn("Could not read {}", file, e);
		}
	}

	private void saveVariants() {
		JsonObject json = new JsonObject();
		variants.forEach((k, v) -> json.addProperty(k, v == PlayerModelType.SLIM ? "slim" : "classic"));
		try {
			Files.createDirectories(dir());
			Files.writeString(variantsFile(), json.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Lumina.LOG.warn("Could not save {}", variantsFile(), e);
		}
	}
}
