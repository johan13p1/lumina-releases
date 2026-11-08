package dev.lumina.client.gui;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.cosmetics.CapeLibrary;
import dev.lumina.client.feature.cosmetics.CapeTexture;
import dev.lumina.client.feature.cosmetics.CosmeticsModule;
import dev.lumina.client.feature.cosmetics.ParticleEffects;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

/** The Cosmetics page of the Lumina menu: pick a cape and a particle effect, with a live preview. */
final class CosmeticsPage {
	private static final int PREVIEW_W = 112;
	/** Capes are 10x16, so this tile shows them at exactly four times that. */
	private static final int CAPE_W = 48, CAPE_H = 72;
	private static final int FX_W = 72, FX_H = 56;
	private static final int GAP = 6;
	private static final int TOP_H = 26;
	private static final int FOOTER_H = 26;
	private static final int TAB_W = 56;
	private static final int SETTINGS_W = 56;
	private static final int FOLDER_W = 76;
	private static final int REMOVE = 12;

	private final CosmeticsModule module;
	private final Consumer<CosmeticsModule> openSettings;
	private boolean effectsTab;
	private final Anim scroll = new Anim(0, 20);
	private float scrollTarget;
	private float previewYaw = 200;
	private boolean draggingPreview;
	private double dragX;
	private long lastDrag;
	private String message;
	private long messageUntil;

	// layout of the current frame, in menu units
	private int x0, y0, w, h;

	CosmeticsPage(CosmeticsModule module, Consumer<CosmeticsModule> openSettings) {
		this.module = module;
		this.openSettings = openSettings;
		// opens on the tab you used last
		this.effectsTab = Lumina.config().uiString("cosmetics_tab", "capes").equals("effects");
	}

	private void toast(String text) {
		message = text;
		messageUntil = Util.getMillis() + 3500;
	}

	// ------------------------------------------------------------------
	// layout helpers
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

	private int tileW() {
		return effectsTab ? FX_W : CAPE_W;
	}

	private int tileH() {
		return effectsTab ? FX_H : CAPE_H;
	}

	private int columns() {
		return Math.max(1, (gridW() + GAP) / (tileW() + GAP));
	}

	private int tileCount() {
		return effectsTab ? ParticleEffects.Effect.values().length + 1 : module.capes.all().size() + 1;
	}

	private float tileX(int i) {
		return gridX() + (i % columns()) * (tileW() + GAP);
	}

	private float tileY(int i) {
		return gridY() + (i / columns()) * (tileH() + GAP) - Math.round(scroll.get());
	}

	private int folderX() {
		return gridX() + gridW() - FOLDER_W;
	}

	private int settingsX() {
		return x0 + w - SETTINGS_W;
	}

	private int footerY() {
		return y0 + h - McUI.BUTTON_H;
	}

	// ------------------------------------------------------------------
	// rendering
	// ------------------------------------------------------------------

	void render(GuiGraphicsExtractor g, int x, int y, int width, int height, double mx, double my, float a, boolean interactive) {
		this.x0 = x;
		this.y0 = y;
		this.w = width;
		this.h = height;
		renderTop(g, mx, my, a, interactive);
		renderPreview(g, x, y, PREVIEW_W, height, a);
		renderGrid(g, mx, my, a, interactive);
		renderFooter(g, mx, my, a, interactive);
	}

	private void renderTop(GuiGraphicsExtractor g, double mx, double my, float a, boolean interactive) {
		String[] labels = {"Capes", "Effects"};
		for (int i = 0; i < 2; i++) {
			int tx = gridX() + i * (TAB_W + 2);
			boolean active = (i == 1) == effectsTab;
			McUI.segment(g, tx, y0, TAB_W, McUI.BUTTON_H, labels[i], active, interactive && UI.hover(mx, my, tx, y0, TAB_W, McUI.BUTTON_H), a);
		}
		McUI.button(g, settingsX(), y0, SETTINGS_W, McUI.BUTTON_H, "Settings", interactive && UI.hover(mx, my, settingsX(), y0, SETTINGS_W, McUI.BUTTON_H), true, a);
	}

