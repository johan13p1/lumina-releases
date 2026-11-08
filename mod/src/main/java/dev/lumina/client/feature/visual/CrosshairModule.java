package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2fStack;

public final class CrosshairModule extends Module {
	public final ModeSetting shape;
	private final NumberSetting length;
	private final NumberSetting gap;
	private final NumberSetting thickness;
	private final BoolSetting dot;
	private final NumberSetting dotSize;
	private final ColorSetting color;
	private final BoolSetting invert;
	private final BoolSetting outline;
	private final ColorSetting outlineColor;
	public final BoolSetting thirdPerson;
	public final BoolSetting hideInScreens;

	public CrosshairModule() {
		super("crosshair", "Crosshair", "Design your own crosshair", Category.VISUAL, Icons.CROSSHAIR, false);
		shape = add(new ModeSetting("shape", "Shape", "Cross", "Cross", "Circle", "Dot", "T-Shape", "Square"));
		length = add(new NumberSetting("length", "Length", 5, 1, 15, 0.5).visibleWhen(() -> !shape.is("Dot")));
		gap = add(new NumberSetting("gap", "Gap", 2, 0, 10, 0.5).visibleWhen(() -> !shape.is("Dot")));
		thickness = add(new NumberSetting("thickness", "Thickness", 1, 0.5, 4, 0.5).visibleWhen(() -> !shape.is("Dot")));
		dot = add(new BoolSetting("dot", "Center Dot", false).visibleWhen(() -> !shape.is("Dot")));
		dotSize = add(new NumberSetting("dot_size", "Dot Size", 1, 0.5, 4, 0.5).visibleWhen(() -> dot.on() || shape.is("Dot")));
		add(new HeaderSetting("Color"));
		color = add(new ColorSetting("color", "Color", 0xFFFFFFFF));
		invert = add(new BoolSetting("invert", "Invert Like Vanilla", false));
		outline = add(new BoolSetting("outline", "Outline", true).visibleWhen(() -> !invert.on()));
		outlineColor = add(new ColorSetting("outline_color", "Outline Color", 0xA0000000).visibleWhen(() -> outline.on() && !invert.on()));
		add(new HeaderSetting("Visibility"));
		thirdPerson = add(new BoolSetting("third_person", "Show In Third Person", false));
		hideInScreens = add(new BoolSetting("hide_in_screens", "Hide While Menus Are Open", true));
	}

	/** Draws the crosshair centered in the screen, aligned to physical pixels. */
	public void render(GuiGraphics g) {
		renderAt(g, mc().getWindow().getWidth() / 2, mc().getWindow().getHeight() / 2);
	}

	@Override
	public float previewHeight() {
		return 70;
	}

	/** Live preview in the settings: the crosshair on a dark and on a bright background. */
	@Override
	public void renderPreview(GuiGraphics g, float x, float y, float w, float h, float alpha) {
		float half = w / 2f;
		Shapes.rect(g, x, y, half, h, Theme.alpha(0xFF1A1B20, alpha));
		Shapes.rect(g, x + half, y, w - half, h, Theme.alpha(0xFF9FA7B3, alpha));
		Shapes.outline(g, x, y, w, h, 1, Theme.alpha(0xFF000000, alpha));
		dev.lumina.client.gui.McUI.textTop(g, "Preview", x + 6, y + 6, Theme.alpha(dev.lumina.client.gui.McUI.GRAY, alpha));
		float gs = Shapes.guiScale();
		var dark = g.pose().transformPosition(x + half / 2f, y + h / 2f, new org.joml.Vector2f());
		var light = g.pose().transformPosition(x + half * 1.5f, y + h / 2f, new org.joml.Vector2f());
		// the crosshair snaps to physical pixels, so draw it untransformed at the transformed centers
		Matrix3x2fStack stack = g.pose();
		stack.pushMatrix();
		stack.identity();
		renderAt(g, Math.round(dark.x * gs), Math.round(dark.y * gs));
		renderAt(g, Math.round(light.x * gs), Math.round(light.y * gs));
		stack.popMatrix();
	}

	/** Draws the crosshair centered on a physical pixel position. */
	public void renderAt(GuiGraphics g, int cx, int cy) {
		float gs = Shapes.guiScale();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(1f / gs, 1f / gs);
		int len = Math.max(1, Math.round(length.getFloat() * gs));
		int gp = Math.round(gap.getFloat() * gs);
		int t = Math.max(1, Math.round(thickness.getFloat() * gs));
		int o = Math.max(1, Math.round(gs * 0.5f));
		int col = color.color();
		boolean drawOutline = outline.on() && !invert.on();
		int oc = outlineColor.color();
		// thickness centered on the middle pixel; odd values stay symmetric
		int t0 = -(t / 2), t1 = t0 + t;
		switch (shape.get()) {
			case "Cross", "T-Shape" -> {
				int[][] bars = {
					{cx + gp, cy + t0, cx + gp + len, cy + t1},
					{cx - gp - len, cy + t0, cx - gp, cy + t1},
					{cx + t0, cy + gp, cx + t1, cy + gp + len},
					{cx + t0, cy - gp - len, cx + t1, cy - gp},
				};
				int count = shape.is("T-Shape") ? 3 : 4;
				if (drawOutline) for (int i = 0; i < count; i++) fill(g, bars[i][0] - o, bars[i][1] - o, bars[i][2] + o, bars[i][3] + o, oc);
				for (int i = 0; i < count; i++) fill(g, bars[i][0], bars[i][1], bars[i][2], bars[i][3], col);
			}
			case "Square" -> {
				int r = gp + len;
				if (drawOutline) {
					outlineBox(g, cx - r - o, cy - r - o, cx + r + o, cy + r + o, t + 2 * o, oc);
				}
				outlineBox(g, cx - r, cy - r, cx + r, cy + r, t, col);
			}
			case "Circle" -> {
				float radius = (gp + len) / gs;
				pose.popMatrix();
				float ccx = cx / gs, ccy = cy / gs;
				if (drawOutline) Shapes.ring(g, ccx, ccy, radius + o / gs, (t + 2f * o) / gs, oc);
				Shapes.ring(g, ccx, ccy, radius, t / gs, col);
				pose.pushMatrix();
				pose.scale(1f / gs, 1f / gs);
			}
			default -> {
			}
		}
		if (dot.on() || shape.is("Dot")) {
			int d = Math.max(1, Math.round(dotSize.getFloat() * gs));
			int d0 = -(d / 2), d1 = d0 + d;
			if (drawOutline) fill(g, cx + d0 - o, cy + d0 - o, cx + d1 + o, cy + d1 + o, oc);
			fill(g, cx + d0, cy + d0, cx + d1, cy + d1, col);
		}
		pose.popMatrix();
	}

	private void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		if (invert.on()) g.fill(RenderPipelines.GUI_INVERT, x0, y0, x1, y1, 0xFFFFFFFF);
		else g.fill(x0, y0, x1, y1, color);
	}

	private void outlineBox(GuiGraphics g, int x0, int y0, int x1, int y1, int t, int color) {
		fill(g, x0, y0, x1, y0 + t, color);
		fill(g, x0, y1 - t, x1, y1, color);
		fill(g, x0, y0 + t, x0 + t, y1 - t, color);
		fill(g, x1 - t, y0 + t, x1, y1 - t, color);
	}
}
