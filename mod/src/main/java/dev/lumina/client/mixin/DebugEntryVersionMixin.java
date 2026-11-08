package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The first F3 line reads "Lumina 1.21.11 (v1.1.0)" instead of "Minecraft 1.21.11 (fabric-loader-…/fabric)". */
@Mixin(targets = "net.minecraft.client.gui.components.debug.DebugEntryVersion")
public abstract class DebugEntryVersionMixin {
	@ModifyArg(method = "display", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;addPriorityLine(Ljava/lang/String;)V"))
	private String lumina$version(String line) {
		return Lumina.gameName() + " (v" + Lumina.VERSION + ")";
	}
}
