package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** With the vanilla main menu the version in the corner reads "Lumina 1.21.11" as well. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
	@ModifyArg(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"), index = 1)
	private String lumina$version(String text) {
		return Lumina.gameName();
	}
}
