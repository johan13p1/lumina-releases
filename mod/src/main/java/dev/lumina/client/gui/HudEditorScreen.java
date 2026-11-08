package dev.lumina.client.gui;

import dev.lumina.client.Lumina;
import dev.lumina.client.hud.HudRenderer;
import dev.lumina.client.module.HudModule;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.util.MacOS;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Drag HUD elements around, scroll to resize, right click for settings. */
public final class HudEditorScreen extends Screen {
	private static final float SNAP = 4;
	private static final int BUTTON_W = 86, BUTTON_GAP = 4;

	private final @Nullable Screen parent;
	private final Anim fade = new Anim(0, 12);
	private @Nullable HudModule dragging;
	private float grabX, grabY;
	private double scrollRest;
	private @Nullable Float guideX, guideY;
	private final List<float[]> guides = new ArrayList<>();

	public HudEditorScreen(@Nullable Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
		fade.target(1);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<HudModule> elements() {
		List<HudModule> list = new ArrayList<>();
		for (HudModule m : Lumina.modules().hud()) if (m.enabled()) list.add(m);
		return list;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float a = fade.update();
		Shapes.rect(g, 0, 0, width, height, Theme.alpha(0x66000000, a));
		// center guides
		Shapes.rect(g, width / 2f - Shapes.hairline() / 2f, 0, Shapes.hairline(), height, Theme.alpha(0x14FFFFFF, a));
		Shapes.rect(g, 0, height / 2f - Shapes.hairline() / 2f, width, Shapes.hairline(), Theme.alpha(0x14FFFFFF, a));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float a = fade.get();
		HudModule hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
		for (HudModule m : elements()) {
			HudRenderer.draw(g, m, width, height, true);
			float x = m.x(width), y = m.y(height), w = m.scaledWidth(), h = m.scaledHeight();
			boolean isHovered = m == hovered;
			Shapes.rect(g, x - 1, y - 1, w + 2, h + 2, Theme.alpha(isHovered ? 0x18FFFFFF : 0x08FFFFFF, a));
			Shapes.outline(g, x - 1, y - 1, w + 2, h + 2, 1, Theme.alpha(isHovered ? 0xFFFFFFFF : 0x50FFFFFF, a));
			if (isHovered) {
				String label = m.name() + "  " + Math.round(m.scale() * 100) + "%";
				float lw = McUI.width(label) + 8;
				float ly = y - 16 < 2 ? y + h + 3 : y - 16;
				float lx = Math.max(2, Math.min(width - lw - 2, x));
				McUI.frame(g, lx, ly, lw, 13, 0xF0101014, 0xFF000000, a);
				McUI.text(g, label, lx + 4, ly + 6.5f, Theme.alpha(McUI.WHITE, a));
			}
		}
		for (float[] gd : guides) {
			if (gd[0] == 0) Shapes.rect(g, gd[1] - Shapes.hairline() / 2f, 0, Shapes.hairline(), height, Theme.alpha(0xAA7FB7FF, a));
			else Shapes.rect(g, 0, gd[1] - Shapes.hairline() / 2f, width, Shapes.hairline(), Theme.alpha(0xAA7FB7FF, a));
		}

		// toolbar
		int bx = toolbarX(), by = toolbarY();
		if (dragging == null && hovered == null) {
			McUI.textCenter(g, "Drag to move · Scroll to resize · Right click for settings · " + MacOS.middleClickName() + " to reset", width / 2f, by - 10, Theme.alpha(McUI.WHITE, a));
		}
		McUI.button(g, bx, by, BUTTON_W, McUI.BUTTON_H, "Mods", UI.hover(mouseX, mouseY, bx, by, BUTTON_W, McUI.BUTTON_H), true, a);
		McUI.primary(g, bx + BUTTON_W + BUTTON_GAP, by, BUTTON_W, McUI.BUTTON_H, "Done", UI.hover(mouseX, mouseY, bx + BUTTON_W + BUTTON_GAP, by, BUTTON_W, McUI.BUTTON_H), true, a);
	}

	private int toolbarX() {
		return (width - BUTTON_W * 2 - BUTTON_GAP) / 2;
	}

	private int toolbarY() {
		return height - McUI.BUTTON_H - 8;
	}

	private @Nullable HudModule elementAt(double mx, double my) {
		List<HudModule> list = elements();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule m = list.get(i);
			if (UI.hover(mx, my, m.x(width) - 1, m.y(height) - 1, m.scaledWidth() + 2, m.scaledHeight() + 2)) return m;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int bx = toolbarX(), by = toolbarY();
		if (UI.hover(mx, my, bx, by, BUTTON_W, McUI.BUTTON_H)) {
			McUI.click();
			minecraft.setScreen(parent instanceof LuminaScreen ? parent : new LuminaScreen(null));
			return true;
		}
		if (UI.hover(mx, my, bx + BUTTON_W + BUTTON_GAP, by, BUTTON_W, McUI.BUTTON_H)) {
			McUI.click();
			onClose();
			return true;
		}
		HudModule m = elementAt(mx, my);
		if (m == null) return true;
		int button = MacOS.button(event);
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			minecraft.setScreen(new LuminaScreen(this).openModule(m));
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
			m.resetPosition();
			return true;
		}
		dragging = m;
		grabX = (float) mx - m.x(width);
		grabY = (float) my - m.y(height);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return true;
		float w = dragging.scaledWidth(), h = dragging.scaledHeight();
		float x = (float) event.x() - grabX;
		float y = (float) event.y() - grabY;
		guides.clear();
		boolean free = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		if (!free) {
			// candidate lines: screen edges/center and other elements' edges/centers
			List<Float> xs = new ArrayList<>(List.of(2f, width / 2f, width - 2f));
			List<Float> ys = new ArrayList<>(List.of(2f, height / 2f, height - 2f));
			for (HudModule o : elements()) {
				if (o == dragging) continue;
				float ox = o.x(width), oy = o.y(height);
				xs.add(ox);
				xs.add(ox + o.scaledWidth());
				xs.add(ox + o.scaledWidth() / 2f);
				ys.add(oy);
				ys.add(oy + o.scaledHeight());
				ys.add(oy + o.scaledHeight() / 2f);
			}
			float[] snappedX = snap(x, w, xs);
			float[] snappedY = snap(y, h, ys);
			if (snappedX != null) {
				x = snappedX[0];
				guides.add(new float[] {0, snappedX[1]});
			}
			if (snappedY != null) {
				y = snappedY[0];
				guides.add(new float[] {1, snappedY[1]});
			}
		}
		dragging.setPosition(x, y, width, height);
		return true;
	}

