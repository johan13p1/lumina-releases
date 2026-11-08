package dev.lumina.client.gui;

import dev.lumina.client.feature.skins.SkinChanger;
import dev.lumina.client.feature.skins.SkinLibrary;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** The Skin Changer page of the Lumina menu: your skins, a 3D preview and the button that applies one. */
final class SkinsPage {
	private static final int PREVIEW_W = 112;
	/** The front of a skin is 16x32, so this tile shows it at exactly twice that. */
	private static final int TILE_W = 48, TILE_H = 72;
	private static final int GAP = 6;
	private static final int TOP_H = 26;
	private static final int FOOTER_H = 26;
	private static final int MODEL_W = 50;
	private static final int APPLY_W = 70, FOLDER_W = 50;
	private static final int REMOVE = 12;

	private final SkinChanger skins = SkinChanger.get();
	private final Anim scroll = new Anim(0, 20);
	private float scrollTarget;
	private @Nullable String selected; // hash of the selected skin
	private float previewYaw = 160;
	private boolean draggingPreview;
	private double dragX;
	private long lastDrag;
	private boolean picking;
	private @Nullable String toast;
	private boolean toastError;
	private long toastUntil;

	private int x0, y0, w, h;

	private void toast(String text) {
		toast(text, false);
	}

	private void toast(String text, boolean error) {
		toast = text;
		toastError = error;
		toastUntil = Util.getMillis() + 3500;
	}

	void opened() {
		skins.open();
	}

	private SkinLibrary.@Nullable Entry selectedEntry() {
		SkinLibrary.Entry e = skins.library.byHash(selected);
		if (e == null) {
			// default to the skin you wear, else the first one
			e = skins.activeEntry();
			if (e == null && !skins.library.all().isEmpty()) e = skins.library.all().get(0);
			selected = e == null ? null : e.hash();
		}
		return e;
	}

	// ------------------------------------------------------------------
	// layout
	// ------------------------------------------------------------------

	private int gridX() {
		return x0 + PREVIEW_W + 8;
	}

	private int gridY() {
		return y0 + TOP_H;
	}

	private int gridW() {
		return x0 + w - gridX();
	}

	private int gridH() {
		return h - TOP_H - FOOTER_H;
	}

	private int columns() {
		return Math.max(1, (gridW() + GAP) / (TILE_W + GAP));
	}

	private int tileCount() {
		return skins.library.all().size() + 1;
	}

	private float tileX(int i) {
		return gridX() + (i % columns()) * (TILE_W + GAP);
	}

	private float tileY(int i) {
		return gridY() + (i / columns()) * (TILE_H + GAP) - Math.round(scroll.get());
	}

	private int footerY() {
		return y0 + h - McUI.BUTTON_H;
	}

	private int applyX() {
		return gridX() + gridW() - APPLY_W;
	}

	private int folderX() {
		return applyX() - 4 - FOLDER_W;
	}

	// ------------------------------------------------------------------
	// rendering
	// ------------------------------------------------------------------

	void render(GuiGraphics g, int x, int y, int width, int height, double mx, double my, float a, boolean interactive) {
		this.x0 = x;
		this.y0 = y;
		this.w = width;
		this.h = height;
		SkinLibrary.Entry entry = selectedEntry();
		renderModelSwitch(g, entry, mx, my, a, interactive);
		renderPreview(g, x, y, PREVIEW_W, height, entry, a);
		renderGrid(g, mx, my, a, interactive);
		renderFooter(g, entry, mx, my, a, interactive);
	}

	/** Classic or slim arms for the selected skin. */
	private void renderModelSwitch(GuiGraphics g, SkinLibrary.@Nullable Entry entry, double mx, double my, float a, boolean interactive) {
		McUI.text(g, "Your real Minecraft skin", gridX(), y0 + McUI.BUTTON_H / 2f, Theme.alpha(McUI.DARK_GRAY, a));
		boolean slim = entry != null && skins.library.model(entry) == PlayerModelType.SLIM;
		String[] labels = {"Classic", "Slim"};
		for (int i = 0; i < 2; i++) {
			int bx = modelX(i);
			boolean active = (i == 1) == slim;
			boolean hov = interactive && entry != null && UI.hover(mx, my, bx, y0, MODEL_W, McUI.BUTTON_H);
			McUI.segment(g, bx, y0, MODEL_W, McUI.BUTTON_H, labels[i], active, hov, a);
		}
	}

