package dev.lumina.client.feature.hud;

import dev.lumina.client.LuminaKeys;
import dev.lumina.client.gui.SpotifyScreen;
import dev.lumina.client.gui.UI;
import dev.lumina.client.module.HudModule;
import dev.lumina.client.module.HudStyle;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.KeySetting;
import dev.lumina.client.setting.NumberSetting;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** A small player for the Spotify app: song, cover, progress and buttons. Windows only, see {@link SpotifyBridge}. */
public final class SpotifyModule extends HudModule {
	private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
	private static final float W = 178, H = 52;
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("lumina", "textures/gui/module/spotify.png");
	private static final float PAD = 6, COVER = 40;
	private static final float TEXT_X = PAD + COVER + 6, TEXT_W = W - TEXT_X - PAD;
	private static final float TITLE_SIZE = 9, SMALL_SIZE = 7.5f;
	private static final float BAR_Y = 28, BAR_H = 3;
	/** The bar is thin; this much around it counts as a click on it. */
	private static final float BAR_GRAB = 3;
	private static final float ROW_Y = 39, BUTTON = 14, BUTTON_GAP = 1, ICON = 8.5f;

	private enum Button { PREVIOUS, REWIND, PLAY_PAUSE, FORWARD, NEXT }

	private final KeyMapping cursorKey = LuminaKeys.register("spotify_cursor", GLFW.GLFW_KEY_N);
	private final NumberSetting seekStep;
	private final ColorSetting barColor;
	private final HudStyle style;
	private final SpotifyBridge bridge = new SpotifyBridge();

	public SpotifyModule() {
		super("spotify", "Spotify", "Shows what Spotify plays, with buttons to control it", Icons.MUSIC, true, AnchorX.RIGHT, AnchorY.BOTTOM, 4, 24);
		add(new KeySetting("cursor_key", "Mouse Cursor Key", cursorKey));
		seekStep = add(new NumberSetting("seek_step", "Seek Step", 10, 5, 60, 5).suffix(" s"));
		barColor = add(new ColorSetting("bar_color", "Progress Color", 0xFF1DB954));
		style = style(true);
	}

	@Override
	public void tick() {
		if (enabled() && WINDOWS) bridge.keepRunning();
		else bridge.stop();
		while (cursorKey.consumeClick()) {
			if (mc().screen == null && enabled() && shouldRender()) mc().setScreen(new SpotifyScreen(this, cursorKey));
		}
	}

	@Override
	public boolean shouldRender() {
		return bridge.state() != null;
	}

	@Override
	public float width() {
		return W;
	}

	@Override
	public float height() {
		return H;
	}

	/** The mouse is only free while one of these screens is open. */
	private boolean cursorFree() {
		Screen screen = mc().screen;
		return screen instanceof SpotifyScreen || screen instanceof ChatScreen;
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
		SpotifyBridge.State state = bridge.state();
		if (state == null) state = new SpotifyBridge.State("Song Title", "Artist", true, 42, 180, true, System.currentTimeMillis());
		style.drawBackground(g, 0, 0, W, H);
		renderCover(g);

		var font = style.font();
		style.drawText(g, font.trimToWidth(state.title(), TITLE_SIZE, TEXT_W), TEXT_X, PAD - 1, TITLE_SIZE, style.text());
		style.drawText(g, font.trimToWidth(state.artist(), SMALL_SIZE, TEXT_W), TEXT_X, PAD + 10, SMALL_SIZE, style.accent());

		double mx = -1, my = -1;
		if (!preview && cursorFree()) {
			var window = mc().getWindow();
			mx = (mc().mouseHandler.getScaledXPos(window) - x(window.getGuiScaledWidth())) / scale();
			my = (mc().mouseHandler.getScaledYPos(window) - y(window.getGuiScaledHeight())) / scale();
		}
		renderProgress(g, state, mx, my);
		renderTimes(g, state);
		for (Button b : Button.values()) renderButton(g, b, state, mx, my);
	}

