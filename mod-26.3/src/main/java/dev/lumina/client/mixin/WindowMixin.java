package dev.lumina.client.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import dev.lumina.client.Lumina;
import dev.lumina.client.util.MacOS;
import java.io.InputStream;
import java.util.List;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * macOS: the GUI is laid out in points and drawn at the Retina resolution (see RenderScale) unless
 * "Sharp Menus & HUD" is off, and the Dock shows the Lumina icon.
 */
@Mixin(Window.class)
public abstract class WindowMixin {
	/** SDL_WINDOW_HIGH_PIXEL_DENSITY: the window gets a framebuffer at the display's Retina scale. */
	private static final long HIGH_PIXEL_DENSITY = 0x2000L;

	@Shadow
	private int width;
	@Shadow
	private int framebufferWidth;
	@Shadow
	private int framebufferHeight;

	@Shadow
	private void setIcon(List<IoSupplier<InputStream>> icons) {
	}

	/** A Retina framebuffer unless "Sharp Menus & HUD" and "Retina Resolution" are both off (then 1x: the most FPS). */
	@ModifyArg(method = "createWindow", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3)
	private long lumina$retinaFlag(long flags) {
		if (!MacOS.IS_MAC) return flags;
		boolean retina = MacOS.sharpUi() || MacOS.retina();
		Lumina.LOG.info("Retina framebuffer {}", retina ? "on" : "off (Sharp Menus & HUD is off)");
		return retina ? flags | HIGH_PIXEL_DENSITY : flags & ~HIGH_PIXEL_DENSITY;
	}

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

	@Inject(method = "setIcon(Lnet/minecraft/server/packs/PackMetadataResources;Lcom/mojang/blaze3d/platform/IconSet;)V", at = @At("HEAD"), cancellable = true)
	private void lumina$dockIcon(PackMetadataResources resources, IconSet icons, CallbackInfo ci) {
		if (!MacOS.IS_MAC) return;
		try {
			setIcon(List.of(MacOS::dockIcon));
			ci.cancel();
		} catch (Exception e) {
			Lumina.LOG.warn("Could not set the Lumina Dock icon", e);
		}
	}
}