	private void renderPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float a) {
		Minecraft mc = Minecraft.getInstance();
		String label;
		CapeLibrary.Entry cape = module.equippedCape();
		ParticleEffects.Effect effect = module.particles.effect();
		if (effectsTab) label = effect == null ? "No effect" : effect.title;
		else label = cape == null ? "No cape" : cape.name();
		if (mc.player != null && a > 0.6f) {
			if (!draggingPreview && Util.getMillis() - lastDrag > 2500) previewYaw += 0.25f;
			McUI.floorRing(g, x + w / 2f, y + h - 47, w * 0.42f, a);
			// seen from behind by default so the cape shows
			PlayerPreview.render(g, x + 6, y + 6, w - 12, h - 22, previewYaw, null);
		} else if (cape != null) {
			// no player outside a world: show the cape itself
			CapeTexture tex = module.capes.texture(cape);
			if (tex != null) drawCapeFace(g, tex, x + (w - 60) / 2f, y + 24, 60, 96, a);
		}
		McUI.titleCenter(g, McUI.trim(label, w - 8), x + w / 2f, y + h - 18, Theme.alpha(McUI.WHITE, a));
		if (mc.player == null) McUI.textCenter(g, "Join a world for 3D", x + w / 2f, y + h - 7, Theme.alpha(McUI.DARK_GRAY, a));
	}

	private void drawCapeFace(GuiGraphicsExtractor g, CapeTexture tex, float x, float y, float w, float h, float a) {
		int frame = tex.currentFrame();
		float s = tex.scale();
		int u = Math.round(tex.frameX(frame) + s), v = Math.round(tex.frameY(frame) + s);
		int rw = Math.max(1, Math.round(10 * s)), rh = Math.max(1, Math.round(16 * s));
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / rw, h / rh);
		g.blit(RenderPipelines.GUI_TEXTURED, tex.atlas, 0, 0, u, v, rw, rh, rw, rh, tex.atlasW, tex.atlasH, Theme.alpha(0xFFFFFFFF, a));
		pose.popMatrix();
	}

	private void renderGrid(GuiGraphicsExtractor g, double mx, double my, float a, boolean interactive) {
		int count = tileCount();
		int rows = (count + columns() - 1) / columns();
		float contentH = rows * (tileH() + GAP) - GAP;
		float maxScroll = Math.max(0, contentH - gridH());
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll.update(scrollTarget);
		boolean inside = interactive && UI.hover(mx, my, gridX(), gridY(), gridW(), gridH());
		McUI.scissor(g, gridX(), gridY(), gridW(), gridH());
		for (int i = 0; i < count; i++) {
			float tx = tileX(i), ty = tileY(i);
			if (ty > gridY() + gridH() || ty + tileH() < gridY()) continue;
			boolean hov = inside && UI.hover(mx, my, tx, ty, tileW(), tileH());
			if (effectsTab) renderEffectTile(g, i, tx, ty, hov, a);
			else renderCapeTile(g, i, tx, ty, hov, mx, my, a);
		}
		g.disableScissor();
		McUI.scrollbar(g, x0 + w + 2, gridY(), gridH(), contentH, Math.round(scroll.get()), a);
	}

	private void tileBack(GuiGraphicsExtractor g, float x, float y, float w, float h, boolean hov, boolean selected, float a) {
		McUI.card(g, x, y, w, h, hov, selected, a);
	}

	private void renderCapeTile(GuiGraphicsExtractor g, int i, float x, float y, boolean hov, double mx, double my, float a) {
		List<CapeLibrary.Entry> capes = module.capes.all();
		CapeLibrary.Entry entry = i == 0 ? null : capes.get(i - 1);
		String id = entry == null ? "" : entry.id();
		boolean selected = module.cape.get().equals(id) || (entry != null && module.equippedCape() == entry);
		if (entry == null) selected = module.cape.get().isEmpty();
		tileBack(g, x, y, CAPE_W, CAPE_H, hov, selected, a);
		if (entry == null) {
			McUI.textCenter(g, "None", x + CAPE_W / 2f, y + CAPE_H / 2f, Theme.alpha(McUI.GRAY, a));
		} else {
			CapeTexture tex = module.capes.texture(entry);
			if (tex != null) {
				drawCapeFace(g, tex, x + 4, y + 4, 40, 64, a);
			} else if (module.capes.failed(entry)) {
				McUI.textCenter(g, "!", x + CAPE_W / 2f, y + CAPE_H / 2f, Theme.alpha(McUI.RED, a));
			} else {
				float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 180.0);
				Shapes.rect(g, x + 4, y + 4, 40, 64, Theme.alpha(Theme.mix(0x0AFFFFFF, 0x1AFFFFFF, pulse), a));
			}
			if (hov) McUI.tooltip(g, entry.name(), mx, my);
			// your own capes can be removed again
			if (!entry.bundled() && hov) drawRemove(g, x + CAPE_W - REMOVE - 2, y + 2, mx, my, a);
		}
	}

	/** Small red cross in the corner of a tile. */
	static void drawRemove(GuiGraphicsExtractor g, float x, float y, double mx, double my, float a) {
		boolean hov = UI.hover(mx, my, x, y, REMOVE, REMOVE);
		McUI.frame(g, x, y, REMOVE, REMOVE, hov ? 0xFFE5484D : 0xFF29292C, 0x24EDEDED, a);
		UI.icon(g, dev.lumina.client.render.Icons.X, x + REMOVE / 2f, y + REMOVE / 2f, 8, Theme.alpha(McUI.WHITE, a));
	}

	private void renderEffectTile(GuiGraphicsExtractor g, int i, float x, float y, boolean hov, float a) {
		ParticleEffects.Effect effect = i == 0 ? null : ParticleEffects.Effect.values()[i - 1];
		boolean selected = effect == null ? module.particles.effect() == null : module.particles.effect() == effect;
		tileBack(g, x, y, FX_W, FX_H, hov, selected, a);
		float cx = x + FX_W / 2f, cy = y + 23;
		if (effect != null) {
			module.particles.ensureAtlasForGui();
			float[] uv = ParticleEffects.uv(effect.icon);
			int color = effect.tinted ? module.effectColor.color() | 0xFF000000 : effect.iconColor;
			float size = 26;
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(cx - size / 2f, cy - size / 2f);
			int cell = ParticleEffects.CELL, n = ParticleEffects.Sprite.values().length;
			pose.scale(size / cell, size / cell);
			g.blit(RenderPipelines.GUI_TEXTURED, ParticleEffects.ATLAS, 0, 0, uv[0] * cell * n, 0, cell, cell, cell, cell, cell * n, cell, Theme.alpha(color, a));
			pose.popMatrix();
		}
		String name = effect == null ? "None" : effect.title;
		McUI.textCenter(g, McUI.trim(name, FX_W - 4), cx, effect == null ? y + FX_H / 2f : y + FX_H - 9, Theme.alpha(selected || hov ? McUI.WHITE : McUI.GRAY, a));
	}

	private void renderFooter(GuiGraphicsExtractor g, double mx, double my, float a, boolean interactive) {
		float cy = footerY() + McUI.BUTTON_H / 2f;
		float textW = gridW() - (effectsTab ? 0 : FOLDER_W + 6);
		if (message != null && Util.getMillis() < messageUntil) {
			McUI.text(g, McUI.trim(message, textW), gridX(), cy, Theme.alpha(McUI.GREEN, a));
		} else {
			String hint = effectsTab ? "Color and amount are in the settings" : "Drop PNG or GIF capes onto the game";
			McUI.text(g, McUI.trim(hint, textW), gridX(), cy, Theme.alpha(McUI.DARK_GRAY, a));
		}
		if (!effectsTab) {
			McUI.button(g, folderX(), footerY(), FOLDER_W, McUI.BUTTON_H, "Open Folder", interactive && UI.hover(mx, my, folderX(), footerY(), FOLDER_W, McUI.BUTTON_H), true, a);
		}
	}

	// ------------------------------------------------------------------
	// input
	// ------------------------------------------------------------------

	void mouseClicked(double mx, double my) {
		for (int i = 0; i < 2; i++) {
			if (!UI.hover(mx, my, gridX() + i * (TAB_W + 2), y0, TAB_W, McUI.BUTTON_H)) continue;
			boolean effects = i == 1;
			McUI.click();
			if (effects != effectsTab) {
				effectsTab = effects;
				Lumina.config().ui.addProperty("cosmetics_tab", effects ? "effects" : "capes");
				scrollTarget = 0;
				scroll.snap(0);
			}
			return;
		}
		if (UI.hover(mx, my, settingsX(), y0, SETTINGS_W, McUI.BUTTON_H)) {
			McUI.click();
			openSettings.accept(module);
			return;
		}
		if (UI.hover(mx, my, x0, y0, PREVIEW_W, h)) {
			draggingPreview = true;
			dragX = mx;
			return;
		}
		if (!effectsTab && UI.hover(mx, my, folderX(), footerY(), FOLDER_W, McUI.BUTTON_H)) {
			McUI.click();
			Util.getPlatform().openPath(module.capes.userDir());
			return;
		}
		if (!UI.hover(mx, my, gridX(), gridY(), gridW(), gridH())) return;
		for (int i = 0; i < tileCount(); i++) {
			float tx = tileX(i), ty = tileY(i);
			if (!UI.hover(mx, my, tx, ty, tileW(), tileH())) continue;
			McUI.click();
			if (effectsTab) {
				module.effect.set(i == 0 ? "" : ParticleEffects.Effect.values()[i - 1].id());
			} else {
				CapeLibrary.Entry entry = i == 0 ? null : module.capes.all().get(i - 1);
				if (entry != null && !entry.bundled() && UI.hover(mx, my, tx + CAPE_W - REMOVE - 2, ty + 2, REMOVE, REMOVE)) {
					if (module.cape.get().equals(entry.id())) module.cape.set("");
					module.capes.delete(entry);
					toast("Cape removed");
					return;
				}
				module.cape.set(entry == null ? "" : entry.id());
			}
			if (!module.enabled()) module.setEnabled(true);
			return;
		}
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
		int added = module.capes.importFiles(files);
		effectsTab = false;
		if (added == 0) {
			toast("Only PNG and GIF files can be capes");
			return;
		}
		// wear the newest one right away
		String last = files.get(files.size() - 1).getFileName().toString();
		for (CapeLibrary.Entry e : module.capes.all()) {
			if (!e.bundled() && e.id().equals("user:" + last)) module.cape.set(e.id());
		}
		toast(added == 1 ? "Cape added" : added + " capes added");
	}

	static CosmeticsModule module() {
		return Lumina.modules().get(CosmeticsModule.class);
	}
}
