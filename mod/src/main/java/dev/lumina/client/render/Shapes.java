package dev.lumina.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lumina.client.Lumina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;

/**
 * 2D primitives used by all of Lumina's UI. Every method takes GUI units as floats and draws in
 * physical pixels internally, so edges stay crisp at every GUI scale and animations can move
 * things by fractions of a GUI pixel.
 *
 * <p>Everything except gradients goes through the one rounded pipeline with the same texture, even
 * plain rectangles, so consecutive shapes end up in a single GUI draw call.
 */
public final class Shapes {
	private static final Identifier WHITE = Lumina.id("white");
	private static boolean whiteReady;

	private Shapes() {
	}

	private static void ensureWhite() {
		if (whiteReady) return;
		whiteReady = true;
		NativeImage image = new NativeImage(4, 4, false);
		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 4; y++) image.setPixel(x, y, 0xFFFFFFFF);
		}
		Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "lumina:white", image));
	}

	public static float guiScale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	private static int px(float v, float s) {
		return Math.round(v * s);
	}

	private static Matrix3x2fStack begin(GuiGraphics g, float s) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		return pose;
	}

	public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
		if (w <= 0 || h <= 0 || ARGB.alpha(color) == 0) return;
		float s = guiScale();
		Matrix3x2fStack pose = begin(g, s);
		fill(g, px(x, s), px(y, s), px(x + w, s), px(y + h, s), color);
		pose.popMatrix();
	}

	/** Vertical gradient from {@code top} to {@code bottom}. */
	public static void gradientV(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
		if (w <= 0 || h <= 0) return;
		float s = guiScale();
		Matrix3x2fStack pose = begin(g, s);
		g.fillGradient(px(x, s), px(y, s), px(x + w, s), px(y + h, s), top, bottom);
		pose.popMatrix();
	}

	/** Horizontal gradient, drawn as physical-pixel columns. Meant for small areas like color pickers. */
	public static void gradientH(GuiGraphics g, float x, float y, float w, float h, int left, int right) {
		if (w <= 0 || h <= 0) return;
		float s = guiScale();
		Matrix3x2fStack pose = begin(g, s);
		int x0 = px(x, s), x1 = px(x + w, s), y0 = px(y, s), y1 = px(y + h, s);
		int span = Math.max(1, x1 - x0);
		for (int i = 0; i < span; i++) {
			int c = ARGB.srgbLerp(span == 1 ? 0 : i / (float) (span - 1), left, right);
			g.fill(x0 + i, y0, x0 + i + 1, y1, c);
		}
		pose.popMatrix();
	}

	public static void outline(GuiGraphics g, float x, float y, float w, float h, float t, int color) {
		rect(g, x, y, w, t, color);
		rect(g, x, y + h - t, w, t, color);
		rect(g, x, y + t, t, h - 2 * t, color);
		rect(g, x + w - t, y + t, t, h - 2 * t, color);
	}

	public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
		if (w <= 0 || h <= 0 || ARGB.alpha(color) == 0) return;
		float s = guiScale();
		int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
		int rr = Math.min(px(r, s), Math.min(x1 - x0, y1 - y0) / 2);
		Matrix3x2fStack pose = begin(g, s);
		if (rr <= 0) {
			fill(g, x0, y0, x1, y1, color);
		} else {
			fill(g, x0 + rr, y0, x1 - rr, y1, color);
			fill(g, x0, y0 + rr, x0 + rr, y1 - rr, color);
			fill(g, x1 - rr, y0 + rr, x1, y1 - rr, color);
			corners(g, x0, y0, x1, y1, rr, 0, color);
		}
		pose.popMatrix();
	}

	/** Rounded rect where each corner can be switched between rounded and square. */
	public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int color, boolean tl, boolean tr, boolean bl, boolean br) {
		if (w <= 0 || h <= 0 || ARGB.alpha(color) == 0) return;
		float s = guiScale();
		int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
		int rr = Math.min(px(r, s), Math.min(x1 - x0, y1 - y0) / 2);
		Matrix3x2fStack pose = begin(g, s);
		if (rr <= 0) {
			fill(g, x0, y0, x1, y1, color);
		} else {
			ensureWhite();
			fill(g, x0 + rr, y0, x1 - rr, y1, color);
			fill(g, x0, y0 + rr, x0 + rr, y1 - rr, color);
			fill(g, x1 - rr, y0 + rr, x1, y1 - rr, color);
			if (tl) quad(g, x0, y0, rr, rr, 0, 1, -1, 1, -1, color); else fill(g, x0, y0, x0 + rr, y0 + rr, color);
			if (tr) quad(g, x1 - rr, y0, rr, rr, 0, 0, 1, 1, -1, color); else fill(g, x1 - rr, y0, x1, y0 + rr, color);
			if (bl) quad(g, x0, y1 - rr, rr, rr, 0, 1, -1, 0, 1, color); else fill(g, x0, y1 - rr, x0 + rr, y1, color);
			if (br) quad(g, x1 - rr, y1 - rr, rr, rr, 0, 0, 1, 0, 1, color); else fill(g, x1 - rr, y1 - rr, x1, y1, color);
		}
		pose.popMatrix();
	}

	/** Rounded outline of thickness {@code t} (GUI units, use {@link #hairline()} for one physical pixel). */
	public static void roundOutline(GuiGraphics g, float x, float y, float w, float h, float r, float t, int color) {
		if (w <= 0 || h <= 0 || ARGB.alpha(color) == 0) return;
		float s = guiScale();
		int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
		int rr = Math.min(px(r, s), Math.min(x1 - x0, y1 - y0) / 2);
		int tt = Math.max(1, px(t, s));
		Matrix3x2fStack pose = begin(g, s);
		int edge = Math.max(rr, 0);
		fill(g, x0 + edge, y0, x1 - edge, y0 + tt, color);
		fill(g, x0 + edge, y1 - tt, x1 - edge, y1, color);
		fill(g, x0, y0 + Math.max(edge, tt), x0 + tt, y1 - Math.max(edge, tt), color);
		fill(g, x1 - tt, y0 + Math.max(edge, tt), x1, y1 - Math.max(edge, tt), color);
		if (rr > 0) {
			int code = rr - tt <= 0 ? 0 : Math.max(1, Math.min(1000, Math.round(1000f * (rr - tt) / rr)));
			corners(g, x0, y0, x1, y1, rr, code, color);
		}
		pose.popMatrix();
	}

	/** Soft drop shadow around a rounded rect. */
	public static void shadow(GuiGraphics g, float x, float y, float w, float h, float r, float blur, int color) {
		if (ARGB.alpha(color) == 0 || blur <= 0) return;
		ensureWhite();
		float s = guiScale();
		int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
		int rr = Math.min(px(r, s), Math.min(x1 - x0, y1 - y0) / 2);
		int b = Math.max(1, px(blur, s));
		int ext = rr + b;
		int code = 1001 + Math.round(999f * rr / ext);
		Matrix3x2fStack pose = begin(g, s);
		// corners
		quad(g, x0 - b, y0 - b, ext, ext, code, 1, -1, 1, -1, color);
		quad(g, x1 - rr, y0 - b, ext, ext, code, 0, 1, 1, -1, color);
		quad(g, x0 - b, y1 - rr, ext, ext, code, 1, -1, 0, 1, color);
		quad(g, x1 - rr, y1 - rr, ext, ext, code, 0, 1, 0, 1, color);
		// edges: the local coordinate only runs across the edge
		int innerW = x1 - x0 - 2 * rr, innerH = y1 - y0 - 2 * rr;
		if (innerW > 0) {
			quad(g, x0 + rr, y0 - b, innerW, ext, code, 0, 0, 1, -1, color);
			quad(g, x0 + rr, y1 - rr, innerW, ext, code, 0, 0, 0, 1, color);
		}
		if (innerH > 0) {
			quad(g, x0 - b, y0 + rr, ext, innerH, code, 1, -1, 0, 0, color);
			quad(g, x1 - rr, y0 + rr, ext, innerH, code, 0, 1, 0, 0, color);
		}
		if (innerW > 0 && innerH > 0) fill(g, x0 + rr, y0 + rr, x1 - rr, y1 - rr, color);
		pose.popMatrix();
	}

	public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
		if (radius <= 0 || ARGB.alpha(color) == 0) return;
		ensureWhite();
		float s = guiScale();
		int d = Math.max(1, px(radius * 2, s));
		int x0 = px(cx, s) - d / 2, y0 = px(cy, s) - d / 2;
		Matrix3x2fStack pose = begin(g, s);
		quad(g, x0, y0, d, d, 0, -1, 2, -1, 2, color);
		pose.popMatrix();
	}

	public static void ring(GuiGraphics g, float cx, float cy, float radius, float thickness, int color) {
		if (radius <= 0 || ARGB.alpha(color) == 0) return;
		ensureWhite();
		float s = guiScale();
		int d = Math.max(1, px(radius * 2, s));
		int x0 = px(cx, s) - d / 2, y0 = px(cy, s) - d / 2;
		int code = Math.max(1, Math.min(1000, Math.round(1000f * (1f - thickness * 2 / (radius * 2)))));
		Matrix3x2fStack pose = begin(g, s);
		quad(g, x0, y0, d, d, code, -1, 2, -1, 2, color);
		pose.popMatrix();
	}

	/** Solid rectangle in physical pixels through the rounded pipeline (local coordinate 0 = inside). */
	private static void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		ensureWhite();
		quad(g, x0, y0, x1 - x0, y1 - y0, 0, 0, 0, 0, 0, color);
	}

	/** Width of one physical pixel in GUI units. */
	public static float hairline() {
		return 1f / guiScale();
	}

	private static void corners(GuiGraphics g, int x0, int y0, int x1, int y1, int rr, int code, int color) {
		ensureWhite();
		quad(g, x0, y0, rr, rr, code, 1, -1, 1, -1, color);
		quad(g, x1 - rr, y0, rr, rr, code, 0, 1, 1, -1, color);
		quad(g, x0, y1 - rr, rr, rr, code, 1, -1, 0, 1, color);
		quad(g, x1 - rr, y1 - rr, rr, rr, code, 0, 1, 0, 1, color);
	}

	/**
	 * One quad of the rounded pipeline. {@code u}/{@code du} and {@code v}/{@code dv} describe the local
	 * coordinate at the left/top edge and how much it changes to the right/bottom edge.
	 */
	private static void quad(GuiGraphics g, int x, int y, int w, int h, int code, float u, int du, float v, int dv, int color) {
		if (w <= 0 || h <= 0) return;
		g.blit(LuminaPipelines.ROUNDED, WHITE, x, y, u + 4f * code, v, w, h, du, dv, 1, 1, color);
	}
}
