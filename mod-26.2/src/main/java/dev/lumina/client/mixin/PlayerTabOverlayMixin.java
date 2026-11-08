package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.TabEditorModule;
import dev.lumina.client.online.LuminaOnline;
import dev.lumina.client.render.Logo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	private static TabEditorModule lumina$module() {
		TabEditorModule m = Lumina.modules().get(TabEditorModule.class);
		return m != null && m.enabled() ? m : null;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void lumina$colors(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		TabEditorModule m = lumina$module();
		if (m != null) {
			if (color == Integer.MIN_VALUE) color = m.background.color();
			else if (color == Minecraft.getInstance().options.getBackgroundColor(553648127)) color = m.rowBackground.color();
		}
		original.call(g, x0, y0, x1, y1, color);
	}

	/** The Lumina mark in front of every Lumina player's name in the tab list, yours included. Always on. */
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void lumina$logoName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		if (LuminaOnline.showsLogo(info.getProfile().id())) {
			cir.setReturnValue(Logo.withLogo(cir.getReturnValue()));
		}
	}

	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void lumina$numericPing(GuiGraphicsExtractor g, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
		TabEditorModule m = lumina$module();
		if (m == null || !m.numericPing.on()) return;
		var font = Minecraft.getInstance().font;
		int ms = info.getLatency();
		String text = ms < 0 ? "?" : String.valueOf(ms);
		int tw = font.width(text);
		var pose = g.pose();
		pose.pushMatrix();
		float scale = 0.75f;
		pose.translate(x + width - 1 - tw * scale, y + 1.5f);
		pose.scale(scale, scale);
		g.text(font, text, 0, 0, TabEditorModule.pingColor(ms), false);
		pose.popMatrix();
		ci.cancel();
	}

	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;"))
	private Component lumina$header(Component original) {
		TabEditorModule m = lumina$module();
		return m != null && m.hideHeader.on() ? null : original;
	}

	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;"))
	private Component lumina$footer(Component original) {
		TabEditorModule m = lumina$module();
		return m != null && m.hideFooter.on() ? null : original;
	}
}
