package dev.lumina.client.gui;

import dev.lumina.client.render.font.Fonts;
import dev.lumina.client.render.font.TtfFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers shared by Lumina's screens. */
public final class UI {
	public static final float TEXT = 8.5f;
	public static final float SMALL = 7.5f;
	public static final float TITLE = 12f;

	private UI() {
	}

	public static boolean hover(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Draws text vertically centered on {@code cy} using the cap height; cached as a whole line. */
	public static void text(GuiGraphicsExtractor g, TtfFont font, String s, float x, float cy, float size, int color) {
		float top = cy - font.capHeight(size) / 2f - (font.ascent(size) - font.capHeight(size));
		font.drawLine(g, s, x, top, size, color);
	}

	public static void textRight(GuiGraphicsExtractor g, TtfFont font, String s, float right, float cy, float size, int color) {
		text(g, font, s, right - font.width(s, size), cy, size, color);
	}

	public static void textCenter(GuiGraphicsExtractor g, TtfFont font, String s, float cx, float cy, float size, int color) {
		text(g, font, s, cx - font.width(s, size) / 2f, cy, size, color);
	}

	/** Icons are drawn centered on (cx, cy). */
	public static void icon(GuiGraphicsExtractor g, String icon, float cx, float cy, float size, int color) {
		TtfFont f = Fonts.ICONS;
		float w = f.width(icon, size);
		float top = cy - f.lineHeight(size) / 2f;
		f.draw(g, icon, cx - w / 2f, top, size, color);
	}
}
