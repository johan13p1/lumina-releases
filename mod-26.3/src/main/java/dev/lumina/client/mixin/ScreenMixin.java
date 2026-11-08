package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.InventoryModule;
import dev.lumina.client.feature.visual.MenuBlurModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	/** In-game screens (inventories, chests, ...) get optional blur and a flat background color. */
	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void lumina$inGameBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (Lumina.modules() == null) return;
		MenuBlurModule blur = Lumina.modules().get(MenuBlurModule.class);
		boolean blurred = blur != null && blur.enabled() && blur.inGameScreens.on() && Minecraft.getInstance().options.getMenuBackgroundBlurriness() >= 1;
		if (blurred) g.blurBeforeThisStratum();
		InventoryModule inv = Lumina.modules().get(InventoryModule.class);
		if (inv != null && inv.enabled() && inv.customBackground.on() && (Object) this instanceof AbstractContainerScreen<?>) {
			g.fill(0, 0, width, height, inv.backgroundColor.color());
			ci.cancel();
		} else if (blurred && blur.darken.on() == false) {
			ci.cancel();
		}
	}
}
