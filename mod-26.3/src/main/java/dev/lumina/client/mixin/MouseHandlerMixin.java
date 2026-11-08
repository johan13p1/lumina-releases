package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.FreeLookModule;
import dev.lumina.client.feature.camera.ZoomModule;
import dev.lumina.client.util.CpsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void lumina$countClicks(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && minecraft.gui.screen() == null && window == minecraft.getWindow().handle()) {
			CpsTracker.click(info.button());
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void lumina$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (minecraft.gui.screen() != null || window != minecraft.getWindow().handle()) return;
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		if (zoom != null && zoom.onScroll(vertical)) {
			ci.cancel();
			return;
		}
		FreeLookModule freeLook = Lumina.modules().get(FreeLookModule.class);
		if (freeLook != null && freeLook.onScroll(vertical)) ci.cancel();
	}

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void lumina$turn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		double k = zoom != null && zoom.zoomedIn() ? zoom.sensitivityScale() : 1;
		FreeLookModule freeLook = Lumina.modules().get(FreeLookModule.class);
		if (freeLook != null && freeLook.active()) freeLook.turn(dx * k, dy * k);
		else original.call(player, dx * k, dy * k);
	}

	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
	private boolean lumina$cinematicZoom(boolean original) {
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		return original || (zoom != null && zoom.cinematic.on() && zoom.zoomedIn());
	}
}
