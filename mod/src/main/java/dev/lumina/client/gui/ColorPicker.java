package dev.lumina.client.gui;

import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.setting.ColorSetting;
import java.awt.Color;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Popover with saturation/brightness square, hue + alpha sliders, hex input and chroma switch. */
public final class ColorPicker {
	public static final float W = 156;
	private static final float PAD = 9;
	private static final float SQ_H = 84;
	private static final float BAR_H = 8;

	private final ColorSetting setting;
	public float x, y;
	private float hue, sat, bri, alpha;
	private int dragging; // 0 none, 1 square, 2 hue, 3 alpha
	private final TextInput hex;
	private final Anim open = new Anim(0, 18);
	private final Anim chromaAnim;

	public ColorPicker(ColorSetting setting, float x, float y) {
		this.setting = setting;
		this.x = x;
		this.y = y;
		int c = setting.get();
		float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
		hue = hsb[0];
		sat = hsb[1];
		bri = hsb[2];
		alpha = ((c >>> 24) & 0xFF) / 255f;
		hex = new TextInput(hexString(), 9, "#RRGGBB", this::onHex);
		chromaAnim = new Anim(setting.chroma() ? 1 : 0, 16);
		open.target(1);
	}

	public float height() {
		float h = PAD + SQ_H + 7 + BAR_H;
		if (setting.allowsAlpha()) h += 7 + BAR_H;
		return h + 8 + 16 + PAD;
	}

	public ColorSetting setting() {
		return setting;
	}

	public boolean contains(double mx, double my) {
		return UI.hover(mx, my, x, y, W, height());
	}

	private String hexString() {
		int c = setting.get();
		return setting.allowsAlpha() ? String.format(Locale.ROOT, "#%08X", c) : String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
	}

	private void onHex(String s) {
		String t = s.startsWith("#") ? s.substring(1) : s;
		try {
			if (t.length() == 6) {
				int rgb = Integer.parseInt(t, 16);
				applyRgb(rgb, Math.round(alpha * 255));
			} else if (t.length() == 8 && setting.allowsAlpha()) {
				long v = Long.parseLong(t, 16);
				applyRgb((int) (v & 0xFFFFFF), (int) ((v >> 24) & 0xFF));
			}
		} catch (NumberFormatException ignored) {
		}
	}

	private void applyRgb(int rgb, int a) {
		float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
		hue = hsb[0];
		sat = hsb[1];
		bri = hsb[2];
		alpha = a / 255f;
		setting.set((a << 24) | rgb);
	}

	private void apply() {
		int rgb = Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
		int a = setting.allowsAlpha() ? Math.round(alpha * 255) : 255;
		setting.set((a << 24) | rgb);
		if (!hex.focused()) hex.setText(hexString());
	}

	public void render(GuiGraphics g, double mx, double my) {
		float t = Anim.easeOutCubic(open.update());
		float a = t;
		float h = height();
		float oy = (1 - t) * -4;
		McUI.popup(g, x, y + oy, W, h, a);

		float ix = x + PAD, iy = y + oy + PAD, iw = W - PAD * 2;
		// saturation / brightness square
		int pure = Color.HSBtoRGB(hue, 1, 1) | 0xFF000000;
		Shapes.gradientH(g, ix, iy, iw, SQ_H, Theme.alpha(0xFFFFFFFF, a), Theme.alpha(pure, a));
		Shapes.gradientV(g, ix, iy, iw, SQ_H, 0x00000000, Theme.alpha(0xFF000000, a));
		float sx = ix + sat * iw, sy = iy + (1 - bri) * SQ_H;
		Shapes.ring(g, sx, sy, 3.5f, 1.2f, Theme.alpha(0xFFFFFFFF, a));
		Shapes.ring(g, sx, sy, 4.5f, 0.8f, Theme.alpha(0x66000000, a));
		iy += SQ_H + 7;

		// hue bar
		int[] stops = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
		float seg = iw / 6f;
		for (int i = 0; i < 6; i++) Shapes.gradientH(g, ix + seg * i, iy, seg + 0.5f, BAR_H, Theme.alpha(stops[i], a), Theme.alpha(stops[i + 1], a));
		slider(g, ix + hue * iw, iy, a);
		iy += BAR_H + 7;

		if (setting.allowsAlpha()) {
			checker(g, ix, iy, iw, BAR_H, a);
			int rgb = Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
			Shapes.gradientH(g, ix, iy, iw, BAR_H, Theme.alpha(rgb, 0), Theme.alpha(0xFF000000 | rgb, a));
			slider(g, ix + alpha * iw, iy, a);
			iy += BAR_H + 7;
		}
		iy += 1;

		// hex + chroma
		hex.renderVanilla(g, ix, iy, 76, 16, a);
		float ct = chromaAnim.update(setting.chroma());
		McUI.text(g, "Chroma", ix + 80, iy + 8, Theme.alpha(McUI.WHITE, a));
		McUI.toggle(g, ix + iw - McUI.SWITCH_W, iy + (16 - McUI.SWITCH_H) / 2f, ct, a);
	}

