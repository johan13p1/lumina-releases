package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.Waypoint;
import dev.lumina.client.feature.world.WaypointStore;
import dev.lumina.client.feature.world.WaypointsModule;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

/** Waypoint manager, opened with the waypoint key: list, show/hide, rename, move here, add new. */
public final class WaypointScreen extends Screen {
	private static final int W = 400, H = 270;
	private static final int PAD = 8;
	private static final int HEADER_H = 30, FOOTER_H = 30;
	private static final int ROW_H = 22, FIELD_H = 16;
	private static final int SCROLLBAR_SPACE = 8;
	// inside a row, from its left edge
	private static final int SWATCH_X = 2, SWATCH = 12;
	private static final int NAME_X = 18, NAME_W = 120;
	private static final int COORDS_X = 144, COORDS_W = 90;
	private static final int INFO_X = 238, INFO_W = 58;
	// inside a row, from its right edge
	private static final int REMOVE_W = 12;
	private static final int TOGGLE_RIGHT = REMOVE_W + 4 + McUI.SWITCH_W;
	private static final int HERE_W = 34;
	private static final int HERE_RIGHT = TOGGLE_RIGHT + 6 + HERE_W;
	private static final int ADD_W = 70, DONE_W = 50;

	private final WaypointsModule module;
	private final KeyMapping key;
	private final Anim open = new Anim(0, 13);
	private final Anim scroll = new Anim(0, 20);
	private final Map<Waypoint, TextInput> names = new IdentityHashMap<>();
	private final Map<Waypoint, Anim> toggles = new IdentityHashMap<>();
	private final TextInput newName = new TextInput("", 32, "Name of the new waypoint", s -> {
	});
	private float scrollTarget;
	private boolean closing;
	private float scale = 1;
	private int px, py;

	public WaypointScreen(WaypointsModule module, KeyMapping key) {
		super(Component.literal("Waypoints"));
		this.module = module;
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		layout();
	}

	private void layout() {
		scale = McUI.fitScale();
		px = Math.round((width / scale - W) / 2f);
		py = Math.round((height / scale - H) / 2f);
	}

	private List<Waypoint> list() {
		return module.store.current();
	}

	private int listX() {
		return px + PAD;
	}

	private int listY() {
		return py + HEADER_H + 4;
	}

	private int listW() {
		return W - PAD * 2 - SCROLLBAR_SPACE;
	}

	private int listH() {
		return H - HEADER_H - FOOTER_H - 8;
	}

	private int footerY() {
		return py + H - McUI.BUTTON_H - 5;
	}

	private int doneX() {
		return px + W - PAD - DONE_W;
	}

	private int addX() {
		return doneX() - 4 - ADD_W;
	}

	private int fieldW() {
		return addX() - 4 - (px + PAD);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float a = Anim.easeOutCubic(open.get());
		Shapes.rect(g, 0, 0, width, height, Theme.alpha(McUI.BACKDROP, a));
		if (minecraft.options.getMenuBackgroundBlurriness() >= 1 && Lumina.menuBlurEnabled()) g.blurBeforeThisStratum();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float t = open.update(closing ? 0 : 1);
		if (closing && t < 0.02f) {
			minecraft.gui.setScreen(null);
			return;
		}
		float a = Anim.easeOutCubic(t);
		layout();
		double mx = mouseX / scale, my = mouseY / scale;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(scale, scale);
		pose.translate(0, (1 - a) * 6);

		McUI.panel(g, px, py, W, H, McUI.BODY, a);
		ModuleIcons.module(g, "waypoints", px + PAD, py + 6, a);
		McUI.text(g, "Waypoints", px + PAD + 21, py + 14, Theme.alpha(McUI.WHITE, a));
		McUI.textRight(g, McUI.trim(worldLabel(), W - PAD * 2 - 90), px + W - PAD, py + 14, Theme.alpha(McUI.GRAY, a));
		McUI.headerLine(g, px + 1, py + HEADER_H - 2, W - 2, a);

		renderList(g, mx, my, a);
		renderFooter(g, mx, my, a);
		McUI.flushTooltip(g, a);
		pose.popMatrix();
	}

	private String worldLabel() {
		String key = WaypointStore.worldKey();
		String world = key == null ? "" : key.substring(key.indexOf(':') + 1);
		String dim = WaypointStore.dimension().replace("minecraft:", "").replace('_', ' ');
		return world + " · " + dim;
	}

