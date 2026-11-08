package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.account.AccountBridge;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Logo;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Sprites;
import dev.lumina.client.render.Theme;
import net.minecraft.client.renderer.RenderPipelines;
import dev.lumina.client.render.font.Fonts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Lumina's main menu: the vanilla panorama, blurred, with a centered logo and button column.
 * Singleplayer and Multiplayer are the two large buttons; Options and Quit sit side by side below
 * them. The name in the corner opens the alt manager.
 */
public final class LuminaTitleScreen extends Screen {
	private static final float BUTTON_W = 200;
	private static final float BUTTON_H = 24;
	private static final float SMALL_H = 20;
	private static final float GAP = 6;
	private static final float RADIUS = 7;
	private static final float LOGO_H = 70;
	/** Entries before this index are large buttons, the rest share the small row. */
	private static final int LARGE = 2;

	private record Entry(String label, String icon, Runnable action, boolean enabled) {
	}

	private record Box(float x, float y, float w, float h) {
		boolean contains(double mx, double my) {
			return UI.hover(mx, my, x, y, w, h);
		}
	}

	private final List<Entry> entries = new ArrayList<>();
	private final List<Anim> hovers = new ArrayList<>();
	private final Anim accountHover = new Anim(0, 16);
	private final Anim intro = new Anim(0, 4.5f);
	private final AccountMenu accounts = new AccountMenu();
	private final long openedAt = System.currentTimeMillis();
	private float layoutScale = 1;

