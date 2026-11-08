package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.realmsclient.RealmsMainScreen;
import dev.lumina.client.Lumina;
import dev.lumina.client.gui.LuminaTitleScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Realms and the mod list live in the options instead of the main menu. */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
	@Shadow
	@Final
	private Screen lastScreen;

	protected OptionsScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private void lumina$extraButtons(CallbackInfo ci, @Local GridLayout.RowHelper rows) {
		Screen self = this;
		// Realms only makes sense from the main menu, not while playing
		if (lastScreen instanceof LuminaTitleScreen || lastScreen instanceof TitleScreen) {
			Button realms = rows.addChild(Button.builder(Component.literal("Realms..."), b -> minecraft.setScreen(new RealmsMainScreen(self))).build());
			realms.active = minecraft.allowsMultiplayer();
		}
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			rows.addChild(Button.builder(Component.literal("Mods..."), b -> lumina$openModMenu(self)).build());
		}
	}

	private void lumina$openModMenu(Screen parent) {
		try {
			Class<?> cls = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			minecraft.setScreen((Screen) cls.getConstructor(Screen.class).newInstance(parent));
		} catch (ReflectiveOperationException | RuntimeException e) {
			Lumina.LOG.warn("Could not open Mod Menu", e);
		}
	}
}
