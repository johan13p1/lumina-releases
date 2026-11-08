package dev.lumina.client.mixin;

import ca.weblite.objc.Client;
import ca.weblite.objc.Proxy;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.platform.Window;
import com.sun.jna.Pointer;
import dev.lumina.client.Lumina;
import dev.lumina.client.util.MacOS;
import net.minecraft.server.packs.PackResources;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.glfw.GLFWNativeNSGL;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * macOS: the GUI is laid out in points and drawn at the Retina resolution (see RenderScale) unless
 * "Sharp Menus & HUD" is off, and the Dock shows the Lumina icon. 26.2 still uses GLFW, so the window
 * parts follow 1.21.11; the world scaling follows 26.3 (GameRendererMixin swaps the render target).
 */
@Mixin(Window.class)
public abstract class WindowMixin {
	@Shadow
	private int width;
	@Shadow
	private int framebufferWidth;
	@Shadow
	private int framebufferHeight;

	/** True when "Sharp Menus & HUD" is off: the window's view then draws at 1x (see {@link #lumina$pointSurface}). */
	@Unique
	private static boolean lumina$pointResolution;

	/** Framebuffer pixels per window point (2 on Retina displays). */
	@Unique
	private int lumina$density() {
		if (!MacOS.IS_MAC || width <= 0) return 1;
		return Math.max(1, Math.round(framebufferWidth / (float) width));
	}

	/**
	 * The GUI scale is picked like on a non-Retina display (from the size in points), so menus keep
	 * their size; {@link #lumina$retinaGuiScale} then draws them with the Retina pixels.
	 */
	@Inject(method = "calculateScale", at = @At("HEAD"), cancellable = true)
	private void lumina$pointScale(int max, boolean enforceUnicode, CallbackInfoReturnable<Integer> cir) {
		int density = lumina$density();
		if (density <= 1) return;
		int w = framebufferWidth / density, h = framebufferHeight / density;
		int scale = 1;
		while (scale != max && scale < w && scale < h && w / (scale + 1) >= 320 && h / (scale + 1) >= 240) scale++;
		if (enforceUnicode && scale % 2 != 0) scale++;
		cir.setReturnValue(scale);
	}

	@ModifyVariable(method = "setGuiScale", at = @At("HEAD"), argsOnly = true)
	private int lumina$retinaGuiScale(int scale) {
		return scale * lumina$density();
	}

	/**
	 * "Sharp Menus & HUD" off (and Retina Resolution off): the whole window renders at 1x, the most
	 * FPS. GLFW 3.4 ignores GLFW_COCOA_RETINA_FRAMEBUFFER, so the view's Retina surface is turned off
	 * directly; GLFW keeps reporting the Retina size for a while, so the framebuffer size is taken
	 * from the window size below.
	 */
	@ModifyExpressionValue(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
	private static long lumina$pointSurface(long handle) {
		if (!MacOS.IS_MAC || handle == 0 || MacOS.sharpUi() || MacOS.retina()) return handle;
		try {
			Client objc = Client.getInstance();
			Pointer view = ((Proxy) objc.sendProxy(new Pointer(GLFWNativeCocoa.glfwGetCocoaWindow(handle)), "contentView")).getPeer();
			objc.send(view, "setWantsBestResolutionOpenGLSurface:", false);
			objc.send(new Pointer(GLFWNativeNSGL.glfwGetNSGLContext(handle)), "update");
			lumina$pointResolution = true;
			Lumina.LOG.info("Retina framebuffer off (Sharp Menus & HUD is off)");
		} catch (Throwable e) {
			Lumina.LOG.warn("Could not turn off the Retina framebuffer", e);
		}
		return handle;
	}

	@WrapOperation(method = "refreshFramebufferSize", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwGetFramebufferSize(J[I[I)V", remap = false))
	private void lumina$framebufferSize(long handle, int[] width, int[] height, Operation<Void> original) {
		if (lumina$pointResolution) GLFW.glfwGetWindowSize(handle, width, height);
		else original.call(handle, width, height);
	}

	@ModifyVariable(method = "onFramebufferResize", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int lumina$resizeWidth(int width, long handle) {
		return lumina$pointResolution ? lumina$windowSize(handle)[0] : width;
	}

	@ModifyVariable(method = "onFramebufferResize", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int lumina$resizeHeight(int height, long handle) {
		return lumina$pointResolution ? lumina$windowSize(handle)[1] : height;
	}

	@Unique
	private static int[] lumina$windowSize(long handle) {
		int[] w = new int[1], h = new int[1];
		GLFW.glfwGetWindowSize(handle, w, h);
		return new int[] {Math.max(1, w[0]), Math.max(1, h[0])};
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void lumina$logSize(CallbackInfo ci) {
		Window w = (Window) (Object) this;
		if (MacOS.IS_MAC) Lumina.LOG.info("Framebuffer {}x{}, window {}x{}", w.getWidth(), w.getHeight(), w.getScreenWidth(), w.getScreenHeight());
	}

	@Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
	private void lumina$dockIcon(PackResources resources, IconSet icons, CallbackInfo ci) {
		if (!MacOS.IS_MAC) return;
		try {
			MacosUtil.loadIcon(MacOS::dockIcon);
			ci.cancel();
		} catch (Exception e) {
			Lumina.LOG.warn("Could not set the Lumina Dock icon", e);
		}
	}
}
