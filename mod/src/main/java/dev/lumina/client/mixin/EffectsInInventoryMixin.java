package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.InventoryModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void lumina$hideEffects(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
		InventoryModule m = Lumina.modules().get(InventoryModule.class);
		if (m != null && m.enabled() && m.hideEffects.on()) ci.cancel();
	}
}
