package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.Lumina;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Logo;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.util.MacOS;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/** The Lumina mod menu, opened with right shift. */
public final class LuminaScreen extends Screen {
	private static final int W = 540, H = 330;
	private static final int HEADER_H = 30, FOOTER_H = 28;
	private static final int PAD = 8;
	private static final int TAB_H = 18, TAB_Y = 6, TAB_GAP = 4, TABS_X = 92;
	private static final int SEARCH_W = 120, SEARCH_H = 18;
	private static final int RAIL_W = 84, RAIL_ROW_H = 22;
	private static final int CARD_H = 38, CARD_GAP = 4, COLUMNS = 2;
	private static final int SCROLLBAR_SPACE = 8;
	private static final int FOOTER_BUTTON_Y = H - 24;
	private static final int DONE_W = 60, EDIT_W = 72;
	private static final int RESET_W = 44;

	private final @Nullable Screen parent;
	private final Anim openAnim = new Anim(0, 13);
	private boolean closing;
	private final TextInput search = new TextInput("", 32, "Search mods", s -> gridScrollTarget = 0);
	private @Nullable SettingsPanel settings;
	private final Anim gridScroll = new Anim(0, 20);
	private float gridScrollTarget;
	private final Map<Module, Anim> toggleAnims = new IdentityHashMap<>();

	/** The tabs: the module grid, Cosmetics and the Skin Changer. */
	private enum Page {
		MODS("Mods"), COSMETICS("Cosmetics"), SKINS("Skin Changer");

		final String title;

		Page(String title) {
			this.title = title;
		}
	}

	private Page page = Page.MODS;
	/** The rail's filter; null shows all modules. */
	private @Nullable Category category;
	private final CosmeticsPage cosmetics = new CosmeticsPage(CosmeticsPage.module(), this::openSettings);
	private final SkinsPage skins = new SkinsPage();

	private float offsetX;
	private float offsetY;
	private boolean draggingPanel;
	private double dragStartX, dragStartY;
	private float dragStartOX, dragStartOY;

	/** GUI units per menu unit; everything below works in menu units. */
	private float scale = 1;
	private int px, py;

	public LuminaScreen(@Nullable Screen parent) {
		super(Component.literal("Lumina"));
		this.parent = parent;
		this.offsetX = Lumina.config().uiFloat("menu_x", 0);
		this.offsetY = Lumina.config().uiFloat("menu_y", 0);
		String saved = Lumina.config().uiString("menu_category", "");
		for (Page p : Page.values()) if (p.name().equals(saved)) page = p;
		String filter = Lumina.config().uiString("menu_filter", "");
		for (Category c : Category.values()) if (c.name().equals(filter)) category = c;
	}

	private void showPage(Page p) {
		page = p;
		Lumina.config().ui.addProperty("menu_category", p.name());
		if (p == Page.SKINS) skins.opened();
	}

	private void showCategory(@Nullable Category c) {
		category = c;
		Lumina.config().ui.addProperty("menu_filter", c == null ? "" : c.name());
		gridScrollTarget = 0;
		search.setText("");
		closeSettings();
	}

	public LuminaScreen openModule(Module module) {
		// a module's settings show on the Mods tab, whichever tab the menu was last on
		page = Page.MODS;
		settings = new SettingsPanel(module);
		return this;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		layout();
		if (page == Page.SKINS) skins.opened();
	}

