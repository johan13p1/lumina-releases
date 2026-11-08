package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.account.AccountBridge;
import dev.lumina.client.feature.account.AccountSwitcher;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.render.font.Fonts;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** The alt manager: the launcher's accounts next to the account button of the main menu. */
final class AccountMenu {
	private static final float WIDTH = 156;
	private static final float ROW = 28;
	private static final float PAD = 4;
	private static final float LINE_H = 10;
	private static final float MESSAGE_SIZE = 8;
	private static final int FACE = 20;
	private static final int SURFACE = 0xFF18181A;
	private static final int RAISED = 0xFF29292C;
	private static final int LINE = 0x24EDEDED;
	private static final Map<UUID, Identifier> FACES = new ConcurrentHashMap<>();
	private static final Set<UUID> REQUESTED = ConcurrentHashMap.newKeySet();

	private final Minecraft mc = Minecraft.getInstance();
	private boolean open;
	private boolean loaded;
	private List<AccountBridge.Account> accounts = List.of();
	private @Nullable String busy;
	private @Nullable String error;
	private int scroll;
	private float x, y, h;
	private int visibleRows;

	boolean isOpen() {
		return open;
	}

	void close() {
		if (busy == null) open = false;
	}

	void toggle() {
		if (open) {
			close();
			return;
		}
		open = true;
		error = null;
		scroll = 0;
		refresh();
	}

	private void refresh() {
		AccountBridge.list().whenCompleteAsync((list, e) -> {
			if (e != null) error = message(e);
			else accounts = list.accounts();
			loaded = true;
		}, mc::execute);
	}

	private void switchTo(AccountBridge.Account account) {
		error = null;
		busy = "Switching to " + account.name();
		AccountBridge.use(account.id())
			.thenApplyAsync(AccountSwitcher::prepare, Util.nonCriticalIoPool())
			.whenCompleteAsync((prepared, e) -> {
				busy = null;
				if (e != null) {
					error = message(e);
					return;
				}
				AccountSwitcher.apply(prepared);
				Lumina.LOG.info("Signed in as {}", prepared.user().getName());
				open = false;
			}, mc::execute);
	}

	private void addMicrosoft() {
		error = null;
		busy = "Waiting for the sign-in window";
		AccountBridge.addMicrosoft().whenCompleteAsync((list, e) -> {
			busy = null;
			if (e != null) {
				String text = message(e);
				if (!text.toLowerCase(Locale.ROOT).contains("cancel")) error = text;
				return;
			}
			accounts = list.accounts();
			for (AccountBridge.Account account : accounts) {
				if (account.id().equals(list.selected())) switchTo(account);
			}
		}, mc::execute);
	}

	private void remove(AccountBridge.Account account) {
		error = null;
		AccountBridge.remove(account.id()).whenCompleteAsync((list, e) -> {
			if (e != null) error = message(e);
			else accounts = list.accounts();
		}, mc::execute);
	}

	private static String message(Throwable e) {
		Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
		return cause.getMessage() != null ? cause.getMessage() : "Something went wrong";
	}

	private boolean isCurrent(AccountBridge.Account account) {
		return account.uuid().equals(mc.getUser().getProfileId());
	}

