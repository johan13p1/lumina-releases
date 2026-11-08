package dev.lumina.client.gui;

import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.render.font.Fonts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Drawing helpers of the Lumina menu: Minecraft's square blocks with their light and dark edges,
 * in the graphite and off-white of the launcher. Coordinates are menu units; the menu scales them
 * by a whole number of physical pixels, so every edge is one sharp block pixel.
 */
public final class McUI {
	/** Primary text. */
	static final int WHITE = 0xFFEDEDED;
	public static final int GRAY = 0xFFA1A1A6;
	static final int DARK_GRAY = 0xFF85858B;
	static final int GREEN = 0xFF4CC38A;
	static final int RED = 0xFFEC7F84;
	/** Text of what is selected or waiting for input. */
	static final int ACTIVE = 0xFFFFFFFF;
	static final int LINE = 0x14EDEDED;
	static final int LINE_STRONG = 0x2EEDEDED;
	static final int ROW_HOVER = 0x0DEDEDED;
	static final int LINE_HEIGHT = 8;
	/** Body of a menu window; the blurred game shows through a little. */
	static final int BODY = 0xEB0F0F10;
	static final int BACKDROP = 0x50000000;
	private static final int SURFACE_HOVER = 0xFF1F1F21;
	private static final int RAISED = 0xFF29292C;
	private static final int ACCENT = 0xFFEDEDED;
	private static final int ON_ACCENT = 0xFF111112;
	/** The dark outer edge of a block, like the black edge of Minecraft's buttons. */
	private static final int EDGE = 0xFF0A0A0B;
	/** The lit top edge of a block. */
	private static final int LIGHT = 0x1CFFFFFF;
	/** The shaded bottom edge of a block. */
	private static final int SHADE = 0x40000000;
	private static final int MENU_W = 540, MENU_H = 330;
	private static final float TEXT_SIZE = 8.5f;
	private static final float TITLE_SIZE = 9.5f;

	static final int BUTTON_H = 20;
	static final int SMALL_BUTTON_H = 16;
	static final int SWITCH_W = 20, SWITCH_H = 10;
	static final int SLOT = 20;

	/** Menu units to GUI units, set by the menu each frame. */
	static float menuScale = 1;

	private static String tooltipText;
	private static double tooltipX, tooltipY;

	private McUI() {
	}

	/**
	 * GUI units per menu unit, the same for all menu windows. A whole number of physical pixels per menu
	 * unit keeps the pixel art sharp at every GUI scale.
	 */
	static float fitScale() {
		var window = Minecraft.getInstance().getWindow();
		int w = window.getWidth(), h = window.getHeight();
		int pixels = (int) Math.min(w * 0.72f / MENU_W, h * 0.8f / MENU_H);
		if (pixels < 2) pixels = (int) Math.min((float) w / (MENU_W + 8), (float) h / (MENU_H + 8));
		menuScale = Math.max(1, pixels) / Shapes.guiScale();
		return menuScale;
	}

	// ------------------------------------------------------------------
	// Text
	// ------------------------------------------------------------------

	static int width(String s) {
		return (int) Math.ceil(Fonts.REGULAR.width(s, TEXT_SIZE));
	}

	/** The start of {@code s} that fits into {@code maxW}, with an ellipsis if it had to be cut. */
	static String trim(String s, float maxW) {
		return Fonts.REGULAR.trimToWidth(s, TEXT_SIZE, maxW);
	}

	/** Word-wraps to at most {@code maxLines}; the last line gets an ellipsis if text is left over. */
	static List<String> wrap(String s, float maxW, int maxLines) {
		List<String> lines = new ArrayList<>();
		String[] words = s.strip().split("\\s+");
		StringBuilder line = new StringBuilder();
		int i = 0;
		while (i < words.length && lines.size() < maxLines - 1) {
			String next = line.isEmpty() ? words[i] : line + " " + words[i];
			if (width(next) > maxW && !line.isEmpty()) {
				lines.add(line.toString());
				line.setLength(0);
			} else {
				line.setLength(0);
				line.append(next);
				i++;
			}
		}
		for (; i < words.length; i++) line.append(line.isEmpty() ? "" : " ").append(words[i]);
		if (!line.isEmpty()) lines.add(trim(line.toString(), maxW));
		return lines;
	}

	/** Text with its top at {@code y}. */
	public static void textTop(GuiGraphics g, String s, float x, float y, int color) {
		text(g, s, x, y + (LINE_HEIGHT - 1) / 2f, color);
	}

