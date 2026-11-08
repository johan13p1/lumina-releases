package dev.lumina.client.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.lumina.client.Lumina;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

/**
 * Renders a TrueType font through Java2D glyph rasterization into texture atlases. Glyphs are
 * rasterized at the exact physical pixel size they end up on screen, so text is sharp at every
 * GUI scale. Falls back to the vanilla font if Java2D is unavailable.
 */
public final class TtfFont {
	private static final int PAGE_SIZE = 512;
	private static final int MAX_CACHES = 48;
	private static int pageCounter;

	private final String name;
	private final Font base;
	private final Font fallback;
	private final Map<Integer, GlyphCache> caches = new LinkedHashMap<>(16, 0.75f, true);

	private TtfFont(String name, Font base) {
		this.name = name;
		this.base = base;
		this.fallback = base == null ? null : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	}

	public static TtfFont load(String name, String resource) {
		try (InputStream in = TtfFont.class.getResourceAsStream("/assets/lumina/font/" + resource)) {
			if (in == null) throw new IllegalStateException("missing font " + resource);
			return new TtfFont(name, Font.createFont(Font.TRUETYPE_FONT, in));
		} catch (Throwable t) {
			Lumina.LOG.error("Could not load font {}, falling back to the vanilla font", resource, t);
			return new TtfFont(name, null);
		}
	}

	public boolean isVanillaFallback() {
		return base == null;
	}

	private static float guiScale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	private GlyphCache cache(int px) {
		GlyphCache cache = caches.get(px);
		if (cache == null) {
			cache = new GlyphCache(px);
			caches.put(px, cache);
		}
		return cache;
	}

	/**
	 * Drops the least recently used sizes. Called between frames only, because GUI draws are deferred
	 * until the end of a frame and must not reference already closed atlas textures.
	 */
	public void trim() {
		while (caches.size() > MAX_CACHES) {
			Integer eldest = caches.keySet().iterator().next();
			caches.remove(eldest).close();
		}
		for (GlyphCache c : caches.values()) {
			if (c.linesFull) c.clearLines();
		}
	}

	/**
	 * Rasterizes the printable ASCII range at {@code size} for the current GUI scale up front, so a
	 * screen doesn't stutter on its first frames while glyphs are created.
	 */
	public void prewarm(float... sizes) {
		if (base == null) return;
		for (float size : sizes) {
			GlyphCache c = metricsCache(size);
			for (int cp = 32; cp < 127; cp++) {
				for (int sub = 0; sub < GlyphCache.SUBPIXELS; sub++) c.glyph(cp, sub);
			}
			c.flush();
		}
	}

	private GlyphCache metricsCache(float size) {
		return cache(Math.max(1, Math.round(size * guiScale())));
	}

	// ------------------------------------------------------------------
	// Metrics (GUI units)
	// ------------------------------------------------------------------

