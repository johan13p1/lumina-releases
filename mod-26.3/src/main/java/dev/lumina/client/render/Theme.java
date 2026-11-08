package dev.lumina.client.render;

import net.minecraft.util.ARGB;

/** Lumina's color palette: near-black surfaces, soft grays, white highlights. */
public final class Theme {
	public static final int BACKDROP = 0x99060607;
	public static final int PANEL = 0xF20D0D0F;
	public static final int SIDEBAR = 0xFF0A0A0B;
	/** Lumina menu window, slightly see-through so the (blurred) game shows behind it. */
	public static final int MENU = 0xC70E0E10;
	public static final int MENU_SIDEBAR = 0x660A0A0B;
	/** Translucent fills for cards and setting groups on the menu window. */
	public static final int GLASS = 0x0DFFFFFF;
	public static final int GLASS_HOVER = 0x17FFFFFF;
	public static final int SURFACE = 0xFF151518;
	public static final int SURFACE_HOVER = 0xFF1C1C20;
	public static final int SURFACE_ACTIVE = 0xFF232328;
	public static final int BORDER = 0x1AFFFFFF;
	public static final int BORDER_STRONG = 0x33FFFFFF;
	public static final int TEXT = 0xFFEDEDF0;
	public static final int TEXT_DIM = 0xFF9A9AA3;
	public static final int TEXT_MUTED = 0xFF5E5E66;
	public static final int ACCENT = 0xFFF2F2F4;
	public static final int ACCENT_DIM = 0xFFB9B9C0;
	public static final int TRACK_OFF = 0xFF2A2A30;
	public static final int KNOB_OFF = 0xFF77777F;
	public static final int SHADOW = 0x66000000;
	public static final int DANGER = 0xFFE5484D;
	public static final int SUCCESS = 0xFF46A758;

	private Theme() {
	}

	public static int alpha(int color, float alpha) {
		return ARGB.color(Math.round(ARGB.alpha(color) * Math.max(0, Math.min(1, alpha))), color);
	}

	public static int mix(int a, int b, float t) {
		return ARGB.srgbLerp(Math.max(0, Math.min(1, t)), a, b);
	}
}