	/** Words of the status text as lines that fit the panel. */
	private List<String> messageLines() {
		String text = busy != null ? busy : error != null ? error : !loaded ? "Loading" : accounts.isEmpty() ? "No accounts yet" : null;
		List<String> lines = new ArrayList<>();
		if (text == null) return lines;
		float max = WIDTH - 2 * (PAD + 4);
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (Fonts.REGULAR.width(next, MESSAGE_SIZE) > max && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		lines.add(line.toString());
		return lines;
	}

	/** Opens to the right of the account button, with the same bottom edge. */
	void render(GuiGraphicsExtractor g, float anchorX, float anchorBottom, int screenH, double mx, double my) {
		if (!open) return;
		List<String> lines = messageLines();
		float messageH = lines.isEmpty() ? 0 : lines.size() * LINE_H + 8;
		int maxRows = Math.max(1, (int) ((screenH - 16 - messageH - ROW - 2 * PAD - 1) / ROW));
		visibleRows = Math.min(accounts.size(), maxRows);
		scroll = Math.max(0, Math.min(scroll, accounts.size() - visibleRows));
		x = anchorX;
		h = 2 * PAD + visibleRows * ROW + (visibleRows > 0 ? 5 : 0) + ROW + messageH;
		y = anchorBottom - h;

		float hl = Shapes.hairline();
		Shapes.shadow(g, x, y + 2, WIDTH, h, 8, 12, 0x66000000);
		Shapes.round(g, x, y, WIDTH, h, 8, SURFACE);
		Shapes.roundOutline(g, x, y, WIDTH, h, 8, hl, LINE);
		boolean idle = busy == null;
		float rowX = x + PAD, rowW = WIDTH - 2 * PAD, rowY = y + PAD;
		for (int i = 0; i < visibleRows; i++) {
			AccountBridge.Account account = accounts.get(i + scroll);
			boolean current = isCurrent(account);
			boolean hover = idle && UI.hover(mx, my, rowX, rowY, rowW, ROW);
			if (hover && !current) Shapes.round(g, rowX, rowY, rowW, ROW, 4, RAISED);
			face(g, account.uuid(), account.name(), rowX + 4, rowY + 4, FACE, 1);
			UI.text(g, Fonts.MEDIUM, account.name(), rowX + 30, rowY + 10, 9, Theme.TEXT);
			UI.text(g, Fonts.REGULAR, account.microsoft() ? "Microsoft" : "Offline", rowX + 30, rowY + 19, 7.5f, Theme.TEXT_DIM);
			if (current) UI.icon(g, Icons.CHECK, rowX + rowW - 12, rowY + ROW / 2f, 10, Theme.TEXT);
			else if (hover) {
				boolean overRemove = UI.hover(mx, my, rowX + rowW - 22, rowY, 22, ROW);
				UI.icon(g, Icons.X, rowX + rowW - 12, rowY + ROW / 2f, 10, overRemove ? Theme.DANGER : Theme.TEXT_DIM);
			}
			rowY += ROW;
		}
		if (visibleRows > 0) {
			Shapes.rect(g, x + PAD + 4, rowY + 2, WIDTH - 2 * PAD - 8, hl, LINE);
			rowY += 5;
		}
		boolean overAdd = idle && UI.hover(mx, my, rowX, rowY, rowW, ROW);
		if (overAdd) Shapes.round(g, rowX, rowY, rowW, ROW, 4, RAISED);
		int addColor = idle ? overAdd ? Theme.TEXT : Theme.TEXT_DIM : Theme.TEXT_MUTED;
		UI.icon(g, Icons.PLUS, rowX + 4 + FACE / 2f, rowY + ROW / 2f, 12, addColor);
		UI.text(g, Fonts.MEDIUM, "Add Microsoft account", rowX + 30, rowY + ROW / 2f, 9, addColor);
		rowY += ROW;
		float textY = rowY + 4 + LINE_H / 2f;
		int messageColor = error != null && busy == null ? 0xFFEC7F84 : Theme.TEXT_DIM;
		for (String line : lines) {
			UI.text(g, Fonts.REGULAR, line, rowX + 4, textY, MESSAGE_SIZE, messageColor);
			textY += LINE_H;
		}
	}

	boolean contains(double mx, double my) {
		return open && UI.hover(mx, my, x, y, WIDTH, h);
	}

	/** True when the click was inside the panel. */
	boolean click(double mx, double my) {
		if (!contains(mx, my)) return false;
		if (busy != null) return true;
		float rowX = x + PAD, rowW = WIDTH - 2 * PAD, rowY = y + PAD;
		for (int i = 0; i < visibleRows; i++) {
			if (UI.hover(mx, my, rowX, rowY, rowW, ROW)) {
				AccountBridge.Account account = accounts.get(i + scroll);
				if (isCurrent(account)) return true;
				if (UI.hover(mx, my, rowX + rowW - 22, rowY, 22, ROW)) remove(account);
				else switchTo(account);
				return true;
			}
			rowY += ROW;
		}
		if (visibleRows > 0) rowY += 5;
		if (UI.hover(mx, my, rowX, rowY, rowW, ROW)) addMicrosoft();
		return true;
	}

	void scrolled(double amount) {
		if (open) scroll -= (int) Math.signum(amount);
	}

	/** The face of an account's skin; its first letter until the skin is there. */
	static void face(GuiGraphicsExtractor g, UUID uuid, String name, float x, float y, int size, float alpha) {
		Identifier id = FACES.get(uuid);
		if (id != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, id, Math.round(x), Math.round(y), 0, 0, size, size, size, size, Theme.alpha(0xFFFFFFFF, alpha));
			return;
		}
		requestFace(uuid);
		Shapes.round(g, x, y, size, size, 3, Theme.alpha(RAISED, alpha));
		UI.textCenter(g, Fonts.MEDIUM, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), x + size / 2f, y + size / 2f, size * 0.5f, Theme.alpha(Theme.TEXT_DIM, alpha));
	}

	private static void requestFace(UUID uuid) {
		if (!AccountBridge.available() || !REQUESTED.add(uuid)) return;
		Minecraft mc = Minecraft.getInstance();
		AccountBridge.skin(uuid).whenComplete((png, e) -> {
			if (e != null || png == null) return;
			try {
				NativeImage face = faceOf(png);
				mc.execute(() -> {
					Identifier id = Identifier.fromNamespaceAndPath("lumina", "gui/account_face/" + uuid);
					mc.getTextureManager().register(id, new DynamicTexture(id::toString, face));
					FACES.put(uuid, id);
				});
			} catch (IOException ex) {
				Lumina.LOG.warn("Could not read the skin of {}", uuid, ex);
			}
		});
	}

	/** The 8x8 face with the hat layer on top. Old 64x32 skins can have an opaque hat layer, which the game ignores too. */
	private static NativeImage faceOf(byte[] png) throws IOException {
		try (NativeImage skin = NativeImage.read(new ByteArrayInputStream(png))) {
			int k = Math.max(1, skin.getWidth() / 64);
			boolean hat = skin.getHeight() >= skin.getWidth();
			NativeImage face = new NativeImage(8 * k, 8 * k, true);
			for (int py = 0; py < 8 * k; py++) {
				for (int px = 0; px < 8 * k; px++) {
					int color = skin.getPixel(8 * k + px, 8 * k + py) | 0xFF000000;
					face.setPixel(px, py, hat ? ARGB.alphaBlend(color, skin.getPixel(40 * k + px, 8 * k + py)) : color);
				}
			}
			return face;
		}
	}
}