	public float width(String text, float size) {
		if (text == null || text.isEmpty()) return 0;
		if (base == null) return Minecraft.getInstance().font.width(text) * size / 9f;
		GlyphCache c = metricsCache(size);
		float w = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			w += c.glyph(cp).advance;
			i += Character.charCount(cp);
		}
		return w / c.px * size;
	}

	public float width(FormattedCharSequence seq, float size, TtfFont bold) {
		if (base == null) return Minecraft.getInstance().font.width(seq) * size / 9f;
		GlyphCache regular = metricsCache(size);
		GlyphCache boldCache = bold.base == null ? regular : bold.metricsCache(size);
		float[] w = {0};
		seq.accept((index, style, cp) -> {
			GlyphCache c = style.isBold() ? boldCache : regular;
			w[0] += c.glyph(cp).advance / c.px * size;
			return true;
		});
		return w[0];
	}

	/** Height of capital letters, useful for optically centering text. */
	public float capHeight(float size) {
		if (base == null) return 7f * size / 9f;
		GlyphCache c = metricsCache(size);
		return c.capHeight / c.px * size;
	}

	public float ascent(float size) {
		if (base == null) return 7f * size / 9f;
		GlyphCache c = metricsCache(size);
		return c.ascent / c.px * size;
	}

	public float lineHeight(float size) {
		if (base == null) return size;
		GlyphCache c = metricsCache(size);
		return (c.ascent + c.descent) / c.px * size;
	}

	private final Map<String, String> trimmed = new java.util.HashMap<>();

	/** Cuts {@code text} to {@code maxWidth} with an ellipsis; results are memoized (menus call this every frame). */
	public String trimToWidth(String text, float size, float maxWidth) {
		String key = Math.round(size * guiScale() * 100) + ":" + Math.round(maxWidth * 100) + ":" + text;
		String hit = trimmed.get(key);
		if (hit != null) return hit;
		if (trimmed.size() > 4000) trimmed.clear();
		String result = trimUncached(text, size, maxWidth);
		trimmed.put(key, result);
		return result;
	}

	private String trimUncached(String text, float size, float maxWidth) {
		if (width(text, size) <= maxWidth) return text;
		String ellipsis = "…";
		float ew = width(ellipsis, size);
		StringBuilder sb = new StringBuilder();
		float w = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			float cw = width(ch, size);
			if (w + cw + ew > maxWidth) break;
			sb.append(ch);
			w += cw;
			i += Character.charCount(cp);
		}
		return sb.append(ellipsis).toString();
	}

	// ------------------------------------------------------------------
	// Drawing
	// ------------------------------------------------------------------

	/** Draws text with its top-left corner at (x, y), where y is the top of the line box. */
	public void draw(GuiGraphicsExtractor g, String text, float x, float y, float size, int color) {
		if (text == null || text.isEmpty() || ARGB.alpha(color) == 0) return;
		if (base == null) {
			drawVanilla(g, text, x, y, size, color);
			return;
		}
		Matrix3x2fStack pose = g.pose();
		float gs = guiScale();
		float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
		float eff = gs * poseScale;
		int px = Math.max(1, Math.round(size * eff));
		GlyphCache c = cache(px);

		pose.pushMatrix();
		pose.translate(x, y);
		snap(pose, gs, eff);
		pose.scale(size * eff / px / eff);
		float pen = 0;
		int baseline = Math.round(c.ascent);
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			pen = drawGlyph(g, c, cp, cp, pen, baseline, color);
		}
		pose.popMatrix();
		c.flush();
	}

	/**
	 * Like {@link #draw(GuiGraphicsExtractor, String, float, float, float, int)}, but the whole line is
	 * rasterized once into a single texture region and drawn with one blit. Much cheaper for UI text
	 * that is drawn every frame; meant for labels, not for values that change constantly.
	 */
	public void drawLine(GuiGraphicsExtractor g, String text, float x, float y, float size, int color) {
		if (text == null || text.isEmpty() || ARGB.alpha(color) == 0) return;
		if (base == null) {
			drawVanilla(g, text, x, y, size, color);
			return;
		}
		Matrix3x2fStack pose = g.pose();
		float gs = guiScale();
		float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
		float eff = gs * poseScale;
		int px = Math.max(1, Math.round(size * eff));
		GlyphCache c = cache(px);
		Glyph line = c.line(text);
		if (line == null) {
			draw(g, text, x, y, size, color);
			return;
		}
		pose.pushMatrix();
		pose.translate(x, y);
		snap(pose, gs, eff);
		pose.scale(size * eff / px / eff);
		if (line.page != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, line.page.id, line.offX, Math.round(c.ascent) + line.offY,
				line.u, line.v, line.w, line.h, PAGE_SIZE, PAGE_SIZE, color);
		}
		pose.popMatrix();
		c.flush();
	}

	/**
	 * Draws one glyph at the (fractional) pen position using a pre-shifted sub-pixel variant, so
	 * spacing stays even while every texel still lands exactly on a screen pixel.
	 */
	private static float drawGlyph(GuiGraphicsExtractor g, GlyphCache c, int cp, int metricsCp, float pen, int baseline, int color) {
		int whole = (int) Math.floor(pen);
		int sub = Math.round((pen - whole) * GlyphCache.SUBPIXELS);
		if (sub == GlyphCache.SUBPIXELS) {
			whole++;
			sub = 0;
		}
		Glyph glyph = c.glyph(cp, sub);
		if (glyph.page != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, glyph.page.id, whole + glyph.offX, baseline + glyph.offY,
				glyph.u, glyph.v, glyph.w, glyph.h, PAGE_SIZE, PAGE_SIZE, color);
		}
		return pen + c.glyph(metricsCp).advance;
	}

	/** Draws Minecraft formatted text (colors, bold, ...). */
	public void draw(GuiGraphicsExtractor g, FormattedCharSequence seq, float x, float y, float size, int defaultColor, TtfFont bold) {
		if (base == null) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, y);
			pose.scale(size / 9f);
			g.text(Minecraft.getInstance().font, seq, 0, 0, defaultColor, false);
			pose.popMatrix();
			return;
		}
		Matrix3x2fStack pose = g.pose();
		float gs = guiScale();
		float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
		float eff = gs * poseScale;
		int px = Math.max(1, Math.round(size * eff));
		GlyphCache regular = cache(px);
		GlyphCache boldCache = bold.base == null ? regular : bold.cache(px);
		int alpha = ARGB.alpha(defaultColor);

		pose.pushMatrix();
		pose.translate(x, y);
		snap(pose, gs, eff);
		pose.scale(size * eff / px / eff);
		float[] pen = {0};
		int baseline = Math.round(regular.ascent);
		seq.accept((index, style, cp) -> {
			GlyphCache c = style.isBold() ? boldCache : regular;
			int color = styleColor(style, defaultColor, alpha);
			int drawCp = style.isObfuscated() && cp != ' ' ? 'a' + (int) (Math.random() * 26) : cp;
			float adv = c.glyph(cp).advance;
			drawGlyph(g, c, drawCp, cp, pen[0], baseline, color);
			if (style.isStrikethrough()) {
				g.fill(Math.round(pen[0]), baseline - Math.round(c.capHeight / 2), Math.round(pen[0] + adv), baseline - Math.round(c.capHeight / 2) + Math.max(1, px / 12), color);
			}
			if (style.isUnderlined()) {
				g.fill(Math.round(pen[0]), baseline + 1, Math.round(pen[0] + adv), baseline + 1 + Math.max(1, px / 12), color);
			}
			pen[0] += adv;
			return true;
		});
		pose.popMatrix();
		regular.flush();
		if (boldCache != regular) boldCache.flush();
	}

	private static int styleColor(Style style, int defaultColor, int alpha) {
		TextColor tc = style.getColor();
		if (tc == null) return defaultColor;
		return ARGB.color(alpha, tc.getValue());
	}

	/** Moves the current origin onto the physical pixel grid so glyph texels map 1:1 to screen pixels. */
	private static void snap(Matrix3x2fStack pose, float gs, float eff) {
		float physX = pose.m20() * gs;
		float physY = pose.m21() * gs;
		pose.translate((Math.round(physX) - physX) / eff, (Math.round(physY) - physY) / eff);
	}

	private static void drawVanilla(GuiGraphicsExtractor g, String text, float x, float y, float size, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 9f);
		g.text(Minecraft.getInstance().font, text, 0, 0, color, false);
		pose.popMatrix();
	}

	public void clearCaches() {
		caches.values().forEach(GlyphCache::close);
		caches.clear();
	}

	// ------------------------------------------------------------------
	// Glyph cache per pixel size
	// ------------------------------------------------------------------

	private record Glyph(Page page, int u, int v, int w, int h, int offX, int offY, float advance) {
	}

	private static final class Page {
		final Identifier id;
		final NativeImage image;
		final DynamicTexture texture;
		int cursorX = 1, cursorY = 1, rowHeight;
		boolean dirty;

		Page(String fontName, int px) {
			this.id = Lumina.id("font/" + fontName + "_" + px + "_" + (pageCounter++));
			this.image = new NativeImage(PAGE_SIZE, PAGE_SIZE, true);
			this.texture = new LinearTexture(() -> id.toString(), image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
		}

		boolean fits(int w, int h) {
			if (cursorX + w + 1 > PAGE_SIZE) {
				cursorX = 1;
				cursorY += rowHeight + 1;
				rowHeight = 0;
			}
			return cursorY + h + 1 <= PAGE_SIZE && w + 2 <= PAGE_SIZE;
		}

		void close() {
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}

	private static final class LinearTexture extends DynamicTexture {
		LinearTexture(java.util.function.Supplier<String> label, NativeImage image) {
			super(label, image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	private final class GlyphCache {
		static final int SUBPIXELS = 3;
		final int px;
		final Font font;
		final Font fallbackFont;
		final FontRenderContext frc = new FontRenderContext(null, true, true);
		final Int2ObjectOpenHashMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
		final List<Page> pages = new ArrayList<>();
		final float ascent;
		final float descent;
		final float capHeight;

		GlyphCache(int px) {
			this.px = px;
			this.font = base.deriveFont((float) px);
			this.fallbackFont = fallback.deriveFont((float) px);
			var lm = font.getLineMetrics("Hg", frc);
			this.ascent = lm.getAscent();
			this.descent = lm.getDescent();
			Rectangle h = font.createGlyphVector(frc, "H").getPixelBounds(frc, 0, 0);
			this.capHeight = h.height > 0 ? h.height : px * 0.7f;
		}

		Glyph glyph(int cp) {
			return glyph(cp, 0);
		}

		Glyph glyph(int cp, int sub) {
			int key = cp * SUBPIXELS + sub;
			Glyph glyph = glyphs.get(key);
			if (glyph == null) {
				glyph = rasterize(cp, sub / (float) SUBPIXELS);
				glyphs.put(key, glyph);
			}
			return glyph;
		}

		private Glyph rasterize(int cp, float shift) {
			Font f = font.canDisplay(cp) ? font : fallbackFont;
			GlyphVector gv = f.createGlyphVector(frc, new String(Character.toChars(cp)));
			float advance = (float) gv.getGlyphMetrics(0).getAdvanceX();
			Rectangle b = gv.getPixelBounds(frc, shift, 0);
			if (b.width <= 0 || b.height <= 0 || Character.isWhitespace(cp)) {
				return new Glyph(null, 0, 0, 0, 0, 0, 0, advance);
			}
			int pad = 1;
			int w = b.width + pad * 2;
			int h = b.height + pad * 2;
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = img.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2.setColor(Color.WHITE);
			g2.drawGlyphVector(gv, -b.x + pad + shift, -b.y + pad);
			g2.dispose();

			Page page = pages.isEmpty() ? null : pages.get(pages.size() - 1);
			if (page == null || !page.fits(w, h)) {
				page = new Page(name, px);
				pages.add(page);
				if (!page.fits(w, h)) return new Glyph(null, 0, 0, 0, 0, 0, 0, advance);
			}
			int u = page.cursorX, v = page.cursorY;
			for (int yy = 0; yy < h; yy++) {
				for (int xx = 0; xx < w; xx++) {
					int a = img.getRGB(xx, yy) >>> 24;
					page.image.setPixel(u + xx, v + yy, (a << 24) | 0xFFFFFF);
				}
			}
			page.cursorX += w + 1;
			page.rowHeight = Math.max(page.rowHeight, h);
			page.dirty = true;
			return new Glyph(page, u, v, w, h, b.x - pad, b.y - pad, advance);
		}

		// whole lines rasterized at once (see drawLine), in their own pages
		static final int MAX_LINES = 600;
		static final int MAX_LINE_PAGES = 4;
		final Map<String, Glyph> lines = new java.util.HashMap<>();
		final List<Page> linePages = new ArrayList<>();
		/** Set when the line pages are used up; {@link TtfFont#trim()} clears them between frames. */
		boolean linesFull;

		/** The rasterized line, or null when it can't be cached right now (then draw glyph by glyph). */
		Glyph line(String text) {
			Glyph line = lines.get(text);
			if (line != null || linesFull) return line;
			for (int i = 0; i < text.length(); ) {
				int cp = text.codePointAt(i);
				if (!font.canDisplay(cp)) return null;
				i += Character.charCount(cp);
			}
			if (lines.size() >= MAX_LINES) {
				linesFull = true;
				return null;
			}
			GlyphVector gv = font.createGlyphVector(frc, text);
			float advance = (float) gv.getGlyphPosition(gv.getNumGlyphs()).getX();
			Rectangle b = gv.getPixelBounds(frc, 0, 0);
			if (b.width <= 0 || b.height <= 0) {
				line = new Glyph(null, 0, 0, 0, 0, 0, 0, advance);
				lines.put(text, line);
				return line;
			}
			int pad = 1;
			int w = b.width + pad * 2, h = b.height + pad * 2;
			if (w + 2 > PAGE_SIZE || h + 2 > PAGE_SIZE) return null;
			Page page = linePages.isEmpty() ? null : linePages.get(linePages.size() - 1);
			if (page == null || !page.fits(w, h)) {
				if (linePages.size() >= MAX_LINE_PAGES) {
					linesFull = true;
					return null;
				}
				page = new Page(name + "_lines", px);
				linePages.add(page);
				page.fits(w, h);
			}
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = img.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2.setColor(Color.WHITE);
			g2.drawGlyphVector(gv, -b.x + pad, -b.y + pad);
			g2.dispose();
			int u = page.cursorX, v = page.cursorY;
			for (int yy = 0; yy < h; yy++) {
				for (int xx = 0; xx < w; xx++) {
					int a = img.getRGB(xx, yy) >>> 24;
					page.image.setPixel(u + xx, v + yy, (a << 24) | 0xFFFFFF);
				}
			}
			page.cursorX += w + 1;
			page.rowHeight = Math.max(page.rowHeight, h);
			page.dirty = true;
			line = new Glyph(page, u, v, w, h, b.x - pad, b.y - pad, advance);
			lines.put(text, line);
			return line;
		}

		void clearLines() {
			linePages.forEach(Page::close);
			linePages.clear();
			lines.clear();
			linesFull = false;
		}

		void flush() {
			for (Page page : pages) {
				if (page.dirty) {
					page.texture.upload();
					page.dirty = false;
				}
			}
			for (Page page : linePages) {
				if (page.dirty) {
					page.texture.upload();
					page.dirty = false;
				}
			}
		}

		void close() {
			pages.forEach(Page::close);
			pages.clear();
			glyphs.clear();
			clearLines();
		}
	}
}
