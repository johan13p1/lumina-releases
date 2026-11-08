package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lumina.client.Lumina;
import dev.lumina.client.module.Category;
import dev.lumina.client.render.Shapes;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What stands for a module or category in the Lumina menu. Icons fill 16 menu units and are drawn from 32x32 pictures
 * in assets/lumina/textures/gui/module. When a menu unit is a single pixel, the 16x16 versions in .../module16 stay
 * sharp instead.
 */
final class ModuleIcons {
	private static final int SIZE = 16, HD = 32;
	/** Modules with a Lumina icon. */
	private static final Set<String> TEXTURED = Set.of(
		"fps", "cps", "coordinates", "keystrokes", "potion_effects", "ping", "scoreboard", "boss_bar", "minimap", "clock", "stopwatch",
		"memory", "momentum", "spotify",
		"crosshair", "attack_indicator", "hit_color", "damage_tint", "block_outline", "glint_colorizer", "shiny_pots", "item_physics",
		"skin_layers_3d", "name_tags", "particle_changer", "motion_blur", "menu_blur", "hitbox", "fullbright", "cosmetics",
		"zoom", "freelook", "fov_changer",
		"waypoints", "weather_changer", "chunk_borders",
		"chat", "tab_editor", "auto_text", "better_sounds", "inventory", "gui_scale", "client");
	/**
	 * Modules shown as an original item. At 32x32 it is Lumina's own drawing (tools/ItemIcons.java); at 16x16 it is
	 * the game's own picture, with its file in textures/item.
	 */
	private static final Map<String, String> ORIGINAL_ITEMS = Map.of(
		"coordinates", "compass_16",
		"zoom", "spyglass",
		"name_tags", "name_tag");
	private static final Map<String, ItemStack> STACKS = new HashMap<>();
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final Map<String, @Nullable Identifier> ORIGINALS = new HashMap<>();

	private ModuleIcons() {
	}

	static void module(GuiGraphicsExtractor g, String id, float x, float y, float a) {
		boolean hd = hd();
		Identifier texture = !TEXTURED.contains(id) ? null
			: hd ? texture("module", id)
			: ORIGINAL_ITEMS.containsKey(id) ? originalItem(id)
			: texture("module16", id);
		if (texture != null) blit(g, texture, hd ? HD : SIZE, x, y, a);
		else McUI.item(g, STACKS.computeIfAbsent(id, k -> new ItemStack(item(k))), x, y, a);
	}

	static void category(GuiGraphicsExtractor g, @Nullable Category category, float x, float y, float a) {
		String name = "category_" + (category == null ? "all" : category.name().toLowerCase(Locale.ROOT));
		boolean hd = hd();
		blit(g, texture(hd ? "module" : "module16", name), hd ? HD : SIZE, x, y, a);
	}

	/**
	 * Whether a menu unit is at least two pixels, so the 32x32 icons keep their single-pixel lines. At 1080p it is two,
	 * and the icons show 1:1.
	 */
	private static boolean hd() {
		return Math.round(McUI.menuScale * Shapes.guiScale()) >= 2;
	}

	/** Only drawn if the game's own picture can't be read. Called on first draw: the item registry doesn't exist yet when the mod's classes load. */
	private static Item item(String id) {
		return switch (id) {
			case "coordinates" -> Items.COMPASS;
			case "zoom" -> Items.SPYGLASS;
			case "name_tags" -> Items.NAME_TAG;
			default -> Items.BOOK;
		};
	}

	private static Identifier texture(String folder, String name) {
		return TEXTURES.computeIfAbsent(folder + "/" + name, path -> Identifier.fromNamespaceAndPath("lumina", "textures/gui/" + path + ".png"));
	}

	/** The 16x16 icon of an original item: its picture from the game's own files, which a texture pack can't change. */
	private static @Nullable Identifier originalItem(String module) {
		if (!ORIGINALS.containsKey(module)) ORIGINALS.put(module, loadOriginalItem(module));
		return ORIGINALS.get(module);
	}

	private static @Nullable Identifier loadOriginalItem(String module) {
		String file = ORIGINAL_ITEMS.get(module);
		if (file == null) return null;
		try {
			NativeImage image = vanillaItem(file);
			if (image == null) return null;
			Identifier id = Identifier.fromNamespaceAndPath("lumina", "gui/original_item/" + module);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			return id;
		} catch (IOException e) {
			Lumina.LOG.warn("Could not read the original icon of {}", module, e);
			return null;
		}
	}

	private static @Nullable NativeImage vanillaItem(String file) throws IOException {
		IoSupplier<InputStream> resource = Minecraft.getInstance().getVanillaPackResources()
			.getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace("textures/item/" + file + ".png"));
		if (resource == null) return null;
		try (InputStream in = resource.get()) {
			return NativeImage.read(in);
		}
	}

	/** Fills 16 menu units with a picture of the given size. Appears at the same point of the fade-in as the items next to it, which can't fade. */
	private static void blit(GuiGraphicsExtractor g, Identifier texture, int size, float x, float y, float a) {
		if (a <= 0.6f) return;
		g.blit(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), 0, 0, SIZE, SIZE, size, size, size, size, 0xFFFFFFFF);
	}
}