	private void renderCover(GuiGraphics g) {
		if (bridge.hasCover()) {
			int size = SpotifyBridge.COVER_SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, SpotifyBridge.COVER, (int) PAD, (int) PAD, 0, 0, (int) COVER, (int) COVER, size, size, size, size, 0xFFFFFFFF);
		} else {
			Shapes.rect(g, PAD, PAD, COVER, COVER, 0x33FFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, LOGO, (int) (PAD + COVER / 2f) - 16, (int) (PAD + COVER / 2f) - 16, 0, 0, 32, 32, 32, 32, 32, 32, 0xFFFFFFFF);
		}
	}

	private void renderProgress(GuiGraphics g, SpotifyBridge.State state, double mx, double my) {
		boolean hover = state.canSeek() && UI.hover(mx, my, TEXT_X, BAR_Y - BAR_GRAB, TEXT_W, BAR_H + BAR_GRAB * 2);
		float h = hover ? BAR_H + 1 : BAR_H, y = BAR_Y - (h - BAR_H) / 2f;
		Shapes.round(g, TEXT_X, y, TEXT_W, h, h / 2f, 0x33FFFFFF);
		if (state.duration() > 0) {
			float done = (float) (state.positionNow() / state.duration());
			Shapes.round(g, TEXT_X, y, TEXT_W * done, h, h / 2f, barColor.color());
		}
	}

	private void renderTimes(GuiGraphics g, SpotifyBridge.State state) {
		if (state.duration() <= 0) return;
		var font = style.font();
		float y = ROW_Y + (BUTTON - font.lineHeight(SMALL_SIZE)) / 2f;
		String total = time(state.duration());
		style.drawText(g, time(state.positionNow()), TEXT_X, y, SMALL_SIZE, style.accent());
		style.drawText(g, total, TEXT_X + TEXT_W - font.width(total, SMALL_SIZE), y, SMALL_SIZE, style.accent());
	}

	private static String time(double seconds) {
		int s = (int) seconds;
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	private static float buttonX(Button b) {
		float row = Button.values().length * BUTTON + (Button.values().length - 1) * BUTTON_GAP;
		return TEXT_X + (TEXT_W - row) / 2f + b.ordinal() * (BUTTON + BUTTON_GAP);
	}

	private static boolean isSeek(Button b) {
		return b == Button.REWIND || b == Button.FORWARD;
	}

	private void renderButton(GuiGraphics g, Button b, SpotifyBridge.State state, double mx, double my) {
		float x = buttonX(b);
		boolean usable = !isSeek(b) || state.canSeek();
		if (usable && UI.hover(mx, my, x, ROW_Y, BUTTON, BUTTON)) Shapes.round(g, x, ROW_Y, BUTTON, BUTTON, 3, 0x33FFFFFF);
		String icon = switch (b) {
			case PREVIOUS -> Icons.SKIP_BACK;
			case REWIND -> Icons.REWIND;
			case PLAY_PAUSE -> state.playing() ? Icons.PAUSE : Icons.PLAY;
			case FORWARD -> Icons.FAST_FORWARD;
			case NEXT -> Icons.SKIP_FORWARD;
		};
		UI.icon(g, icon, x + BUTTON / 2f, ROW_Y + BUTTON / 2f, ICON, usable ? style.text() : Theme.alpha(style.text(), 0.35f));
	}

	/** A click at screen position (mouseX, mouseY) while the mouse is free; true if it landed on the widget. */
	public boolean click(double mouseX, double mouseY) {
		SpotifyBridge.State state = bridge.state();
		if (state == null || !enabled()) return false;
		var window = mc().getWindow();
		double mx = (mouseX - x(window.getGuiScaledWidth())) / scale(), my = (mouseY - y(window.getGuiScaledHeight())) / scale();
		if (!UI.hover(mx, my, 0, 0, W, H)) return false;
		if (state.canSeek() && state.duration() > 0 && UI.hover(mx, my, TEXT_X, BAR_Y - BAR_GRAB, TEXT_W, BAR_H + BAR_GRAB * 2)) {
			bridge.seek((mx - TEXT_X) / TEXT_W * state.duration());
			return true;
		}
		for (Button b : Button.values()) {
			if (UI.hover(mx, my, buttonX(b), ROW_Y, BUTTON, BUTTON)) press(b, state);
		}
		return true;
	}

	private void press(Button b, SpotifyBridge.State state) {
		switch (b) {
			case PREVIOUS -> bridge.previous();
			case PLAY_PAUSE -> bridge.playPause();
			case NEXT -> bridge.next();
			case REWIND -> {
				if (state.canSeek()) bridge.seek(Math.max(0, state.positionNow() - seekStep.get()));
			}
			case FORWARD -> {
				if (state.canSeek()) bridge.seek(Math.min(state.duration(), state.positionNow() + seekStep.get()));
			}
		}
	}
}
