package dev.lumina.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.util.MacOS;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.jspecify.annotations.Nullable;

/**
 * Retina without the Retina cost (macOS). The window keeps its full Retina framebuffer, so menus, the
 * HUD and all text are drawn at the display's real resolution. The world is rendered into a target
 * at window size (points) instead and scaled up before the GUI is drawn on top: as many world pixels
 * as on a non-Retina framebuffer, so the frame rate stays. "Retina Resolution" in the Lumina settings
 * renders the world at full size as well; it switches live.
 *
 * <p>While the world renders, {@link #active()} is true: the main render target and the window size
 * then report the smaller world target (see MinecraftMixin and WindowMixin).
 */
public final class RenderScale {
	private static @Nullable TextureTarget target;
	private static boolean active;

	private RenderScale() {
	}

	/**
	 * Framebuffer pixels per window point: 2 on Retina displays, otherwise 1 (and always 1 outside
	 * macOS). Not meant to be called while the world renders, where the window reports the world size.
	 */
	public static int density(Window window) {
		if (!MacOS.IS_MAC || window.getScreenWidth() <= 0) return 1;
		return Math.max(1, Math.round(window.getWidth() / (float) window.getScreenWidth()));
	}

	public static boolean active() {
		return active;
	}

	public static RenderTarget target() {
		return target;
	}

	/** Points the world at the smaller target; returns false when the world renders at full size this frame. */
	public static boolean begin(Minecraft mc) {
		int factor = MacOS.IS_MAC && !MacOS.retina() && !mc.gameRenderer.isPanoramicMode() ? density(mc.getWindow()) : 1;
		if (factor <= 1) return false;
		RenderTarget main = mc.getMainRenderTarget();
		int w = Math.max(1, main.width / factor), h = Math.max(1, main.height / factor);
		if (target == null) target = new TextureTarget("Lumina World", w, h, true);
		else if (target.width != w || target.height != h) target.resize(w, h);
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
		active = true;
		return true;
	}

	/** Scales the finished world up into the main target, where the GUI is drawn next. */
	public static void end(Minecraft mc) {
		if (!active) return;
		active = false;
		RenderTarget main = mc.getMainRenderTarget();
		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Lumina world upscale", main.getColorTextureView(), OptionalInt.empty())) {
			// a plain copy (no blending) with linear filtering
			pass.setPipeline(RenderPipelines.TRACY_BLIT);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("InSampler", target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.draw(0, 3);
		}
	}
}