	/** Returns {newPos, guideLine} when one of the element's edges or its center is close to a line. */
	private static float @Nullable [] snap(float pos, float size, List<Float> lines) {
		float best = SNAP + 1;
		float[] result = null;
		float[] offsets = {0, size / 2f, size};
		for (float line : lines) {
			for (float off : offsets) {
				float d = Math.abs(pos + off - line);
				if (d < best) {
					best = d;
					result = new float[] {line - off, line};
				}
			}
		}
		return best <= SNAP ? result : null;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		guides.clear();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		HudModule m = elementAt(mouseX, mouseY);
		if (m == null) return true;
		float x = m.x(width), y = m.y(height);
		float cxr = (float) (mouseX - x) / m.scaledWidth(), cyr = (float) (mouseY - y) / m.scaledHeight();
		var scale = m.settings().stream().filter(s -> s.id().equals("scale")).findFirst().orElse(null);
		if (scale instanceof dev.lumina.client.setting.NumberSetting n) {
			// a trackpad sends many small deltas, a mouse wheel whole notches: resize one step per notch
			scrollRest += scrollY;
			int steps = (int) scrollRest;
			if (steps == 0) return true;
			scrollRest -= steps;
			n.set(n.get() + steps * 0.05);
			// keep the point under the cursor in place
			m.setPosition((float) mouseX - cxr * m.scaledWidth(), (float) mouseY - cyr * m.scaledHeight(), width, height);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE || Lumina.MENU_KEY.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Lumina.config().save();
		minecraft.setScreen(null);
	}
}
