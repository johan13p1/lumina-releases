package dev.lumina.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.util.MacOS;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Retina without the Retina cost (macOS). The window keeps its full Retina framebuffer, so menus, the
 * HUD and all text are drawn at the display's real resolution. The world is rendered into a target at
 * window size (points) instead and scaled up before the GUI is drawn on top: as many world pixels as
 * on a non-Retina framebuffer, so the frame rate stays. "Retina Resolution" in the Lumina settings
 * renders the world at full size as well; it switches live.
 *
 * <p>While the world renders, GameRendererMixin swaps the main target for {@link #world()}. 26.2 draws
 * the hand into the world target, so unlike 26.3 there is no separate 3D-hud depth target.
 */
public final class RenderScale {
	private static final Vector4f CLEAR = new Vector4f(0.0F);
	private static @Nullable TextureTarget world;

	private RenderScale() {
	}

	/** Framebuffer pixels per window point: 2 on Retina displays, otherwise 1 (and always 1 outside macOS). */
	public static int density(Window window) {
		if (!MacOS.IS_MAC || window.getScreenWidth() <= 0) return 1;
		return Math.max(1, Math.round(window.getWidth() / (float) window.getScreenWidth()));
	}

	public static RenderTarget world() {
		return world;
	}

	/** Prepares the smaller world target; returns false when the world renders at full size this frame. */
	public static boolean begin(Window window, RenderTarget main, boolean panoramic) {
		int factor = MacOS.IS_MAC && !MacOS.retina() && !panoramic ? density(window) : 1;
		if (factor <= 1) return false;
		int w = Math.max(1, main.width / factor), h = Math.max(1, main.height / factor);
		if (world == null) world = new TextureTarget("Lumina World", w, h, true, GpuFormat.RGBA8_UNORM);
		else if (world.width != w || world.height != h) world.resize(w, h);
		// 26.x uses reversed depth: 0 is far
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(world.getColorTexture(), CLEAR, world.getDepthTexture(), 0.0);
		return true;
	}

	/** Scales the finished world up into the main target, where the GUI is drawn next. */
	public static void upscale(RenderTarget main) {
		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Lumina world upscale", main.getColorTextureView(), Optional.empty())) {
			// a plain copy (no blending) with linear filtering
			pass.setPipeline(RenderPipelines.TRACY_BLIT);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("InSampler", world.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.draw(3, 1, 0, 0);
		}
	}
}
