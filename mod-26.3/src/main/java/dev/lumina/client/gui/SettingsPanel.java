package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.KeySetting;
import dev.lumina.client.setting.MacroSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import dev.lumina.client.setting.Setting;
import dev.lumina.client.setting.TextSetting;
import dev.lumina.client.setting.WaypointsSetting;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.Waypoint;
import dev.lumina.client.feature.world.WaypointStore;
import dev.lumina.client.feature.world.WaypointsModule;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Scrollable list of a module's settings; {@link HeaderSetting}s start a new section. */
public final class SettingsPanel {
	private static final int ROW_H = 24;
	private static final int HEADER_H = 18;
	private static final int MACRO_ROW_H = 20;
	private static final int CONTROL_H = McUI.SMALL_BUTTON_H;
	private static final int SLIDER_W = 130;
	private static final int ADD_W = 58;
	private static final int ADD_HERE_W = 62;
	private static final int REMOVE_W = 12;
	private static final int ROW_PAD = 4;
	private static final int KEY_BOX_W = 74;
	private static final float DROPDOWN_ITEM_H = 16;

	private final Module module;
	private final List<Row> rows = new ArrayList<>();
	private final Map<BoolSetting, Anim> toggles = new IdentityHashMap<>();
	private final Map<Object, Anim> waypointToggles = new IdentityHashMap<>();
	private final Map<Object, TextInput> inputs = new HashMap<>();
	private final Anim scroll = new Anim(0, 20);
	private float scrollTarget;
	private float contentHeight;
	private float x, y, w, h;

	private ColorPicker picker;
	private ModeSetting dropdown;
	private float dropdownX, dropdownY, dropdownW;
	private KeySetting listening;
	private NumberSetting draggingSlider;
	private float sliderX;

	private record Row(Setting<?> setting, float y, float h) {
	}

	public SettingsPanel(Module module) {
		this.module = module;
	}

	public Module module() {
		return module;
	}

	public boolean capturesKeyboard() {
		if (listening != null) return true;
		if (picker != null && picker.typing()) return true;
		for (TextInput in : inputs.values()) if (in.focused()) return true;
		return false;
	}

	private void layout() {
		rows.clear();
		float cy = 0;
		List<Setting<?>> visible = new ArrayList<>();
		for (Setting<?> s : module.settings()) if (s.isVisible()) visible.add(s);
		for (int i = 0; i < visible.size(); i++) {
			Setting<?> s = visible.get(i);
			if (s instanceof HeaderSetting) {
				// skip headers that would have no rows below them
				if (i + 1 >= visible.size() || visible.get(i + 1) instanceof HeaderSetting) continue;
				cy += rows.isEmpty() ? 0 : 6;
				rows.add(new Row(s, cy, HEADER_H));
				cy += HEADER_H;
				continue;
			}
			float rh = s instanceof MacroSetting m ? ROW_H + m.macros().size() * MACRO_ROW_H + 4
				: s instanceof WaypointsSetting ? ROW_H + waypoints().size() * MACRO_ROW_H + 4 : ROW_H;
			rows.add(new Row(s, cy, rh));
			cy += rh;
		}
		contentHeight = cy + 4;
	}

	public void render(GuiGraphicsExtractor g, float x, float y, float w, float h, double mx, double my, float alpha) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		layout();
		float maxScroll = Math.max(0, contentHeight - h);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		float sc = Math.round(scroll.update(scrollTarget));
		boolean popupOpen = picker != null || dropdown != null;

		McUI.scissor(g, x, y, w, h);
		for (Row r : rows) {
			float ry = y + r.y - sc;
			if (ry > y + h || ry + r.h < y) continue;
			boolean hovered = !popupOpen && UI.hover(mx, my, x, Math.max(y, ry), w, Math.min(r.h, y + h - ry)) && my >= y && my < y + h;
			drawRow(g, r, ry, mx, my, hovered, alpha);
		}
		g.disableScissor();
		McUI.scrollbar(g, x + w + 2, y, h, contentHeight, sc, alpha);

