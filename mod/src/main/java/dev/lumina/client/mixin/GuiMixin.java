package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.AttackIndicatorModule;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** Lumina draws its own attack indicator, so hide the vanilla crosshair one. */
	@ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object lumina$hideVanillaIndicator(Object original) {
		AttackIndicatorModule m = Lumina.modules().get(AttackIndicatorModule.class);
		if (m != null && m.enabled() && original == AttackIndicatorStatus.CROSSHAIR) return AttackIndicatorStatus.OFF;
		return original;
	}
}
