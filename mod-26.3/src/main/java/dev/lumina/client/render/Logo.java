package dev.lumina.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.lumina.client.Lumina;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** The Lumina mark as textures and as a text glyph (font lumina:logo, char U+E000). */
public final class Logo {
	/** Flat white mark, 128 px, tint it when drawing. */
	public static final Identifier FLAT = Lumina.id("textures/gui/logo.png");
	/** Glass rendering of the mark, 256 px. */
	public static final Identifier GLASS = Lumina.id("textures/gui/logo_glass.png");
	private static final FontDescription FONT = new FontDescription.Resource(Lumina.id("logo"));
	/** The mark's character in that font (U+E000). Built from its code: the character itself is invisible and got lost in an edit once. */
	private static final String GLYPH = String.valueOf((char) 0xE000);
	/** The marks as read from the mod's own files, by their resource name. */
	private static final Map<Identifier, Identifier> PINNED = new HashMap<>();

	private Logo() {
	}

	/** Draws a square logo texture with its top-left corner at (x, y). */
	public static void draw(GuiGraphicsExtractor g, Identifier texture, float x, float y, float size, int color) {
		int tex = texture == GLASS ? 256 : 128;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / tex, size / tex);
		g.blit(RenderPipelines.GUI_TEXTURED, pinned(texture), 0, 0, 0, 0, tex, tex, tex, tex, tex, tex, color);
		pose.popMatrix();
	}

	/**
	 * The mark straight from the mod's own files, kept as a separate texture: no texture pack, not even
	 * one a server sends, can replace it. Render thread only.
	 */
	private static Identifier pinned(Identifier texture) {
		return PINNED.computeIfAbsent(texture, Logo::pin);
	}

	private static Identifier pin(Identifier texture) {
		try (InputStream in = Logo.class.getResourceAsStream("/assets/" + texture.getNamespace() + "/" + texture.getPath())) {
			if (in == null) return texture;
			NativeImage image = NativeImage.read(in);
			Identifier id = Lumina.id("pinned/" + texture.getPath());
			// smooth and clamped, like the texture's own .mcmeta asks for
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image) {
				{
					this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
			});
			return id;
		} catch (IOException e) {
			Lumina.LOG.warn("Could not read {} from the mod files", texture, e);
			return texture;
		}
	}

	/** Prefixes a player name with the Lumina mark, e.g. for the tab list and name tags. */
	public static MutableComponent withLogo(Component name) {
		MutableComponent mark = Component.literal(GLYPH).withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF).withItalic(false).withBold(false));
		return Component.empty().append(mark).append(Component.literal(" ")).append(name);
	}
}
