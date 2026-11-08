package dev.lumina.client.feature.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.Lumina;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Tints the screen edges red when your health is low. */
public final class DamageTintModule extends Module {
	private static final Identifier VIGNETTE = Lumina.id("vignette");
	private static boolean textureReady;

	private final NumberSetting threshold;
	private final NumberSetting strength;
	private final ColorSetting color;
	private final Anim level = new Anim(0, 4);

	public DamageTintModule() {
		super("damage_tint", "Damage Tint", "Red screen edges when you are low on health", Category.VISUAL, Icons.HEART_PULSE, false);
		threshold = add(new NumberSetting("threshold", "Start Below", 10, 1, 20, 1).suffix(" HP"));
		strength = add(new NumberSetting("strength", "Strength", 0.75, 0.1, 1, 0.05));
		color = add(new ColorSetting("color", "Color", 0xFFB01818, false));
	}

	private static void ensureTexture() {
		if (textureReady) return;
		textureReady = true;
		int size = 128;
		NativeImage img = new NativeImage(size, size, false);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = (x + 0.5f) / size * 2 - 1, dy = (y + 0.5f) / size * 2 - 1;
				float d = (float) Math.sqrt(dx * dx * 0.85f + dy * dy * 0.85f);
				float a = Math.max(0, Math.min(1, (d - 0.45f) / 0.7f));
				a = a * a * (3 - 2 * a);
				img.setPixel(x, y, ARGB.color(Math.round(a * 255), 0xFFFFFF));
			}
		}
		DynamicTexture tex = new DynamicTexture(() -> "lumina:vignette", img) {
			{
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		};
		net.minecraft.client.Minecraft.getInstance().getTextureManager().register(VIGNETTE, tex);
	}

	public void render(GuiGraphicsExtractor g) {
		var player = mc().player;
		if (player == null || player.isCreative() || player.isSpectator()) return;
		float health = player.getHealth();
		float target = health < threshold.getFloat() ? 1 - health / threshold.getFloat() : 0;
		float v = level.update(target);
		if (v < 0.01f) return;
		ensureTexture();
		int c = ARGB.color(Math.round(255 * v * strength.getFloat()), color.color());
		g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, g.guiWidth(), g.guiHeight(), 128, 128, 128, 128, c);
	}
}