	private void layout() {
		scale = McUI.fitScale();
		float menuW = width / scale, menuH = height / scale;
		float maxX = (menuW - W) / 2f - 4, maxY = (menuH - H) / 2f - 4;
		offsetX = Math.max(-Math.max(0, maxX), Math.min(Math.max(0, maxX), offsetX));
		offsetY = Math.max(-Math.max(0, maxY), Math.min(Math.max(0, maxY), offsetY));
		px = Math.round((menuW - W) / 2f + offsetX);
		py = Math.round((menuH - H) / 2f + offsetY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float a = Anim.easeOutCubic(openAnim.get());
		if (minecraft.level == null) extractPanorama(g, delta);
		Shapes.rect(g, 0, 0, width, height, Theme.alpha(McUI.BACKDROP, a));
		if (minecraft.options.getMenuBackgroundBlurriness() >= 1 && Lumina.menuBlurEnabled()) g.blurBeforeThisStratum();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float open = openAnim.update(closing ? 0 : 1);
		if (closing && open < 0.02f) {
			minecraft.gui.setScreen(parent);
			return;
		}
		float a = Anim.easeOutCubic(open);
		layout();
		double mx = mouseX / scale, my = mouseY / scale;
		boolean live = !closing;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(scale, scale);
		pose.translate(0, (1 - a) * 6);

		McUI.panel(g, px, py, W, H, McUI.BODY, a);
		renderTabs(g, mx, my, a, live);
		renderFooter(g, mx, my, a, live);
		switch (page) {
			case COSMETICS -> {
				if (settings == null) cosmetics.render(g, px + PAD, contentTop() + 6, W - PAD * 2 - SCROLLBAR_SPACE, contentH() - 12, mx, my, a, live);
				else renderSettingsPage(g, px + PAD, W - PAD * 2 - SCROLLBAR_SPACE, mx, my, a, live);
			}
			case SKINS -> skins.render(g, px + PAD, contentTop() + 6, W - PAD * 2 - SCROLLBAR_SPACE, contentH() - 12, mx, my, a, live);
			default -> {
				renderRail(g, mx, my, a, live);
				if (settings == null) renderGrid(g, mx, my, a, live);
				else renderSettingsPage(g, gridX(), gridW(), mx, my, a, live);
			}
		}
		McUI.flushTooltip(g, a);
		pose.popMatrix();
	}

	private int contentTop() {
		return py + HEADER_H;
	}

	private int contentH() {
		return H - HEADER_H - FOOTER_H;
	}

	// ------------------------------------------------------------------
	// Tabs and footer
	// ------------------------------------------------------------------

	private int tabW(Page p) {
		return McUI.width(p.title) + 18;
	}

	private int tabX(Page p) {
		int x = px + TABS_X;
		for (int i = 0; i < p.ordinal(); i++) x += tabW(Page.values()[i]) + TAB_GAP;
		return x;
	}

	private int searchX() {
		return px + W - PAD - SEARCH_W;
	}

	private boolean searchVisible() {
		return page == Page.MODS && settings == null;
	}

	private void renderTabs(GuiGraphicsExtractor g, double mx, double my, float a, boolean live) {
		Logo.draw(g, Logo.FLAT, px + 10, py + 8, 14, Theme.alpha(0xFFFFFFFF, a));
		McUI.title(g, "Lumina", px + 28, py + 15, Theme.alpha(McUI.WHITE, a));
		int tabY = py + TAB_Y;
		for (Page p : Page.values()) {
			boolean hov = live && UI.hover(mx, my, tabX(p), tabY, tabW(p), TAB_H);
			McUI.segment(g, tabX(p), tabY, tabW(p), TAB_H, p.title, page == p, hov, a);
		}
		if (searchVisible()) search.renderVanilla(g, searchX(), py + TAB_Y, SEARCH_W, SEARCH_H, a);
		McUI.headerLine(g, px + 1, py + HEADER_H - 2, W - 2, a);
	}

	private void renderFooter(GuiGraphicsExtractor g, double mx, double my, float a, boolean live) {
		McUI.footerLine(g, px + 1, py + H - FOOTER_H, W - 2, a);
		McUI.text(g, Lumina.gameName() + " · v" + Lumina.VERSION, px + 10, py + H - 14, Theme.alpha(McUI.DARK_GRAY, a));
		int doneX = px + W - PAD - DONE_W, editX = doneX - 4 - EDIT_W, by = py + FOOTER_BUTTON_Y;
		McUI.button(g, editX, by, EDIT_W, McUI.BUTTON_H, "Edit HUD", live && UI.hover(mx, my, editX, by, EDIT_W, McUI.BUTTON_H), true, a);
		McUI.primary(g, doneX, by, DONE_W, McUI.BUTTON_H, "Done", live && UI.hover(mx, my, doneX, by, DONE_W, McUI.BUTTON_H), true, a);
	}

	// ------------------------------------------------------------------
	// Category rail
	// ------------------------------------------------------------------

	private int railY(int index) {
		return contentTop() + 6 + index * RAIL_ROW_H;
	}

	private void renderRail(GuiGraphicsExtractor g, double mx, double my, float a, boolean live) {
		McUI.dividerV(g, px + RAIL_W, contentTop(), contentH(), a);
		for (int i = 0; i <= Category.values().length; i++) {
			Category c = i == 0 ? null : Category.values()[i - 1];
			int x = px + 6, y = railY(i), w = RAIL_W - 12;
			boolean selected = category == c && search.text().isBlank();
			boolean hov = live && UI.hover(mx, my, x, y, w, RAIL_ROW_H - 2);
			McUI.rowBack(g, x, y, w, RAIL_ROW_H - 2, selected, hov, a);
			ModuleIcons.category(g, c, x + 2, y + 2, a);
			McUI.text(g, c == null ? "All" : c.title(), x + 22, y + (RAIL_ROW_H - 2) / 2f, Theme.alpha(selected || hov ? McUI.WHITE : McUI.GRAY, a));
		}
	}

	// ------------------------------------------------------------------
	// Module grid
	// ------------------------------------------------------------------

	private int gridX() {
		return px + RAIL_W + PAD;
	}

	private int gridW() {
		return W - RAIL_W - PAD * 2 - SCROLLBAR_SPACE;
	}

	private int cardW() {
		return (gridW() - CARD_GAP * (COLUMNS - 1)) / COLUMNS;
	}

	private List<Module> visibleModules() {
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>();
		for (Module m : Lumina.modules().all()) {
			if (!m.listed()) continue;
			if (q.isEmpty() ? category != null && m.category() != category : !m.name().toLowerCase(Locale.ROOT).contains(q) && !m.description().toLowerCase(Locale.ROOT).contains(q)) continue;
			list.add(m);
		}
		return list;
	}

	private void renderGrid(GuiGraphicsExtractor g, double mx, double my, float a, boolean live) {
		int gx = gridX(), gy = contentTop() + 6, gh = contentH() - 12;
		List<Module> list = visibleModules();
		int rows = (list.size() + COLUMNS - 1) / COLUMNS;
		float contentH = rows * (CARD_H + CARD_GAP) - CARD_GAP;
		gridScrollTarget = Math.max(0, Math.min(Math.max(0, contentH - gh), gridScrollTarget));
		float sc = Math.round(gridScroll.update(gridScrollTarget));
		boolean inside = live && UI.hover(mx, my, gx, gy, gridW(), gh);

		McUI.scissor(g, gx, gy, gridW(), gh);
		for (int i = 0; i < list.size(); i++) {
			float cardX = gx + (i % COLUMNS) * (cardW() + CARD_GAP);
			float cardY = gy + (i / COLUMNS) * (CARD_H + CARD_GAP) - sc;
			if (cardY > gy + gh || cardY + CARD_H < gy) continue;
			renderCard(g, list.get(i), cardX, cardY, inside && UI.hover(mx, my, cardX, cardY, cardW(), CARD_H), a);
		}
		if (list.isEmpty()) McUI.textCenter(g, "No mods match \"" + search.text() + "\"", gx + gridW() / 2f, gy + 40, Theme.alpha(McUI.GRAY, a));
		g.disableScissor();
		McUI.scrollbar(g, gx + gridW() + 2, gy, gh, contentH, sc, a);
	}

	private void renderCard(GuiGraphicsExtractor g, Module m, float x, float y, boolean hov, float a) {
		float on = toggleAnims.computeIfAbsent(m, k -> new Anim(m.enabled() ? 1 : 0, 18)).update(m.enabled());
		McUI.card(g, x, y, cardW(), CARD_H, hov, false, a);

		float slotX = x + 6, slotY = y + (CARD_H - McUI.SLOT) / 2f;
		McUI.slot(g, slotX, slotY, McUI.SLOT, m.enabled(), a);
		ModuleIcons.module(g, m.id(), slotX + 2, slotY + 2, a);

		float tx = slotX + McUI.SLOT + 8;
		float textW = cardW() - (tx - x) - McUI.SWITCH_W - 12;
		McUI.title(g, McUI.trim(m.name(), textW), tx, y + 9, Theme.alpha(m.enabled() ? McUI.WHITE : McUI.GRAY, a));
		List<String> lines = McUI.wrap(m.description(), textW, 2);
		for (int i = 0; i < lines.size(); i++) McUI.text(g, lines.get(i), tx, y + 20 + i * 9, Theme.alpha(McUI.DARK_GRAY, a));

		if (m.canToggle()) McUI.toggle(g, x + cardW() - 7 - McUI.SWITCH_W, y + (CARD_H - McUI.SWITCH_H) / 2f, on, a);
	}

	// ------------------------------------------------------------------
	// Settings page
	// ------------------------------------------------------------------

	private int settingsTop() {
		return contentTop() + 6;
	}

	private void renderSettingsPage(GuiGraphicsExtractor g, int x, int w, double mx, double my, float a, boolean live) {
		Module m = settings.module();
		int y = settingsTop();
		McUI.button(g, x, y, 20, 20, "", live && UI.hover(mx, my, x, y, 20, 20), true, a);
		UI.icon(g, dev.lumina.client.render.Icons.CHEVRON_LEFT, x + 10, y + 10, 10, Theme.alpha(McUI.WHITE, a));
		McUI.slot(g, x + 24, y, McUI.SLOT, m.enabled(), a);
		ModuleIcons.module(g, m.id(), x + 26, y + 2, a);
		int right = x + w;
		float tx = x + 24 + McUI.SLOT + 6;
		float textW = w - (tx - x) - (m.canToggle() ? McUI.SWITCH_W + 8 : 0) - RESET_W - 4;
		McUI.title(g, McUI.trim(m.name(), textW), tx, y + 5, Theme.alpha(McUI.WHITE, a));
		McUI.text(g, McUI.trim(m.description(), textW), tx, y + 15, Theme.alpha(McUI.DARK_GRAY, a));

		if (m.canToggle()) {
			float on = toggleAnims.computeIfAbsent(m, k -> new Anim(m.enabled() ? 1 : 0, 18)).update(m.enabled());
			McUI.toggle(g, right - McUI.SWITCH_W, y + (20 - McUI.SWITCH_H) / 2f, on, a);
		}
		int resetX = resetX(m, right);
		McUI.button(g, resetX, y, RESET_W, 20, "Reset", live && UI.hover(mx, my, resetX, y, RESET_W, 20), true, a);

		int listY = y + 26;
		int listH = contentTop() + contentH() - 6 - listY;
		float preview = m.previewHeight();
		if (preview > 0) {
			// stays in place while the settings below scroll, so changes are visible right away
			m.renderPreview(g, x, listY, w, preview, a);
			listY += preview + 6;
			listH -= preview + 6;
		}
		settings.render(g, x, listY, w, listH, live ? mx : -1, live ? my : -1, a);
	}

	private int resetX(Module m, int right) {
		return right - (m.canToggle() ? McUI.SWITCH_W + 8 : 0) - RESET_W;
	}

	// ------------------------------------------------------------------
	// Input
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) return true;
		layout();
		double mx = event.x() / scale, my = event.y() / scale;
		int button = event.button();
		search.setFocused(false);

