package dev.lumina.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.Lumina;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Small anti-aliased shapes rasterized once with Java2D (e.g. the minimap arrow). */
public final class Sprites {
	private static final Map<String, Identifier> CACHE = new HashMap<>();
	public static final int SIZE = 64;

	private Sprites() {
	}

	public static Identifier arrow() {
		return get("arrow", g -> {
			Path2D p = new Path2D.Float();
			p.moveTo(32, 4);
			p.lineTo(56, 58);
			p.lineTo(32, 46);
			p.lineTo(8, 58);
			p.closePath();
			g.setColor(new Color(0, 0, 0, 120));
			g.setStroke(new java.awt.BasicStroke(6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
			g.draw(p);
			g.setColor(Color.WHITE);
			g.fill(p);
		});
	}

	/** White, fully opaque on the left fading to transparent on the right; tint it when drawing. */
	public static Identifier fadeRight() {
		return get("fade_right", g -> {
			g.setPaint(new java.awt.GradientPaint(0, 0, new Color(255, 255, 255, 255), SIZE, 0, new Color(255, 255, 255, 0)));
			g.fillRect(0, 0, SIZE, SIZE);
		});
	}

	/** White, transparent at the top fading to opaque at the bottom. */
	public static Identifier fadeDown() {
		return get("fade_down", g -> {
			g.setPaint(new java.awt.GradientPaint(0, 0, new Color(255, 255, 255, 0), 0, SIZE, new Color(255, 255, 255, 255)));
			g.fillRect(0, 0, SIZE, SIZE);
		});
	}

	public static Identifier get(String name, Consumer<Graphics2D> painter) {
		return CACHE.computeIfAbsent(name, n -> {
			BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			painter.accept(g);
			g.dispose();
			NativeImage out = new NativeImage(SIZE, SIZE, true);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) out.setPixel(x, y, img.getRGB(x, y));
			}
			Identifier id = Lumina.id("sprite/" + n);
			register(id, out);
			return id;
		});
	}

	/** Registers a smoothly scaled texture; a texture already under this name is replaced and freed. */
	public static void register(Identifier id, NativeImage image) {
		DynamicTexture tex = new DynamicTexture(() -> id.toString(), image) {
			{
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		};
		Minecraft.getInstance().getTextureManager().register(id, tex);
	}
}