	private int modelX(int i) {
		return x0 + w - (2 - i) * MODEL_W - (1 - i) * 2;
	}

	private void renderPreview(GuiGraphics g, int x, int y, int w, int h, SkinLibrary.@Nullable Entry entry, float a) {
		Minecraft mc = Minecraft.getInstance();
		SkinLibrary.Texture tex = entry == null ? null : skins.library.texture(entry);
		PlayerModelType model = entry == null ? PlayerModelType.WIDE : skins.library.model(entry);
		if (mc.player != null && a > 0.6f) {
			if (!draggingPreview && Util.getMillis() - lastDrag > 2500) previewYaw += 0.25f;
			McUI.floorRing(g, x + w / 2f, y + h - 47, w * 0.42f, a);
			PlayerPreview.render(g, x + 6, y + 6, w - 12, h - 22, previewYaw,
				tex == null ? null : SkinChanger.preview(mc.player.getSkin(), tex.id(), model));
		} else if (tex != null) {
			// no player outside a world: the skin from the front
			drawSkinFront(g, tex.id(), model, x + (w - 64) / 2f, y + 24, 64, 128, a);
		}
		String label = entry == null ? "No skin yet" : entry.name();
		McUI.titleCenter(g, McUI.trim(label, w - 8), x + w / 2f, y + h - 18, Theme.alpha(McUI.WHITE, a));
		boolean wearing = entry != null && skins.isActive(entry);
		String sub = wearing ? "Your current skin" : mc.player != null ? "" : "Join a world for 3D";
		McUI.textCenter(g, sub, x + w / 2f, y + h - 7, Theme.alpha(wearing ? McUI.GREEN : McUI.DARK_GRAY, a));
	}