		if (settings != null && settings.hasPopup()) {
			settings.mouseClicked(mx, my, button, width / scale, height / scale);
			return true;
		}
		if (!UI.hover(mx, my, px, py, W, H)) return true;

		for (Page p : Page.values()) {
			if (UI.hover(mx, my, tabX(p), py + TAB_Y, tabW(p), TAB_H)) {
				McUI.click();
				showPage(p);
				gridScrollTarget = 0;
				closeSettings();
				return true;
			}
		}
		int doneX = px + W - PAD - DONE_W, editX = doneX - 4 - EDIT_W, by = py + FOOTER_BUTTON_Y;
		if (UI.hover(mx, my, doneX, by, DONE_W, McUI.BUTTON_H)) {
			McUI.click();
			onClose();
			return true;
		}
		if (UI.hover(mx, my, editX, by, EDIT_W, McUI.BUTTON_H)) {
			McUI.click();
			minecraft.gui.setScreen(new HudEditorScreen(this));
			return true;
		}
		if (searchVisible() && UI.hover(mx, my, searchX(), py + TAB_Y, SEARCH_W, SEARCH_H)) {
			search.setFocused(true);
			return true;
		}
		if (my < contentTop()) {
			startDrag(event);
			return true;
		}

		if (page == Page.MODS) {
			for (int i = 0; i <= Category.values().length; i++) {
				if (UI.hover(mx, my, px + 6, railY(i), RAIL_W - 12, RAIL_ROW_H - 2)) {
					McUI.click();
					showCategory(i == 0 ? null : Category.values()[i - 1]);
					return true;
				}
			}
		}