		if (dropdown != null) renderDropdown(g, mx, my, alpha);
		if (picker != null) picker.render(g, mx, my);
	}

	private void drawRow(GuiGraphicsExtractor g, Row r, float ry, double mx, double my, boolean hovered, float alpha) {
		Setting<?> s = r.setting;
		if (s instanceof HeaderSetting) {
			float cy = ry + HEADER_H / 2f + 2;
			McUI.text(g, s.name(), x + ROW_PAD, cy, Theme.alpha(McUI.GRAY, alpha));
			float lineX = x + ROW_PAD + McUI.width(s.name()) + 6;
			McUI.headerLine(g, lineX, Math.round(cy), x + w - lineX, alpha);
			return;
		}
		if (hovered) Shapes.rect(g, x, ry, w, Math.min(r.h, ROW_H), Theme.alpha(McUI.ROW_HOVER, alpha));

		float cy = ry + ROW_H / 2f;
		float right = x + w - ROW_PAD;
		float controlY = Math.round(cy - CONTROL_H / 2f);
		McUI.text(g, McUI.trim(s.name(), w * 0.5f), x + ROW_PAD, cy, Theme.alpha(McUI.WHITE, alpha));

		if (s instanceof BoolSetting b) {
			Anim t = toggles.computeIfAbsent(b, k -> new Anim(b.on() ? 1 : 0, 18));
			McUI.toggle(g, right - McUI.SWITCH_W, cy - McUI.SWITCH_H / 2f, t.update(b.on()), alpha);
		} else if (s instanceof NumberSetting n) {
			float sx = right - SLIDER_W;
			boolean sliderHover = draggingSlider == n || (hovered && UI.hover(mx, my, sx, controlY, SLIDER_W, CONTROL_H));
			McUI.slider(g, sx, controlY, SLIDER_W, CONTROL_H, (float) n.progress(), n.display(), sliderHover, alpha);
		} else if (s instanceof ColorSetting c) {
			float sw = 28, sh = CONTROL_H - 2;
			float sx = right - sw, sy = Math.round(cy - sh / 2f);
			// left half without transparency, right half with it
			ColorPicker.checker(g, sx + sw / 2f, sy, sw / 2f, sh, alpha);
			Shapes.rect(g, sx, sy, sw / 2f, sh, Theme.alpha(c.color() | 0xFF000000, alpha));
			Shapes.rect(g, sx + sw / 2f, sy, sw / 2f, sh, Theme.alpha(c.color(), alpha));
			Shapes.outline(g, sx, sy, sw, sh, 1, Theme.alpha(hovered ? 0x99EDEDED : McUI.LINE_STRONG, alpha));
			String hex = c.chroma() ? "Chroma" : String.format("#%06X", c.get() & 0xFFFFFF);
			McUI.textRight(g, hex, sx - 6, cy, Theme.alpha(McUI.GRAY, alpha));
		} else if (s instanceof ModeSetting m) {
			float bw = modeWidth(m);
			float bx = right - bw;
			boolean open = dropdown == m;
			McUI.button(g, bx, controlY, bw, CONTROL_H, "", open || (hovered && UI.hover(mx, my, bx, controlY, bw, CONTROL_H)), true, alpha);
			McUI.text(g, m.get(), bx + 6, cy, Theme.alpha(McUI.WHITE, alpha));
			McUI.arrowDown(g, bx + bw - 9, cy, Theme.alpha(McUI.GRAY, alpha));
		} else if (s instanceof KeySetting k) {
			drawKeyBox(g, k, right, controlY, mx, my, alpha);
		} else if (s instanceof TextSetting t) {
			TextInput in = inputs.computeIfAbsent(t, key -> new TextInput(t.get(), t.maxLength(), "Type...", t::set));
			float iw = Math.min(170, w * 0.5f);
			in.renderVanilla(g, right - iw, controlY, iw, CONTROL_H, alpha);
		} else if (s instanceof WaypointsSetting) {
			drawWaypoints(g, ry, mx, my, alpha);
		} else if (s instanceof MacroSetting macros) {
			McUI.button(g, right - ADD_W, controlY, ADD_W, CONTROL_H, "Add", UI.hover(mx, my, right - ADD_W, controlY, ADD_W, CONTROL_H), true, alpha);
			float rowY = ry + ROW_H;
			for (MacroSetting.Macro macro : macros.macros()) {
				float rcy = rowY + MACRO_ROW_H / 2f;
				float boxY = Math.round(rcy - CONTROL_H / 2f);
				float keyRight = x + ROW_PAD + KEY_BOX_W;
				drawKeyBox(g, macro.key, keyRight, boxY, mx, my, alpha);
				TextInput in = inputs.computeIfAbsent(macro, key -> new TextInput(macro.text.get(), 256, "/command or message", macro.text::set));
				float tx = keyRight + 6;
				in.renderVanilla(g, tx, boxY, right - REMOVE_W - 4 - tx, CONTROL_H, alpha);
				drawRemove(g, right, rcy, mx, my, alpha);
				rowY += MACRO_ROW_H;
			}
		}
	}

	/** The red cross at the end of a macro or waypoint row. */
	private void drawRemove(GuiGraphicsExtractor g, float right, float cy, double mx, double my, float alpha) {
		boolean hov = UI.hover(mx, my, right - REMOVE_W, cy - 7, REMOVE_W, 14);
		UI.icon(g, dev.lumina.client.render.Icons.X, right - REMOVE_W / 2f, cy, 9, Theme.alpha(hov ? McUI.RED : McUI.GRAY, alpha));
	}

	private static float modeWidth(ModeSetting m) {
		return Math.max(70, McUI.width(m.get()) + 24);
	}

	private static List<Waypoint> waypoints() {
		WaypointsModule m = Lumina.modules().get(WaypointsModule.class);
		return m == null || WaypointStore.worldKey() == null ? List.of() : m.store.current();
	}

	private void drawWaypoints(GuiGraphicsExtractor g, float ry, double mx, double my, float alpha) {
		float cy = ry + ROW_H / 2f;
		float right = x + w - ROW_PAD;
		float controlY = Math.round(cy - CONTROL_H / 2f);
		if (WaypointStore.worldKey() != null) {
			McUI.button(g, right - ADD_HERE_W, controlY, ADD_HERE_W, CONTROL_H, "Add Here", UI.hover(mx, my, right - ADD_HERE_W, controlY, ADD_HERE_W, CONTROL_H), true, alpha);
		} else {
			McUI.textRight(g, "Join a world to manage waypoints", right, cy, Theme.alpha(McUI.GRAY, alpha));
		}
		float rowY = ry + ROW_H;
		String dim = WaypointStore.dimension();
		for (Waypoint wp : waypoints()) {
			float rcy = rowY + MACRO_ROW_H / 2f;
			float boxY = Math.round(rcy - CONTROL_H / 2f);
			float colorX = x + ROW_PAD;
			Shapes.rect(g, colorX, boxY + 2, 12, 12, Theme.alpha(0xFF000000, alpha));
			Shapes.rect(g, colorX + 1, boxY + 3, 10, 10, Theme.alpha(0xFF000000 | wp.color, alpha * (wp.visible ? 1 : 0.35f)));
			TextInput in = inputs.computeIfAbsent(wp, key -> new TextInput(wp.name, 32, "Name", name -> {
				wp.name = name;
				saveWaypoints();
			}));
			float tx = x + 22, tw = Math.min(130, w * 0.32f);
			in.renderVanilla(g, tx, boxY, tw, CONTROL_H, alpha);
			float toggleX = right - REMOVE_W - 4 - McUI.SWITCH_W;
			String coords = wp.x + ", " + wp.y + ", " + wp.z + (dim.equals(wp.dimension) ? "" : " · " + wp.dimension.replace("minecraft:", ""));
			McUI.text(g, McUI.trim(coords, toggleX - tx - tw - 12), tx + tw + 6, rcy, Theme.alpha(McUI.GRAY, alpha));
			Anim t = waypointToggles.computeIfAbsent(wp, k -> new Anim(wp.visible ? 1 : 0, 18));
			McUI.toggle(g, toggleX, rcy - McUI.SWITCH_H / 2f, t.update(wp.visible), alpha);
			drawRemove(g, right, rcy, mx, my, alpha);
			rowY += MACRO_ROW_H;
		}
	}

	private static void saveWaypoints() {
		WaypointsModule m = Lumina.modules().get(WaypointsModule.class);
		if (m != null) m.store.save();
	}

	private boolean clickWaypoints(float ry, double mx, double my) {
		float cy = ry + ROW_H / 2f;
		float right = x + w - ROW_PAD;
		WaypointsModule m = Lumina.modules().get(WaypointsModule.class);
		if (m == null || WaypointStore.worldKey() == null) return true;
		if (UI.hover(mx, my, right - ADD_HERE_W, cy - CONTROL_H / 2f, ADD_HERE_W, CONTROL_H) && Minecraft.getInstance().player != null) {
			McUI.click();
			m.addAtPlayer();
			return true;
		}
		float rowY = ry + ROW_H;
		for (Waypoint wp : new ArrayList<>(waypoints())) {
			float rcy = rowY + MACRO_ROW_H / 2f;
			float toggleX = right - REMOVE_W - 4 - McUI.SWITCH_W;
			if (UI.hover(mx, my, x + ROW_PAD, rcy - 7, 14, 14)) {
				int[] palette = {0xFFE5484D, 0xFFF76B15, 0xFFFFC53D, 0xFF46A758, 0xFF12A594, 0xFF3E63DD, 0xFF8E4EC6, 0xFFD6409F, 0xFFF0F0F2};
				int idx = 0;
				for (int i = 0; i < palette.length; i++) if ((palette[i] & 0xFFFFFF) == (wp.color & 0xFFFFFF)) idx = i;
				wp.color = palette[(idx + 1) % palette.length];
				saveWaypoints();
			} else if (UI.hover(mx, my, x + 22, rcy - CONTROL_H / 2f, Math.min(130, w * 0.32f), CONTROL_H)) {
				TextInput in = inputs.get(wp);
				if (in != null) in.setFocused(true);
			} else if (UI.hover(mx, my, toggleX - 2, rcy - 7, McUI.SWITCH_W + 4, 14)) {
				McUI.click();
				wp.visible = !wp.visible;
				saveWaypoints();
			} else if (UI.hover(mx, my, right - REMOVE_W, rcy - 7, REMOVE_W, 14)) {
				McUI.click();
				m.remove(wp);
				inputs.remove(wp);
				waypointToggles.remove(wp);
			}
			rowY += MACRO_ROW_H;
		}
		return true;
	}

	private void drawKeyBox(GuiGraphicsExtractor g, KeySetting k, float right, float boxY, double mx, double my, float alpha) {
		boolean isListening = listening == k;
		float bw = keyBoxWidth(k);
		float bx = right - bw;
		McUI.button(g, bx, boxY, bw, CONTROL_H, "", isListening || UI.hover(mx, my, bx, boxY, bw, CONTROL_H), true, alpha);
		String text = isListening ? "> " + k.displayName().getString() + " <" : k.displayName().getString();
		int color = isListening ? McUI.ACTIVE : k.isBound() ? McUI.WHITE : McUI.GRAY;
		McUI.textCenter(g, McUI.trim(text, bw - 6), bx + bw / 2f, boxY + CONTROL_H / 2f, Theme.alpha(color, alpha));
	}

	private float keyBoxWidth(KeySetting k) {
		return Math.max(KEY_BOX_W, McUI.width("> " + k.displayName().getString() + " <") + 8);
	}

	private void renderDropdown(GuiGraphicsExtractor g, double mx, double my, float alpha) {
		List<String> modes = dropdown.modes();
		float dh = modes.size() * DROPDOWN_ITEM_H + 4;
		McUI.popup(g, dropdownX, dropdownY, dropdownW, dh, alpha);
		for (int i = 0; i < modes.size(); i++) {
			float iy = dropdownY + 2 + i * DROPDOWN_ITEM_H;
			String mode = modes.get(i);
			boolean hov = UI.hover(mx, my, dropdownX, iy, dropdownW, DROPDOWN_ITEM_H);
			if (hov) Shapes.rect(g, dropdownX + 1, iy, dropdownW - 2, DROPDOWN_ITEM_H, Theme.alpha(0x30FFFFFF, alpha));
			McUI.text(g, mode, dropdownX + 6, iy + DROPDOWN_ITEM_H / 2f, Theme.alpha(dropdown.is(mode) ? McUI.ACTIVE : hov ? McUI.WHITE : McUI.GRAY, alpha));
		}
	}

	// ------------------------------------------------------------------
	// Input
	// ------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button, float screenW, float screenH) {
		if (listening != null) {
			listening.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
			listening = null;
			return true;
		}
		if (picker != null) {
			if (picker.mouseClicked(mx, my, button)) return true;
			picker = null;
			return true;
		}
		if (dropdown != null) {
			List<String> modes = dropdown.modes();
			for (int i = 0; i < modes.size(); i++) {
				if (UI.hover(mx, my, dropdownX, dropdownY + 2 + i * DROPDOWN_ITEM_H, dropdownW, DROPDOWN_ITEM_H)) {
					McUI.click();
					dropdown.set(modes.get(i));
					break;
				}
			}
			dropdown = null;
			return true;
		}
		for (TextInput in : inputs.values()) in.setFocused(false);
		if (!UI.hover(mx, my, x, y, w, h)) return false;

		float sc = Math.round(scroll.get());
		for (Row r : rows) {
			float ry = y + r.y - sc;
			if (!UI.hover(mx, my, x, ry, w, r.h)) continue;
			Setting<?> s = r.setting;
			float cy = ry + ROW_H / 2f;
			float right = x + w - ROW_PAD;
			if (s instanceof BoolSetting b) {
				McUI.click();
				b.toggle();
			} else if (s instanceof NumberSetting n) {
				float sx = right - SLIDER_W;
				if (mx >= sx) {
					draggingSlider = n;
					sliderX = sx;
					mouseDragged(mx, my);
				}
			} else if (s instanceof ColorSetting c) {
				if (mx >= right - 100) {
					McUI.click();
					float px = Math.min(right - ColorPicker.W, screenW - ColorPicker.W - 6);
					ColorPicker p = new ColorPicker(c, px, cy + 12);
					if (p.y + p.height() > screenH - 6) p.y = cy - 12 - p.height();
					picker = p;
				}
			} else if (s instanceof ModeSetting m) {
				if (mx >= right - modeWidth(m)) {
					McUI.click();
					float widest = 0;
					for (String mode : m.modes()) widest = Math.max(widest, McUI.width(mode));
					dropdownW = Math.max(modeWidth(m), widest + 14);
					dropdownX = right - dropdownW;
					float dh = m.modes().size() * DROPDOWN_ITEM_H + 4;
					dropdownY = Math.round(cy + CONTROL_H / 2f + 1);
					if (dropdownY + dh > screenH - 6) dropdownY = Math.round(cy - CONTROL_H / 2f - 1 - dh);
					dropdown = m;
				}
			} else if (s instanceof KeySetting k) {
				if (mx >= right - keyBoxWidth(k)) {
					McUI.click();
					listening = k;
				}
			} else if (s instanceof TextSetting t) {
				TextInput in = inputs.get(t);
				if (in != null) in.setFocused(true);
			} else if (s instanceof WaypointsSetting) {
				return clickWaypoints(ry, mx, my);
			} else if (s instanceof MacroSetting macros) {
				if (UI.hover(mx, my, right - ADD_W, cy - CONTROL_H / 2f, ADD_W, CONTROL_H)) {
					McUI.click();
					macros.add();
					return true;
				}
				float rowY = ry + ROW_H;
				for (MacroSetting.Macro macro : new ArrayList<>(macros.macros())) {
					float rcy = rowY + MACRO_ROW_H / 2f;
					float keyRight = x + ROW_PAD + KEY_BOX_W;
					if (UI.hover(mx, my, keyRight - keyBoxWidth(macro.key), rcy - CONTROL_H / 2f, keyBoxWidth(macro.key), CONTROL_H)) {
						McUI.click();
						listening = macro.key;
					} else if (UI.hover(mx, my, right - REMOVE_W, rcy - 7, REMOVE_W, 14)) {
						McUI.click();
						macros.remove(macro);
						inputs.remove(macro);
					} else if (UI.hover(mx, my, keyRight + 6, rcy - CONTROL_H / 2f, right - REMOVE_W - 4 - keyRight - 6, CONTROL_H)) {
						TextInput in = inputs.get(macro);
						if (in != null) in.setFocused(true);
					}
					rowY += MACRO_ROW_H;
				}
			}
			return true;
		}
		return true;
	}

	public void mouseDragged(double mx, double my) {
		if (picker != null) picker.mouseDragged(mx, my);
		if (draggingSlider != null) {
			// the handle's middle follows the mouse, like a vanilla slider
			draggingSlider.setProgress((mx - sliderX - 4) / (SLIDER_W - 8));
		}
	}

	public void mouseReleased() {
		draggingSlider = null;
		if (picker != null) picker.mouseReleased();
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (picker != null || dropdown != null) return true;
		if (!UI.hover(mx, my, x, y, w + 8, h)) return false;
		scrollTarget -= (float) amount * 24;
		return true;
	}

	public boolean keyPressed(KeyEvent e) {
		if (listening != null) {
			if (e.key() == InputConstants.KEY_ESCAPE || e.key() == InputConstants.KEY_BACKSPACE) listening.setKey(InputConstants.UNKNOWN);
			else listening.setKey(InputConstants.getKey(e));
			listening = null;
			return true;
		}
		if (picker != null) {
			if (picker.keyPressed(e)) return true;
			if (e.key() == InputConstants.KEY_ESCAPE) {
				picker = null;
				return true;
			}
		}
		if (dropdown != null && e.key() == InputConstants.KEY_ESCAPE) {
			dropdown = null;
			return true;
		}
		for (TextInput in : inputs.values()) if (in.keyPressed(e)) return true;
		return false;
	}

	public boolean charTyped(CharacterEvent e) {
		if (picker != null && picker.charTyped(e)) return true;
		for (TextInput in : inputs.values()) if (in.charTyped(e)) return true;
		return false;
	}

	public boolean hasPopup() {
		return picker != null || dropdown != null || listening != null;
	}
}