	/** The front of a skin, like a paper doll: 16x32 skin pixels scaled into the box. */
	static void drawSkinFront(GuiGraphics g, Identifier tex, PlayerModelType model, float x, float y, float w, float h, float a) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / 16f, h / 32f);
		int color = Theme.alpha(0xFFFFFFFF, a);
		int arm = model == PlayerModelType.SLIM ? 3 : 4;
		for (int layer = 0; layer < 2; layer++) {
			int o = layer * 16; // the outer layer sits 16 pixels lower (or 32 for the head)
			part(g, tex, 4, 0, layer == 0 ? 8 : 40, 8, 8, 8, color);
			part(g, tex, 4, 8, 20, 20 + o, 8, 12, color);
			part(g, tex, 4 - arm, 8, 44, 20 + o, arm, 12, color);
			part(g, tex, 12, 8, layer == 0 ? 36 : 52, 52, arm, 12, color);
			part(g, tex, 4, 20, 4, 20 + o, 4, 12, color);
			part(g, tex, 8, 20, layer == 0 ? 20 : 4, 52, 4, 12, color);
		}
		pose.popMatrix();
	}

	private static void part(GuiGraphics g, Identifier tex, int x, int y, int u, int v, int pw, int ph, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, pw, ph, pw, ph, 64, 64, color);
	}

	private void renderGrid(GuiGraphics g, double mx, double my, float a, boolean interactive) {
		int count = tileCount();
		int rows = (count + columns() - 1) / columns();
		float contentH = rows * (TILE_H + GAP) - GAP;
		float maxScroll = Math.max(0, contentH - gridH());
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll.update(scrollTarget);
		boolean inside = interactive && UI.hover(mx, my, gridX(), gridY(), gridW(), gridH());
		McUI.scissor(g, gridX(), gridY(), gridW(), gridH());
		List<SkinLibrary.Entry> all = skins.library.all();
		for (int i = 0; i < count; i++) {
			float tx = tileX(i), ty = tileY(i);
			if (ty > gridY() + gridH() || ty + TILE_H < gridY()) continue;
			boolean hov = inside && UI.hover(mx, my, tx, ty, TILE_W, TILE_H);
			if (i == 0) renderAddTile(g, tx, ty, hov, mx, my, a);
			else renderSkinTile(g, all.get(i - 1), tx, ty, hov, mx, my, a);
		}
		g.disableScissor();
		McUI.scrollbar(g, x0 + w + 2, gridY(), gridH(), contentH, Math.round(scroll.get()), a);
	}

	private void tileBack(GuiGraphics g, float x, float y, boolean hov, boolean selected, float a) {
		McUI.card(g, x, y, TILE_W, TILE_H, hov, selected, a);
	}

	private void renderAddTile(GuiGraphics g, float x, float y, boolean hov, double mx, double my, float a) {
		tileBack(g, x, y, hov, false, a);
		int color = Theme.alpha(hov ? McUI.WHITE : McUI.GRAY, a);
		float cx = x + TILE_W / 2f, cy = y + TILE_H / 2f;
		if (picking) {
			McUI.textCenter(g, "...", cx, cy, color);
		} else {
			Shapes.rect(g, cx - 1, cy - 8, 2, 16, color);
			Shapes.rect(g, cx - 8, cy - 1, 16, 2, color);
		}
		if (hov) McUI.tooltip(g, "Add Skin", mx, my);
	}

	private void renderSkinTile(GuiGraphics g, SkinLibrary.Entry entry, float x, float y, boolean hov, double mx, double my, float a) {
		boolean isSelected = entry.hash().equals(selected);
		boolean active = skins.isActive(entry);
		tileBack(g, x, y, hov, isSelected, a);
		SkinLibrary.Texture tex = skins.library.texture(entry);
		if (tex != null) drawSkinFront(g, tex.id(), skins.library.model(entry), x + 8, y + 4, 32, 64, a);
		else McUI.textCenter(g, "!", x + TILE_W / 2f, y + TILE_H / 2f, Theme.alpha(McUI.RED, a));
		if (hov) McUI.tooltip(g, active ? entry.name() + " (your current skin)" : entry.name(), mx, my);
		if (active) {
			// the skin you wear right now
			Shapes.rect(g, x + 2, y + 2, 7, 7, Theme.alpha(0xFF000000, a));
			Shapes.rect(g, x + 3, y + 3, 5, 5, Theme.alpha(McUI.GREEN, a));
		} else if (hov) {
			CosmeticsPage.drawRemove(g, x + TILE_W - REMOVE - 2, y + 2, mx, my, a);
		}
	}

	private void renderFooter(GuiGraphics g, SkinLibrary.@Nullable Entry entry, double mx, double my, float a, boolean interactive) {
		float cy = footerY() + McUI.BUTTON_H / 2f;
		float textW = folderX() - gridX() - 6;
		String message = skins.message();
		if (toast != null && Util.getMillis() < toastUntil) {
			McUI.text(g, McUI.trim(toast, textW), gridX(), cy, Theme.alpha(toastError ? McUI.RED : McUI.GREEN, a));
		} else if (message != null) {
			int color = skins.messageIsError() ? McUI.RED : skins.busy() ? McUI.GRAY : McUI.GREEN;
			McUI.text(g, McUI.trim(message, textW), gridX(), cy, Theme.alpha(color, a));
		} else {
			String hint = SkinChanger.online() ? "Drop PNG skins onto the game" : "Changing skins needs a Microsoft account";
			McUI.text(g, McUI.trim(hint, textW), gridX(), cy, Theme.alpha(McUI.DARK_GRAY, a));
		}
		boolean canApply = entry != null && !skins.busy() && !skins.isActive(entry) && SkinChanger.online() && skins.library.texture(entry) != null;
		McUI.primary(g, applyX(), footerY(), APPLY_W, McUI.BUTTON_H, skins.busy() ? "Applying..." : "Apply Skin", interactive && canApply && UI.hover(mx, my, applyX(), footerY(), APPLY_W, McUI.BUTTON_H), canApply, a);
		McUI.button(g, folderX(), footerY(), FOLDER_W, McUI.BUTTON_H, "Folder", interactive && UI.hover(mx, my, folderX(), footerY(), FOLDER_W, McUI.BUTTON_H), true, a);
	}

	// ------------------------------------------------------------------
	// input
	// ------------------------------------------------------------------

	void mouseClicked(double mx, double my) {
		SkinLibrary.Entry entry = selectedEntry();
		for (int i = 0; i < 2; i++) {
			if (!UI.hover(mx, my, modelX(i), y0, MODEL_W, McUI.BUTTON_H)) continue;
			if (entry != null) {
				PlayerModelType model = i == 1 ? PlayerModelType.SLIM : PlayerModelType.WIDE;
				if (model != skins.library.model(entry)) {
					skins.library.setModel(entry, model);
					McUI.click();
				}
			}
			return;
		}
		if (UI.hover(mx, my, x0, y0, PREVIEW_W, h)) {
			draggingPreview = true;
			dragX = mx;
			return;
		}
		if (UI.hover(mx, my, applyX(), footerY(), APPLY_W, McUI.BUTTON_H)) {
			if (entry != null && !skins.isActive(entry)) {
				McUI.click();
				skins.apply(entry);
			}
			return;
		}
		if (UI.hover(mx, my, folderX(), footerY(), FOLDER_W, McUI.BUTTON_H)) {
			McUI.click();
			Util.getPlatform().openPath(skins.library.dir());
			return;
		}
		if (!UI.hover(mx, my, gridX(), gridY(), gridW(), gridH())) return;
		List<SkinLibrary.Entry> all = skins.library.all();
		for (int i = 0; i < tileCount(); i++) {
			float tx = tileX(i), ty = tileY(i);
			if (!UI.hover(mx, my, tx, ty, TILE_W, TILE_H)) continue;
			McUI.click();
			if (i == 0) {
				pickFile();
				return;
			}
			SkinLibrary.Entry e = all.get(i - 1);
			if (!skins.isActive(e) && UI.hover(mx, my, tx + TILE_W - REMOVE - 2, ty + 2, REMOVE, REMOVE)) {
				skins.library.delete(e);
				if (e.hash().equals(selected)) selected = null;
				toast("Skin removed");
				return;
			}
			selected = e.hash();
			return;
		}
	}

	/** Opens the system file picker on its own thread, so the game keeps running. */
	private void pickFile() {
		if (picking) return;
		picking = true;
		Thread thread = new Thread(() -> {
			String file;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.png"));
				filters.flip();
				file = TinyFileDialogs.tinyfd_openFileDialog("Choose a Minecraft skin", null, filters, "Minecraft skin (PNG)", false);
			} catch (Throwable t) {
				file = null;
			}
			String picked = file;
			Minecraft.getInstance().execute(() -> {
				picking = false;
				if (picked != null) filesDropped(List.of(Path.of(picked)));
			});
		}, "Lumina skin picker");
		thread.setDaemon(true);
		thread.start();
	}

	void mouseDragged(double mx) {
		if (!draggingPreview) return;
		previewYaw += (float) (mx - dragX) * 1.6f;
		dragX = mx;
		lastDrag = Util.getMillis();
	}

	void mouseReleased() {
		if (draggingPreview) lastDrag = Util.getMillis();
		draggingPreview = false;
	}

	void scrolled(double scrollY) {
		scrollTarget -= (float) scrollY * 30;
	}

	void filesDropped(List<Path> files) {
		List<SkinLibrary.Entry> added = skins.library.importFiles(files);
		if (added.isEmpty()) {
			toast("Only PNG files can be skins", true);
			return;
		}
		SkinLibrary.Entry last = added.get(added.size() - 1);
		selected = last.hash();
		if (skins.library.texture(last) == null) {
			String error = skins.library.error(last);
			toast(error != null ? error : "That file is not a skin", true);
			return;
		}
		toast(added.size() == 1 ? "Skin added – press Apply to wear it" : added.size() + " skins added");
	}
}