		if (settings != null) {
			int x = page == Page.MODS ? gridX() : px + PAD;
			int w = page == Page.MODS ? gridW() : W - PAD * 2 - SCROLLBAR_SPACE;
			clickSettingsHeader(mx, my, button, x, w);
			return true;
		}

		switch (page) {
			case COSMETICS -> cosmetics.mouseClicked(mx, my);
			case SKINS -> skins.mouseClicked(mx, my);
			default -> clickGrid(event, mx, my);
		}
		return true;
	}

	private void clickSettingsHeader(double mx, double my, int button, int x, int w) {
		Module m = settings.module();
		int y = settingsTop(), right = x + w;
		if (UI.hover(mx, my, x, y, 20, 20)) {
			McUI.click();
			closeSettings();
		} else if (m.canToggle() && UI.hover(mx, my, right - McUI.SWITCH_W - 2, y, McUI.SWITCH_W + 4, 20)) {
			McUI.click();
			m.toggle();
		} else if (UI.hover(mx, my, resetX(m, right), y, RESET_W, 20)) {
			McUI.click();
			m.resetSettings();
			if (m instanceof dev.lumina.client.module.HudModule h) h.resetPosition();
		} else {
			settings.mouseClicked(mx, my, button, width / scale, height / scale);
		}
	}

	private void clickGrid(MouseButtonEvent event, double mx, double my) {
		int gx = gridX(), gy = contentTop() + 6, gh = contentH() - 12;
		if (!UI.hover(mx, my, gx, gy, gridW(), gh)) return;
		List<Module> list = visibleModules();
		float sc = Math.round(gridScroll.get());
		for (int i = 0; i < list.size(); i++) {
			float cardX = gx + (i % COLUMNS) * (cardW() + CARD_GAP);
			float cardY = gy + (i / COLUMNS) * (CARD_H + CARD_GAP) - sc;
			if (!UI.hover(mx, my, cardX, cardY, cardW(), CARD_H)) continue;
			Module m = list.get(i);
			float toggleX = cardX + cardW() - 7 - McUI.SWITCH_W;
			boolean onToggle = m.canToggle() && UI.hover(mx, my, toggleX - 3, cardY, McUI.SWITCH_W + 6, CARD_H);
			McUI.click();
			if (m.canToggle() && (MacOS.button(event) == InputConstants.MOUSE_BUTTON_RIGHT || onToggle)) m.toggle();
			else openSettings(m);
			return;
		}
	}

	private void startDrag(MouseButtonEvent event) {
		draggingPanel = true;
		dragStartX = event.x();
		dragStartY = event.y();
		dragStartOX = offsetX;
		dragStartOY = offsetY;
	}

	private void openSettings(Module m) {
		settings = new SettingsPanel(m);
	}

	private void closeSettings() {
		settings = null;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingPanel) {
			draggingPanel = false;
			Lumina.config().ui.addProperty("menu_x", offsetX);
			Lumina.config().ui.addProperty("menu_y", offsetY);
			dev.lumina.client.setting.SettingChanges.markDirty();
		}
		if (settings != null) settings.mouseReleased();
		cosmetics.mouseReleased();
		skins.mouseReleased();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingPanel) {
			offsetX = dragStartOX + (float) ((event.x() - dragStartX) / scale);
			offsetY = dragStartOY + (float) ((event.y() - dragStartY) / scale);
			return true;
		}
		double mx = event.x() / scale, my = event.y() / scale;
		if (settings != null) settings.mouseDragged(mx, my);
		else if (page == Page.COSMETICS) cosmetics.mouseDragged(mx);
		else if (page == Page.SKINS) skins.mouseDragged(mx);
		return true;
	}

	/** Files dragged onto the window become skins on the Skin Changer page, capes everywhere else. */
	@Override
	public void onFilesDrop(List<java.nio.file.Path> files) {
		if (closing || files.isEmpty()) return;
		closeSettings();
		if (page == Page.SKINS) {
			skins.filesDropped(files);
			return;
		}
		if (page != Page.COSMETICS) showPage(Page.COSMETICS);
		cosmetics.filesDropped(files);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double mx = mouseX / scale, my = mouseY / scale;
		if (settings != null) {
			settings.mouseScrolled(mx, my, scrollY);
			return true;
		}
		switch (page) {
			case COSMETICS -> cosmetics.scrolled(scrollY);
			case SKINS -> skins.scrolled(scrollY);
			default -> gridScrollTarget -= (float) scrollY * 30;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (settings != null && settings.capturesKeyboard()) {
			settings.keyPressed(event);
			return true;
		}
		if (search.focused()) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				search.setFocused(false);
				return true;
			}
			search.keyPressed(event);
			return true;
		}
		if (settings != null && settings.keyPressed(event)) return true;
		if (event.key() == InputConstants.KEY_ESCAPE) {
			if (settings != null) closeSettings();
			else onClose();
			return true;
		}
		if (Lumina.MENU_KEY.matches(event)) {
			onClose();
			return true;
		}
		if ((event.modifiers() & InputConstants.MOD_CONTROL) != 0 && event.key() == InputConstants.KEY_F) {
			if (searchVisible()) search.setFocused(true);
			return true;
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (settings != null && settings.charTyped(event)) return true;
		if (search.focused()) return search.charTyped(event);
		// start typing anywhere to search
		if (searchVisible() && event.isAllowedChatCharacter() && event.codepoint() != ' ') {
			search.setFocused(true);
			return search.charTyped(event);
		}
		return false;
	}

	@Override
	public void onClose() {
		if (closing) return;
		closing = true;
		Lumina.config().save();
	}

	@Override
	public void removed() {
		Lumina.config().save();
	}
}
