package dev.lumina.client.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import dev.lumina.client.feature.camera.FreeLookModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.Window;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.GuiScaleModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	@Final
	private Window window;

	@Shadow
	public abstract boolean isEnforceUnicode();

	@ModifyExpressionValue(method = "resizeGui", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;calculateScale(IZ)I"))
	private int lumina$screenScale(int original) {
		if (Lumina.modules() == null) return original;
		GuiScaleModule m = Lumina.modules().get(GuiScaleModule.class);
		if (m == null) return original;
		Minecraft self = (Minecraft) (Object) this;
		Screen screen = self.gui == null ? null : self.gui.screen();
		int auto = window.calculateScale(0, isEnforceUnicode());
		int custom = m.scaleFor(screen, original, auto);
		return custom > 0 ? custom : original;
	}

	@Unique
	private static boolean lumina$flying() {
		if (Lumina.modules() == null) return false;
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		return m != null && m.flying();
	}

	/** While the spectator camera flies, your player does nothing: queued key presses are dropped. */
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void lumina$dropKeysWhileFlying(CallbackInfo ci) {
		if (!lumina$flying()) return;
		Options o = ((Minecraft) (Object) this).options;
		for (KeyMapping k : new KeyMapping[] {o.keyTogglePerspective, o.keySwapOffhand, o.keyDrop, o.keyPickItem, o.keyAttack, o.keyUse}) {
			while (k.consumeClick()) {
			}
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void lumina$noAttackWhileFlying(CallbackInfoReturnable<Boolean> cir) {
		if (lumina$flying()) cir.setReturnValue(false);
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void lumina$noUseWhileFlying(CallbackInfo ci) {
		if (lumina$flying()) ci.cancel();
	}

	/** Not a cancel: with false the game stops any block breaking that was still going on. */
	@ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
	private boolean lumina$noMiningWhileFlying(boolean down) {
		return down && !lumina$flying();
	}

	@Inject(method = "pick", at = @At("TAIL"))
	private void lumina$clearFlyingTarget(float partialTicks, CallbackInfo ci) {
		FreeLookModule m = Lumina.modules() == null ? null : Lumina.modules().get(FreeLookModule.class);
		if (m != null) m.clearTarget();
	}

	/** The window is called "Lumina 26.3" instead of "Minecraft* 26.3". */
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void lumina$windowTitle(CallbackInfoReturnable<String> cir) {
		// keep what follows the version, e.g. " - Singleplayer"
		String rest = cir.getReturnValue().replaceFirst("^Minecraft\\*? \\S+", "");
		cir.setReturnValue(Lumina.gameName() + rest);
	}
}
