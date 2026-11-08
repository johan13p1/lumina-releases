package dev.lumina.client.feature.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lumina.client.mixin.OverlayTextureAccessor;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Changes the red flash entities get when they are hit. */
public final class HitColorModule extends Module {
	private final ColorSetting color;
	private final NumberSetting intensity;

	public HitColorModule() {
		super("hit_color", "Hit Color", "Change the color entities flash when hit", Category.VISUAL, Icons.DROPLET, false);
		color = add(new ColorSetting("color", "Color", 0xFFFF0000, false).onChange(c -> apply()));
		intensity = add(new NumberSetting("intensity", "Intensity", 0.3, 0.05, 1, 0.05).onChange(v -> apply()));
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		apply();
	}

	@Override
	public void tick() {
		if (enabled() && color.chroma() && mc().level != null && mc().level.getGameTime() % 2 == 0) apply();
	}

	private void apply() {
		if (mc().gameRenderer == null) return;
		DynamicTexture tex = ((OverlayTextureAccessor) mc().gameRenderer.overlayTexture()).lumina$texture();
		NativeImage img = tex.getPixels();
		if (img == null) return;
		int argb = enabled()
			? (Math.round((1 - intensity.getFloat()) * 255) << 24) | (color.color() & 0xFFFFFF)
			: 0xB2FF0000;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) img.setPixel(x, y, argb);
		}
		tex.upload();
	}
}
