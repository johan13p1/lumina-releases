package dev.lumina.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.Lumina;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;

/**
 * Lumina's loading screen (replaces "Mojang Studios"). It shows while resources load, so the image
 * is read straight from the mod jar instead of through the resource manager, and only vanilla
 * pipelines are used: Lumina's own shaders aren't loaded yet at that point.
 */
public final class LoadingArt {
	public static final int BACKGROUND = 0xFF09090B;
	private static final Identifier ID = Lumina.id("loading_art");
	private static int texW = -1, texH;

	private LoadingArt() {
	}

	private static boolean ensure() {
		if (texW > 0) return true;
		if (texW == 0) return false;
		texW = 0;
		try (InputStream in = LoadingArt.class.getResourceAsStream("/assets/lumina/textures/gui/loading.png")) {
			if (in == null) return false;
			NativeImage image = NativeImage.read(in);
			int w = image.getWidth(), h = image.getHeight();
			DynamicTexture tex = new DynamicTexture(() -> ID.toString(), image) {
				{
					this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
			};
			Minecraft.getInstance().getTextureManager().register(ID, tex);
			texW = w;
			texH = h;
			return true;
		} catch (Exception e) {
			Lumina.LOG.warn("Could not load the loading screen art", e);
			return false;
		}
	}

	/** Logo lockup centered slightly above the middle of the screen; {@code alpha} fades it. */
	public static void drawLogo(GuiGraphics g, float alpha) {
		if (!ensure()) return;
		float w = g.guiWidth(), h = g.guiHeight();
		float logoH = Math.min(w * 0.75f, h) * 0.17f;
		float logoW = logoH * texW / texH;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((w - logoW) / 2f, h * 0.47f - logoH / 2f);
		pose.scale(logoW / texW, logoH / texH);
		g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0, 0, texW, texH, texW, texH, texW, texH, ARGB.white(alpha));
		pose.popMatrix();
	}

	/**
	 * Progress bar under the logo, built like the Lumina menu: a sunken track (dark top and left edge, faint light
	 * bottom and right edge) that an off-white block fills, lit on top and shaded at the bottom. It is laid out in
	 * screen pixels, in steps of one "bar pixel" (two screen pixels at 1080p), so it stays crisp at every GUI scale
	 * and grows smoothly.
	 */
	public static void drawProgress(GuiGraphics g, float progress, float alpha) {
		float scale = Minecraft.getInstance().getWindow().getGuiScale();
		float w = g.guiWidth(), h = g.guiHeight(), size = Math.min(w * 0.75f, h);
		int px = Math.max(1, Math.round(h * scale / 540f));
		int barW = Math.round(size * 0.34f * scale / px) * px, barH = 6 * px;
		int x0 = Math.round((w * scale - barW) / 2f), y0 = Math.round((h * 0.47f + size * 0.17f / 2f + h * 0.07f) * scale);
		int fill = Math.round((barW - 2 * px) * Math.max(0, Math.min(1, progress)));
		int ix = x0 + px, iy = y0 + px, ih = barH - 2 * px;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(1 / scale, 1 / scale);
		// every part is its own opaque rectangle, so the whole bar fades evenly
		g.fill(x0, y0, x0 + barW, y0 + px, fade(0xFF0B0B0D, alpha));
		g.fill(x0, y0 + px, x0 + px, y0 + barH, fade(0xFF0B0B0D, alpha));
		g.fill(x0 + px, y0 + barH - px, x0 + barW, y0 + barH, fade(0xFF333335, alpha));
		g.fill(x0 + barW - px, y0 + px, x0 + barW, y0 + barH - px, fade(0xFF333335, alpha));
		g.fill(ix + fill, iy, x0 + barW - px, iy + ih, fade(0xFF19191C, alpha));
		if (fill > 0) {
			g.fill(ix, iy, ix + fill, iy + px, fade(0xFFFFFFFF, alpha));
			g.fill(ix, iy + px, ix + fill, iy + ih - px, fade(0xFFEDEDED, alpha));
			g.fill(ix, iy + ih - px, ix + fill, iy + ih, fade(0xFFB4B4B9, alpha));
		}
		pose.popMatrix();
	}

	/** The color with its alpha scaled by the screen's fade. */
	private static int fade(int argb, float alpha) {
		return ARGB.color(Math.round(ARGB.alpha(argb) * alpha), argb);
	}
}
