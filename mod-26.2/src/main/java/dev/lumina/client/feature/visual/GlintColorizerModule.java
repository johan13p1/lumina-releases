package dev.lumina.client.feature.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.Lumina;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.NumberSetting;
import java.io.InputStream;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Recolors the enchantment glint by swapping its textures for tinted copies. */
public final class GlintColorizerModule extends Module {
	private final ColorSetting itemColor;
	private final BoolSetting separateArmor;
	private final ColorSetting armorColor;
	private final NumberSetting brightness;
	private final NumberSetting speed;
	private final NumberSetting strength;
	private AbstractTexture itemTex;
	private AbstractTexture armorTex;
	private boolean dirty = true;

	public GlintColorizerModule() {
		super("glint_colorizer", "Glint Colorizer", "Change the color of the enchantment glint", Category.VISUAL, Icons.SPARKLES, false);
		itemColor = add(new ColorSetting("color", "Glint Color", 0xFF7F5CFF, false).onChange(c -> dirty = true));
		separateArmor = add(new BoolSetting("separate_armor", "Separate Armor Color", false).onChange(b -> dirty = true));
		armorColor = add(new ColorSetting("armor_color", "Armor Glint Color", 0xFF7F5CFF, false).visibleWhen(separateArmor::on).onChange(c -> dirty = true));
		brightness = add(new NumberSetting("brightness", "Brightness", 1.2, 0.3, 2.5, 0.05).onChange(v -> dirty = true));
		speed = add(new NumberSetting("speed", "Glint Speed", 0.5, 0, 1, 0.05).describe("Vanilla accessibility option").onChange(v -> applyVanillaOptions()));
		strength = add(new NumberSetting("strength", "Glint Strength", 0.75, 0, 1, 0.05).describe("Vanilla accessibility option").onChange(v -> applyVanillaOptions()));
	}

	@Override
	public void onReady() {
		speed.load(new com.google.gson.JsonPrimitive(mc().options.glintSpeed().get()));
		strength.load(new com.google.gson.JsonPrimitive(mc().options.glintStrength().get()));
	}

	private void applyVanillaOptions() {
		if (mc().options == null) return;
		mc().options.glintSpeed().set(speed.get());
		mc().options.glintStrength().set(strength.get());
	}

	@Override
	protected void onEnable() {
		dirty = true;
	}

	@Override
	protected void onDisable() {
		restore();
	}

	@Override
	public void tick() {
		if (!enabled()) return;
		var tm = mc().getTextureManager();
		// a resource reload replaces our textures; also refresh for chroma
		boolean replaced = tm.getTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM) != itemTex || tm.getTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR) != armorTex;
		boolean chroma = (itemColor.chroma() || (separateArmor.on() && armorColor.chroma())) && mc().level != null && mc().level.getGameTime() % 2 == 0;
		if (dirty || replaced || chroma) apply();
	}

	private void apply() {
		dirty = false;
		int item = itemColor.color();
		int armor = separateArmor.on() ? armorColor.color() : item;
		itemTex = tinted(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM, item);
		armorTex = tinted(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR, armor);
	}

	private AbstractTexture tinted(Identifier id, int color) {
		var tm = mc().getTextureManager();
		try (InputStream in = mc().getResourceManager().open(id)) {
			NativeImage src = NativeImage.read(in);
			float b = brightness.getFloat();
			float cr = ARGB.red(color) / 255f, cg = ARGB.green(color) / 255f, cb = ARGB.blue(color) / 255f;
			for (int y = 0; y < src.getHeight(); y++) {
				for (int x = 0; x < src.getWidth(); x++) {
					int p = src.getPixel(x, y);
					float lum = Math.max(ARGB.red(p), Math.max(ARGB.green(p), ARGB.blue(p))) / 255f * b;
					int r = Math.min(255, Math.round(cr * lum * 255));
					int g = Math.min(255, Math.round(cg * lum * 255));
					int bl = Math.min(255, Math.round(cb * lum * 255));
					src.setPixel(x, y, ARGB.color(ARGB.alpha(p), r, g, bl));
				}
			}
			DynamicTexture tex = new DynamicTexture(() -> "lumina glint " + id, src) {
				{
					this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
				}
			};
			tm.register(id, tex);
			return tex;
		} catch (Exception e) {
			Lumina.LOG.warn("Could not recolor {}", id, e);
			return tm.getTexture(id);
		}
	}

	private void restore() {
		var tm = mc().getTextureManager();
		if (tm == null) return;
		tm.release(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
		tm.release(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR);
		itemTex = null;
		armorTex = null;
	}
}