	/** Text vertically centered on {@code cy}. */
	static void text(GuiGraphics g, String s, float x, float cy, int color) {
		UI.text(g, Fonts.REGULAR, s, x, cy, TEXT_SIZE, color);
	}

	static void textRight(GuiGraphics g, String s, float right, float cy, int color) {
		UI.textRight(g, Fonts.REGULAR, s, right, cy, TEXT_SIZE, color);
	}

	static void textCenter(GuiGraphics g, String s, float cx, float cy, int color) {
		UI.textCenter(g, Fonts.REGULAR, s, cx, cy, TEXT_SIZE, color);
	}

	/** A name: slightly larger and medium weight. */
	static void title(GuiGraphics g, String s, float x, float cy, int color) {
		UI.text(g, Fonts.MEDIUM, s, x, cy, TITLE_SIZE, color);
	}

	static void titleCenter(GuiGraphics g, String s, float cx, float cy, int color) {
		UI.textCenter(g, Fonts.MEDIUM, s, cx, cy, TITLE_SIZE, color);
	}

	/** A button label, centered in its box. */
	private static void label(GuiGraphics g, String s, float x, float y, float w, float h, int color) {
		UI.textCenter(g, Fonts.MEDIUM, trim(s, w - 6), x + w / 2f, y + h / 2f, TEXT_SIZE, color);
	}

	/** A small label at the mouse, drawn by {@link #flushTooltip} on top of everything; {@code mx}/{@code my} are in menu units. */
	static void tooltip(GuiGraphics g, String s, double mx, double my) {
		tooltipText = s;
		tooltipX = mx;
		tooltipY = my;
	}

	static void flushTooltip(GuiGraphics g, float a) {
		if (tooltipText == null) return;
		String s = tooltipText;
		tooltipText = null;
		float w = width(s) + 12, h = 16;
		var window = Minecraft.getInstance().getWindow();
		float x = Math.round(Math.min(tooltipX + 8, window.getGuiScaledWidth() / menuScale - w - 2));
		float y = Math.round(Math.max(2, tooltipY - h - 2));
		block(g, x, y, w, h, RAISED, EDGE, LIGHT, SHADE, a);
		text(g, s, x + 6, y + h / 2f, Theme.alpha(WHITE, a));
	}

	// ------------------------------------------------------------------
	// Blocks
	// ------------------------------------------------------------------

	/** A raised square block: a dark outer edge, a lit top edge and a shaded bottom edge. */
	private static void block(GuiGraphics g, float x, float y, float w, float h, int fill, int edge, int light, int shade, float a) {
		x = Math.round(x);
		y = Math.round(y);
		w = Math.round(w);
		h = Math.round(h);
		Shapes.outline(g, x, y, w, h, 1, Theme.alpha(edge, a));
		Shapes.rect(g, x + 1, y + 1, w - 2, h - 2, Theme.alpha(fill, a));
		Shapes.rect(g, x + 1, y + 1, w - 2, 1, Theme.alpha(light, a));
		Shapes.rect(g, x + 1, y + h - 2, w - 2, 1, Theme.alpha(shade, a));
	}

	/** A sunken square, like an inventory slot: dark at the top left, lit at the bottom right. */
	private static void inset(GuiGraphics g, float x, float y, float w, float h, int fill, float a) {
		x = Math.round(x);
		y = Math.round(y);
		w = Math.round(w);
		h = Math.round(h);
		Shapes.rect(g, x + 1, y + 1, w - 2, h - 2, Theme.alpha(fill, a));
		Shapes.rect(g, x, y, w - 1, 1, Theme.alpha(0x8C000000, a));
		Shapes.rect(g, x, y + 1, 1, h - 2, Theme.alpha(0x8C000000, a));
		Shapes.rect(g, x + 1, y + h - 1, w - 1, 1, Theme.alpha(0x1FEDEDED, a));
		Shapes.rect(g, x + w - 1, y + 1, 1, h - 2, Theme.alpha(0x1FEDEDED, a));
	}

	// ------------------------------------------------------------------
	// Widgets
	// ------------------------------------------------------------------

	/** A plain button; hovering lights up its edge, like Minecraft's buttons. */
	static void button(GuiGraphics g, float x, float y, float w, float h, String label, boolean hover, boolean enabled, float a) {
		boolean lit = enabled && hover;
		block(g, x, y, w, h, !enabled ? 0xFF161618 : lit ? RAISED : SURFACE_HOVER, lit ? WHITE : EDGE, enabled ? LIGHT : 0, SHADE, a);
		label(g, label, x, y, w, h, Theme.alpha(enabled ? WHITE : DARK_GRAY, a));
	}