	private void slider(GuiGraphics g, float cx, float y, float a) {
		Shapes.rect(g, Math.round(cx) - 2, y - 1, 4, BAR_H + 2, Theme.alpha(0xFF000000, a));
		Shapes.rect(g, Math.round(cx) - 1, y, 2, BAR_H, Theme.alpha(0xFFFFFFFF, a));
	}

	static void checker(GuiGraphics g, float x, float y, float w, float h, float a) {
		Shapes.rect(g, x, y, w, h, Theme.alpha(0xFFCCCCCC, a));
		float c = h / 2f;
		for (float cx = 0; cx < w; cx += c) {
			for (float cy = 0; cy < h; cy += c) {
				if ((((int) (cx / c)) + ((int) (cy / c))) % 2 == 0) {
					Shapes.rect(g, x + cx, y + cy, Math.min(c, w - cx), Math.min(c, h - cy), Theme.alpha(0xFF888888, a));
				}
			}
		}
	}

	public boolean mouseClicked(double mx, double my, int button) {
		if (!contains(mx, my)) return false;
		float ix = x + PAD, iy = y + PAD, iw = W - PAD * 2;
		hex.setFocused(false);
		if (UI.hover(mx, my, ix - 2, iy - 2, iw + 4, SQ_H + 4)) {
			dragging = 1;
		} else if (UI.hover(mx, my, ix - 2, iy + SQ_H + 5, iw + 4, BAR_H + 4)) {
			dragging = 2;
		} else if (setting.allowsAlpha() && UI.hover(mx, my, ix - 2, iy + SQ_H + 7 + BAR_H + 5, iw + 4, BAR_H + 4)) {
			dragging = 3;
		} else {
			float rowY = iy + SQ_H + 7 + BAR_H + 7 + (setting.allowsAlpha() ? BAR_H + 7 : 0) + 1;
			if (UI.hover(mx, my, ix, rowY, 76, 16)) {
				hex.setFocused(true);
			} else if (UI.hover(mx, my, ix + 80, rowY, iw - 80, 16)) {
				setting.setChroma(!setting.chroma());
			}
		}
		mouseDragged(mx, my);
		return true;
	}

	public void mouseDragged(double mx, double my) {
		float ix = x + PAD, iy = y + PAD, iw = W - PAD * 2;
		switch (dragging) {
			case 1 -> {
				sat = clamp((float) ((mx - ix) / iw));
				bri = 1 - clamp((float) ((my - iy) / SQ_H));
				apply();
			}
			case 2 -> {
				hue = clamp((float) ((mx - ix) / iw));
				apply();
			}
			case 3 -> {
				alpha = clamp((float) ((mx - ix) / iw));
				apply();
			}
			default -> {
			}
		}
	}

	public void mouseReleased() {
		dragging = 0;
	}

	public boolean keyPressed(KeyEvent e) {
		return hex.keyPressed(e);
	}

	public boolean charTyped(CharacterEvent e) {
		return hex.charTyped(e);
	}

	public boolean typing() {
		return hex.focused();
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}
}