	public LuminaTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	@Override
	protected void init() {
		entries.clear();
		hovers.clear();
		entries.add(new Entry("Singleplayer", Icons.USER_ROUND, () -> minecraft.setScreen(new SelectWorldScreen(this)), true));
		boolean mp = minecraft.allowsMultiplayer();
		entries.add(new Entry("Multiplayer", Icons.GLOBE, () -> minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)), mp));
		entries.add(new Entry("Options", Icons.SETTINGS, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)), true));
		entries.add(new Entry("Quit Game", Icons.LOG_OUT, minecraft::stop, true));
		for (int i = 0; i < entries.size(); i++) hovers.add(new Anim(0, 16));
		intro.target(1);
		layoutScale = Math.min(1f, (height - 64) / contentHeight());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		renderPanorama(g, delta);
		if (minecraft.options.getMenuBackgroundBlurriness() >= 1) g.blurBeforeThisStratum();
	}

	private float columnX() {
		return width / 2f - BUTTON_W / 2f;
	}

	private float contentHeight() {
		return LOGO_H + LARGE * (BUTTON_H + GAP) + SMALL_H;
	}

	/** Where entry {@code i} sits, in unscaled menu units (see {@link #layoutScale}). */
	private Box box(int i) {
		float x = columnX(), top = columnTop();
		if (i < LARGE) return new Box(x, top + i * (BUTTON_H + GAP), BUTTON_W, BUTTON_H);
		int small = entries.size() - LARGE;
		float w = (BUTTON_W - GAP * (small - 1)) / small;
		return new Box(x + (i - LARGE) * (w + GAP), top + LARGE * (BUTTON_H + GAP), w, SMALL_H);
	}

	/** Screen y of the first button; the logo sits LOGO_H (scaled) above it. */
	private float columnTop() {
		return height / 2f - contentHeight() * layoutScale / 2f + LOGO_H * layoutScale;
	}

	/** The name in the bottom left corner. */
	private Box accountBox() {
		float w = 8 + Fonts.REGULAR.width("Signed in as ", 7) + 9.5f + Fonts.MEDIUM.width(minecraft.getUser().getName(), 7) + (AccountBridge.available() ? 14 : 4);
		return new Box(6, height - 15, w, 14);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float t = intro.update();
		float e = Anim.easeOutCubic(t);
		// dim the panorama evenly, a bit more towards the bottom
		Shapes.rect(g, 0, 0, width, height, Theme.alpha(0x73070708, e));
		g.blit(RenderPipelines.GUI_TEXTURED, Sprites.fadeDown(), 0, (int) (height * 0.55f), 0, 0, width, (int) (height * 0.45f) + 1, Sprites.SIZE, Sprites.SIZE, Sprites.SIZE, Sprites.SIZE, Theme.alpha(0xB3070708, e));

		Matrix3x2fStack pose = g.pose();
		float x = columnX();
		float top = columnTop();
		pose.pushMatrix();
		pose.translate(x, top);
		pose.scale(layoutScale, layoutScale);
		pose.translate(-x, -top);

		// logo, centered over the column
		float center = width / 2f;
		float ly = top - LOGO_H + 16 + (1 - e) * 6;
		float logoW = 30 + 6 + Fonts.SEMIBOLD.width("Lumina", 30);
		float lx = center - logoW / 2f;
		Logo.draw(g, Logo.GLASS, lx, ly - 15, 30, Theme.alpha(0xFFFFFFFF, e));
		Fonts.SEMIBOLD.draw(g, "Lumina", lx + 36, ly - Fonts.SEMIBOLD.ascent(30) + Fonts.SEMIBOLD.capHeight(30) / 2f + 1, 30, Theme.alpha(Theme.TEXT, e));
		String sub = Lumina.gameName();
		Fonts.REGULAR.draw(g, sub, center - Fonts.REGULAR.width(sub, 8) / 2f, ly + 22, 8, Theme.alpha(Theme.TEXT_DIM, e));

		boolean live = !accounts.contains(mouseX, mouseY);
		double mx = (mouseX - x) / layoutScale + x, my = (mouseY - top) / layoutScale + top;
		long elapsed = System.currentTimeMillis() - openedAt;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			Box b = box(i);
			// hover is tested on the resting position, so the lift below cannot make it flicker
			float h = hovers.get(i).update(live && entry.enabled && b.contains(mx, my));
			float a = Anim.easeOutCubic(Math.max(0, Math.min(1, (elapsed - 120 - i * 45) / 420f)));
			float by = b.y + (1 - a) * 10 - h;
			drawButton(g, entry, b.x, by, b.w, b.h, h, a, i < LARGE);
		}
		pose.popMatrix();

		drawAccount(g, mouseX, mouseY, live, e);
		UI.textRight(g, Fonts.REGULAR, "v" + Lumina.VERSION, width - 10, height - 8, 7, Theme.alpha(Theme.TEXT_MUTED, e));
		Box account = accountBox();
		accounts.render(g, account.x, account.y - 4, height, mouseX, mouseY);
	}

	/**
	 * Dark glass with a soft shadow and a light top edge; brightens and lifts on hover. Large and small
	 * ones are the same button, so the bottom row reads just as clearly.
	 */
	private static void drawButton(GuiGraphics g, Entry entry, float x, float y, float w, float h, float hover, float a, boolean large) {
		float hl = Shapes.hairline();
		Shapes.shadow(g, x, y + 2, w, h, RADIUS, 10, Theme.alpha(0x52000000, a * (0.6f + 0.4f * hover)));
		// a dark base keeps the glass readable over bright panoramas
		Shapes.round(g, x, y, w, h, RADIUS, Theme.alpha(0x8C0D0D10, a));
		Shapes.round(g, x, y, w, h, RADIUS, Theme.alpha(Theme.mix(0x12FFFFFF, 0x2EFFFFFF, hover), a));
		Shapes.roundOutline(g, x, y, w, h, RADIUS, hl, Theme.alpha(Theme.mix(0x24FFFFFF, 0x59FFFFFF, hover), a));
		Shapes.rect(g, x + RADIUS, y + hl, w - 2 * RADIUS, hl, Theme.alpha(Theme.mix(0x24FFFFFF, 0x4DFFFFFF, hover), a));
		int text = entry.enabled ? Theme.mix(0xFFE2E2E6, 0xFFFFFFFF, hover) : Theme.TEXT_MUTED;
		int icon = entry.enabled ? Theme.mix(0xFFB4B4BC, 0xFFFFFFFF, hover) : Theme.TEXT_MUTED;
		drawLabel(g, entry, x, y, w, h, large ? 9 : 8.5f, large ? 10 : 9.5f, text, icon, a);
	}

	/** Icon and label, centered as a group. */
	private static void drawLabel(GuiGraphics g, Entry entry, float x, float y, float w, float h, float size, float iconSize, int text, int icon, float a) {
		float iconW = iconSize + 4;
		float groupW = iconW + Fonts.MEDIUM.width(entry.label, size);
		float gx = x + (w - groupW) / 2f;
		float cy = y + h / 2f;
		UI.icon(g, entry.icon, gx + iconSize / 2f, cy, iconSize, Theme.alpha(icon, a));
		UI.text(g, Fonts.MEDIUM, entry.label, gx + iconW, cy, size, Theme.alpha(text, a));
	}

	/** Who is signed in; opens the alt manager when the launcher started the game. */
	private void drawAccount(GuiGraphics g, int mouseX, int mouseY, boolean live, float e) {
		Box b = accountBox();
		boolean canOpen = AccountBridge.available();
		float h = accountHover.update(accounts.isOpen() || (live && canOpen && b.contains(mouseX, mouseY)));
		if (h > 0.01f) Shapes.round(g, b.x, b.y, b.w, b.h, 4, Theme.alpha(0x26FFFFFF, h * e));
		float cy = b.y + b.h / 2f;
		UI.text(g, Fonts.REGULAR, "Signed in as", 10, cy, 7, Theme.alpha(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_DIM, h), e));
		float nameX = 10 + Fonts.REGULAR.width("Signed in as ", 7);
		Logo.draw(g, Logo.FLAT, nameX, cy - 3.5f, 7, Theme.alpha(Theme.TEXT_DIM, e));
		nameX += 9.5f;
		String name = minecraft.getUser().getName();
		UI.text(g, Fonts.MEDIUM, name, nameX, cy, 7, Theme.alpha(Theme.mix(Theme.TEXT_DIM, Theme.TEXT, h), e));
		if (canOpen) UI.icon(g, Icons.CHEVRON_RIGHT, nameX + Fonts.MEDIUM.width(name, 7) + 7, cy, 7, Theme.alpha(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT, h), e));
	}

	private void clickSound() {
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (accounts.isOpen()) {
			if (accounts.click(event.x(), event.y())) {
				clickSound();
				return true;
			}
			boolean onName = accountBox().contains(event.x(), event.y());
			accounts.close();
			if (onName) return true;
		}
		if (AccountBridge.available() && accountBox().contains(event.x(), event.y())) {
			clickSound();
			accounts.toggle();
			return true;
		}
		float x = columnX(), top = columnTop();
		double mx = (event.x() - x) / layoutScale + x, my = (event.y() - top) / layoutScale + top;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (entry.enabled && box(i).contains(mx, my)) {
				clickSound();
				entry.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		accounts.scrolled(scrollY);
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE && accounts.isOpen()) {
			accounts.close();
			return true;
		}
		if (Lumina.MENU_KEY.matches(event)) {
			minecraft.setScreen(new LuminaScreen(this));
			return true;
		}
		return super.keyPressed(event);
	}
}