	/** The one light button of a page. */
	static void primary(GuiGraphics g, float x, float y, float w, float h, String label, boolean hover, boolean enabled, float a) {
		if (!enabled) {
			button(g, x, y, w, h, label, false, false, a);
			return;
		}
		block(g, x, y, w, h, hover ? 0xFFFFFFFF : ACCENT, EDGE, 0x99FFFFFF, 0x38000000, a);
		label(g, label, x, y - 0.5f, w, h, Theme.alpha(ON_ACCENT, a));
	}

	/** One choice of several, or a tab: a raised block when active, plain text otherwise. */
	static void segment(GuiGraphics g, float x, float y, float w, float h, String label, boolean active, boolean hover, float a) {
		if (active) block(g, x, y, w, h, RAISED, EDGE, LIGHT, SHADE, a);
		else if (hover) Shapes.rect(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Theme.alpha(ROW_HOVER, a));
		label(g, label, x, y, w, h, Theme.alpha(active || hover ? WHITE : GRAY, a));
	}

	/** A sunken track with a block handle; the value sits to its left. */
	static void slider(GuiGraphics g, float x, float y, float w, float h, float progress, String label, boolean hover, float a) {
		x = Math.round(x);
		w = Math.round(w);
		float cy = Math.round(y + h / 2f);
		inset(g, x, cy - 2, w, 4, 0xFF161618, a);
		float handle = Math.round(x + (w - 6) * progress);
		if (handle > x + 1) Shapes.rect(g, x + 1, cy - 1, handle - x - 1, 2, Theme.alpha(0xB3EDEDED, a));
		block(g, handle, cy - 6, 6, 12, hover ? 0xFFFFFFFF : ACCENT, EDGE, 0x99FFFFFF, 0x38000000, a);
		textRight(g, label, x - 8, cy, Theme.alpha(WHITE, a));
	}

	/** A sunken dark field; its edge lights up while typing. */
	static void textField(GuiGraphics g, float x, float y, float w, float h, boolean focused, float a) {
		x = Math.round(x);
		y = Math.round(y);
		w = Math.round(w);
		h = Math.round(h);
		Shapes.rect(g, x + 1, y + 1, w - 2, h - 2, Theme.alpha(0xFF0A0A0B, a));
		Shapes.outline(g, x, y, w, h, 1, Theme.alpha(focused ? 0xCCEDEDED : LINE_STRONG, a));
	}

	/** Square on/off switch: a block that slides right and turns off-white; {@code t} is the animated on-state in [0, 1]. */
	static void toggle(GuiGraphics g, float x, float y, float t, float a) {
		x = Math.round(x);
		y = Math.round(y);
		Shapes.outline(g, x, y, SWITCH_W, SWITCH_H, 1, Theme.alpha(EDGE, a));
		Shapes.rect(g, x + 1, y + 1, SWITCH_W - 2, SWITCH_H - 2, Theme.alpha(Theme.mix(0xFF161618, 0xFF3A3A3E, t), a));
		float k = SWITCH_H - 2;
		float kx = Math.round(x + 1 + (SWITCH_W - SWITCH_H) * t);
		Shapes.rect(g, kx, y + 1, k, k, Theme.alpha(Theme.mix(0xFF5A5A5F, ACCENT, t), a));
		Shapes.rect(g, kx, y + 1, k, 1, Theme.alpha(0x40FFFFFF, a));
		Shapes.rect(g, kx, y + k, k, 1, Theme.alpha(0x40000000, a));
	}

	/** The slot behind a module's icon, like an inventory slot; lit while the module is on. */
	static void slot(GuiGraphics g, float x, float y, float size, boolean lit, float a) {
		inset(g, x, y, size, size, lit ? 0x1AEDEDED : 0x40000000, a);
	}

	/** A real Minecraft item. Items can't fade, so they wait until the menu is nearly faded in. */
	static void item(GuiGraphics g, ItemStack stack, float x, float y, float a) {
		if (a > 0.6f) g.renderItem(stack, Math.round(x), Math.round(y));
	}

	/** A card of a list or grid: a square tile whose edge lights up on hover and turns off-white when selected. */
	static void card(GuiGraphics g, float x, float y, float w, float h, boolean hover, boolean selected, float a) {
		x = Math.round(x);
		y = Math.round(y);
		w = Math.round(w);
		h = Math.round(h);
		Shapes.rect(g, x + 1, y + 1, w - 2, h - 2, Theme.alpha(selected ? 0x1AEDEDED : hover ? 0x12EDEDED : 0x0AEDEDED, a));
		Shapes.outline(g, x, y, w, h, 1, Theme.alpha(selected ? 0xD9EDEDED : hover ? 0x4DEDEDED : LINE, a));
	}

