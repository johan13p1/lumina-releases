package dev.lumina.client.mixin;

import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import dev.lumina.client.feature.camera.FreeLookModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.ClientSettingsModule;
import dev.lumina.client.feature.utility.GuiScaleModule;
import dev.lumina.client.gui.LuminaTitleScreen;
import dev.lumina.client.render.RenderScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	@Nullable
	public Screen screen;

	@Shadow
	@Final
	private Window window;

	@Shadow
	public abstract void resizeDisplay();

	@Shadow
	public abstract boolean isEnforceUnicode();

	@ModifyExpressionValue(method = "resizeDisplay", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;calculateScale(IZ)I"))
	private int lumina$screenScale(int original) {
		if (Lumina.modules() == null) return original;
		GuiScaleModule m = Lumina.modules().get(GuiScaleModule.class);
		if (m == null) return original;
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

	/** While the world renders, it renders into the smaller world target (see RenderScale). */
	@Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
	private void lumina$worldTarget(CallbackInfoReturnable<RenderTarget> cir) {
		if (RenderScale.active()) cir.setReturnValue(RenderScale.target());
	}

	/** The window is called "Lumina 1.21.11" instead of "Minecraft* 1.21.11". */
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void lumina$windowTitle(CallbackInfoReturnable<String> cir) {
		// keep what follows the version, e.g. " - Singleplayer"
		String rest = cir.getReturnValue().replaceFirst("^Minecraft\\*? \\S+", "");
		cir.setReturnValue(Lumina.gameName() + rest);
	}

	/** Swap the vanilla title screen for Lumina's main menu. */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen lumina$titleScreen(Screen screen) {
		if (screen instanceof TitleScreen && Lumina.modules() != null) {
			ClientSettingsModule s = Lumina.settings();
			if (s != null && s.customMainMenu.on()) return new LuminaTitleScreen();
		}
		return screen;
	}

	@Inject(method = "setScreen", at = @At("RETURN"))
	private void lumina$applyScreenScale(@Nullable Screen newScreen, CallbackInfo ci) {
		if (Lumina.modules() == null) return;
		GuiScaleModule m = Lumina.modules().get(GuiScaleModule.class);
		if (m == null || !m.enabled()) return;
		Minecraft self = (Minecraft) (Object) this;
		int auto = window.calculateScale(0, isEnforceUnicode());
		int normal = window.calculateScale(self.options.guiScale().get(), isEnforceUnicode());
		int custom = m.scaleFor(screen, normal, auto);
		int wanted = custom > 0 ? custom : normal;
		// the window multiplies the scale by the Retina density
		if (wanted * RenderScale.density(window) != window.getGuiScale()) resizeDisplay();
	}
}
