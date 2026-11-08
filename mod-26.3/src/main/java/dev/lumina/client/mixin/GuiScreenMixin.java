package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.ClientSettingsModule;
import dev.lumina.client.feature.utility.GuiScaleModule;
import dev.lumina.client.gui.LuminaTitleScreen;
import dev.lumina.client.render.RenderScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Since 26.2 the current screen lives in {@link Gui} instead of {@link Minecraft}. */
@Mixin(Gui.class)
public abstract class GuiScreenMixin {
	@Shadow
	public abstract @Nullable Screen screen();

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
		Minecraft mc = Minecraft.getInstance();
		var window = mc.getWindow();
		int auto = window.calculateScale(0, mc.isEnforceUnicode());
		int normal = window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
		int custom = m.scaleFor(screen(), normal, auto);
		int wanted = custom > 0 ? custom : normal;
		// the window multiplies the scale by the Retina density
		if (wanted * RenderScale.density(window) != window.getGuiScale()) mc.resizeGui();
	}
}