	private void renderList(GuiGraphicsExtractor g, double mx, double my, float a) {
		List<Waypoint> wps = list();
		int x = listX(), y = listY(), w = listW(), h = listH();
		float contentH = wps.size() * ROW_H;
		scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - h), scrollTarget));
		float sc = Math.round(scroll.update(scrollTarget));
		if (wps.isEmpty()) {
			McUI.textCenter(g, "No waypoints in this world yet", px + W / 2f, y + h / 2f - 6, Theme.alpha(McUI.WHITE, a));
			McUI.textCenter(g, "Add one at your position below", px + W / 2f, y + h / 2f + 6, Theme.alpha(McUI.GRAY, a));
			return;
		}
		Player player = minecraft.player;
		String dim = WaypointStore.dimension();
		boolean inside = UI.hover(mx, my, x, y, w, h);
		McUI.scissor(g, x, y, w, h);
		for (int i = 0; i < wps.size(); i++) {
			Waypoint wp = wps.get(i);
			float ry = y + i * ROW_H - sc;
			if (ry > y + h || ry + ROW_H < y) continue;
			float rcy = ry + ROW_H / 2f;
			if (inside && UI.hover(mx, my, x, ry, w, ROW_H)) Shapes.rect(g, x, ry, w, ROW_H, Theme.alpha(McUI.ROW_HOVER, a));

			float vis = wp.visible ? 1 : 0.35f;
			float boxY = Math.round(rcy - FIELD_H / 2f);
			Shapes.rect(g, x + SWATCH_X, boxY + 2, SWATCH, SWATCH, Theme.alpha(0xFF000000, a));
			Shapes.rect(g, x + SWATCH_X + 1, boxY + 3, SWATCH - 2, SWATCH - 2, Theme.alpha(0xFF000000 | wp.color, a * vis));

			TextInput in = names.computeIfAbsent(wp, k -> new TextInput(wp.name, 32, "Name", name -> {
				wp.name = name;
				module.store.save();
			}));
			in.renderVanilla(g, x + NAME_X, boxY, NAME_W, FIELD_H, a);

			String coords = wp.x + ", " + wp.y + ", " + wp.z;
			String info = !dim.equals(wp.dimension) ? wp.dimension.replace("minecraft:", "").replace('_', ' ')
				: player == null ? "" : Math.round(Math.sqrt(player.distanceToSqr(wp.x + 0.5, wp.y, wp.z + 0.5))) + " m";
			McUI.text(g, McUI.trim(coords, COORDS_W), x + COORDS_X, rcy, Theme.alpha(wp.visible ? McUI.GRAY : McUI.DARK_GRAY, a));
			McUI.text(g, McUI.trim(info, INFO_W), x + INFO_X, rcy, Theme.alpha(McUI.DARK_GRAY, a));

			float hereX = x + w - HERE_RIGHT;
			McUI.button(g, hereX, boxY, HERE_W, FIELD_H, "Here", inside && UI.hover(mx, my, hereX, boxY, HERE_W, FIELD_H), true, a);
			Anim on = toggles.computeIfAbsent(wp, k -> new Anim(wp.visible ? 1 : 0, 18));
			McUI.toggle(g, x + w - TOGGLE_RIGHT, rcy - McUI.SWITCH_H / 2f, on.update(wp.visible), a);
			boolean removeHover = inside && UI.hover(mx, my, x + w - REMOVE_W, rcy - 7, REMOVE_W, 14);
			UI.icon(g, dev.lumina.client.render.Icons.X, x + w - REMOVE_W / 2f, rcy, 9, Theme.alpha(removeHover ? McUI.RED : McUI.GRAY, a));

			if (inside) {
				if (UI.hover(mx, my, hereX, boxY, HERE_W, FIELD_H)) McUI.tooltip(g, "Move to my position", mx, my);
				else if (UI.hover(mx, my, x + w - TOGGLE_RIGHT - 2, rcy - 7, McUI.SWITCH_W + 4, 14)) McUI.tooltip(g, wp.visible ? "Hide" : "Show", mx, my);
				else if (removeHover) McUI.tooltip(g, "Delete", mx, my);
				else if (UI.hover(mx, my, x + SWATCH_X, rcy - 7, SWATCH, 14)) McUI.tooltip(g, "Change color", mx, my);
			}
		}
		g.disableScissor();
		McUI.scrollbar(g, x + w + 2, y, h, contentH, sc, a);
	}

	private void renderFooter(GuiGraphicsExtractor g, double mx, double my, float a) {
		McUI.footerLine(g, px + 1, py + H - FOOTER_H, W - 2, a);
		int y = footerY();
		newName.renderVanilla(g, px + PAD, y, fieldW(), McUI.BUTTON_H, a);
		McUI.button(g, addX(), y, ADD_W, McUI.BUTTON_H, "Add Here", UI.hover(mx, my, addX(), y, ADD_W, McUI.BUTTON_H), true, a);
		McUI.primary(g, doneX(), y, DONE_W, McUI.BUTTON_H, "Done", UI.hover(mx, my, doneX(), y, DONE_W, McUI.BUTTON_H), true, a);
	}

	private void add() {
		if (minecraft.player == null) return;
		Waypoint wp = module.addAtPlayer();
		String name = newName.text().trim();
		if (!name.isEmpty()) {
			wp.name = name;
			module.store.save();
		}
		newName.setText("");
		newName.setFocused(false);
		scrollTarget = Float.MAX_VALUE;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) return true;
		layout();
		double mx = event.x() / scale, my = event.y() / scale;
		newName.setFocused(false);
		for (TextInput in : names.values()) in.setFocused(false);
		if (!UI.hover(mx, my, px, py, W, H)) {
			onClose();
			return true;
		}
		int fy = footerY();
		if (UI.hover(mx, my, doneX(), fy, DONE_W, McUI.BUTTON_H)) {
			McUI.click();
			onClose();
			return true;
		}
		if (UI.hover(mx, my, addX(), fy, ADD_W, McUI.BUTTON_H)) {
			McUI.click();
			add();
			return true;
		}
		if (UI.hover(mx, my, px + PAD, fy, fieldW(), McUI.BUTTON_H)) {
			newName.setFocused(true);
			return true;
		}
		List<Waypoint> wps = list();
		int x = listX(), y = listY(), w = listW();
		if (!UI.hover(mx, my, x, y, w, listH())) return true;
		float sc = Math.round(scroll.get());
		for (Waypoint wp : new ArrayList<>(wps)) {
			float ry = y + wps.indexOf(wp) * ROW_H - sc, rcy = ry + ROW_H / 2f;
			if (!UI.hover(mx, my, x, ry, w, ROW_H)) continue;
			if (UI.hover(mx, my, x + w - HERE_RIGHT, rcy - FIELD_H / 2f, HERE_W, FIELD_H)) {
				if (minecraft.player != null) {
					wp.x = minecraft.player.getBlockX();
					wp.y = minecraft.player.getBlockY();
					wp.z = minecraft.player.getBlockZ();
					wp.dimension = WaypointStore.dimension();
				}
			} else if (UI.hover(mx, my, x + w - TOGGLE_RIGHT - 2, rcy - 7, McUI.SWITCH_W + 4, 14)) {
				wp.visible = !wp.visible;
			} else if (UI.hover(mx, my, x + w - REMOVE_W, rcy - 7, REMOVE_W, 14)) {
				module.remove(wp);
				names.remove(wp);
				toggles.remove(wp);
				McUI.click();
				return true;
			} else if (UI.hover(mx, my, x + SWATCH_X, rcy - 7, SWATCH, 14)) {
				wp.color = WaypointsModule.nextColor(wp.color);
			} else if (UI.hover(mx, my, x + NAME_X, rcy - FIELD_H / 2f, NAME_W, FIELD_H)) {
				names.get(wp).setFocused(true);
				return true;
			} else {
				return true;
			}
			McUI.click();
			module.store.save();
			return true;
		}
		return true;
	}

	private TextInput focusedInput() {
		if (newName.focused()) return newName;
		for (TextInput in : names.values()) if (in.focused()) return in;
		return null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		TextInput in = focusedInput();
		if (in != null) {
			boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
			if (in == newName && enter) {
				add();
				return true;
			}
			in.keyPressed(event);
			return true;
		}
		if (event.key() == InputConstants.KEY_ESCAPE || key.matches(event)) {
			onClose();
			return true;
		}
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			add();
			return true;
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		TextInput in = focusedInput();
		if (in != null) return in.charTyped(event);
		// start typing anywhere to name a new waypoint
		if (event.isAllowedChatCharacter() && event.codepoint() != ' ') {
			newName.setFocused(true);
			return newName.charTyped(event);
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget -= (float) scrollY * ROW_H;
		return true;
	}

	@Override
	public void onClose() {
		if (closing) return;
		closing = true;
		module.store.save();
	}
}