	/** A menu that lies over everything else. */
	static void popup(GuiGraphics g, float x, float y, float w, float h, float a) {
		Shapes.shadow(g, x, y + 2, w, h, 0, 8, Theme.alpha(0x59000000, a));
		block(g, x, y, w, h, SURFACE_HOVER, EDGE, LIGHT, 0, a);
	}

	/** A small square box in the given colors. */
	static void frame(GuiGraphics g, float x, float y, float w, float h, int fill, int border, float a) {
		x = Math.round(x);
		y = Math.round(y);
		w = Math.round(w);
		h = Math.round(h);
		Shapes.rect(g, x + 1, y + 1, w - 2, h - 2, Theme.alpha(fill, a));
		Shapes.outline(g, x, y, w, h, 1, Theme.alpha(border, a));
	}

	/** Rail row, list row: a raised block when selected. */
	static void rowBack(GuiGraphics g, float x, float y, float w, float h, boolean selected, boolean hover, float a) {
		if (selected) block(g, x, y, w, h, RAISED, EDGE, LIGHT, SHADE, a);
		else if (hover) Shapes.rect(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Theme.alpha(ROW_HOVER, a));
	}

	/** The window: square, with a dark outer edge and a lit top edge. */
	static void panel(GuiGraphics g, float x, float y, float w, float h, int fill, float a) {
		Shapes.shadow(g, x, y + 4, w, h, 0, 20, Theme.alpha(0x59000000, a));
		Shapes.outline(g, x, y, w, h, 1, Theme.alpha(0xF0000000, a));
		Shapes.rect(g, x + 1, y + 1, w - 2, h - 2, Theme.alpha(fill, a));
		Shapes.rect(g, x + 1, y + 1, w - 2, 1, Theme.alpha(0x14FFFFFF, a));
	}

	/** The thin ring a character stands on, like in the launcher. */
	static void floorRing(GuiGraphics g, float cx, float cy, float radius, float a) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(1, 0.2f);
		Shapes.circle(g, 0, 0, radius, Theme.alpha(0x08EDEDED, a));
		Shapes.ring(g, 0, 0, radius, 1, Theme.alpha(LINE_STRONG, a));
		pose.popMatrix();
	}

	/** A thin square scroll bar; does nothing when everything fits. */
	static void scrollbar(GuiGraphics g, float x, float y, float h, float contentH, float scroll, float a) {
		float max = contentH - h;
		if (max <= 0) return;
		float barH = Math.max(24, h * h / contentH);
		Shapes.rect(g, Math.round(x + 1), Math.round(y + (h - barH) * Math.max(0, Math.min(1, scroll / max))), 3, Math.round(barH), Theme.alpha(LINE_STRONG, a));
	}

	/** An engraved line under a window's header: a dark line with a lit one below it. */
	static void headerLine(GuiGraphics g, float x, float y, float w, float a) {
		Shapes.rect(g, x, y, w, 1, Theme.alpha(0x99000000, a));
		Shapes.rect(g, x, y + 1, w, 1, Theme.alpha(0x0FFFFFFF, a));
	}

	static void footerLine(GuiGraphics g, float x, float y, float w, float a) {
		headerLine(g, x, y, w, a);
	}

	/** The engraved line between the rail and the content. */
	static void dividerV(GuiGraphics g, float x, float y, float h, float a) {
		Shapes.rect(g, x, y, 1, h, Theme.alpha(0x99000000, a));
		Shapes.rect(g, x + 1, y, 1, h, Theme.alpha(0x0FFFFFFF, a));
	}

	/** Small arrow pointing down, drawn pixel by pixel like the game's own arrows. */
	static void arrowDown(GuiGraphics g, float cx, float cy, int color) {
		float x = Math.round(cx), y = Math.round(cy) - 2;
		Shapes.rect(g, x - 3, y, 7, 1, color);
		Shapes.rect(g, x - 2, y + 1, 5, 1, color);
		Shapes.rect(g, x - 1, y + 2, 3, 1, color);
		Shapes.rect(g, x, y + 3, 1, 1, color);
	}

	// ------------------------------------------------------------------
	// Other
	// ------------------------------------------------------------------

	static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	/** Scissor in menu units, so a scrolling list ends exactly at its frame. */
	static void scissor(GuiGraphics g, float x, float y, float w, float h) {
		g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
	}
}
