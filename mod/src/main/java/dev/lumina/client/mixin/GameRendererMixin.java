package dev.lumina.client.mixin;

import dev.lumina.client.feature.camera.FreeLookModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.FovChangerModule;
import dev.lumina.client.feature.camera.ZoomModule;
import dev.lumina.client.feature.visual.MotionBlurModule;
import dev.lumina.client.feature.world.WaypointsModule;
import dev.lumina.client.render.RenderScale;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import java.util.function.IntBinaryOperator;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	/** Uploads the frame's global uniforms again with another screen size (see RenderScale). */
	@Unique
	private IntBinaryOperator lumina$globals;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/client/Camera;Z)V"))
	private void lumina$keepGlobals(GlobalSettingsUniform uniform, int width, int height, double glint, long gameTime, DeltaTracker deltaTracker, int blur, Camera camera,
		boolean rgss, Operation<Void> original) {
		original.call(uniform, width, height, glint, gameTime, deltaTracker, blur, camera, rgss);
		lumina$globals = (w, h) -> {
			original.call(uniform, w, h, glint, gameTime, deltaTracker, blur, camera, rgss);
			return 0;
		};
	}

	/** The world renders into the smaller target; shaders that use the screen size (lines) get its size. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
	private void lumina$beginWorld(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		if (RenderScale.begin(minecraft) && lumina$globals != null) lumina$globals.applyAsInt(RenderScale.target().width, RenderScale.target().height);
	}

	@Inject(method = "pick", at = @At("TAIL"))
	private void lumina$clearFlyingTarget(float partialTick, CallbackInfo ci) {
		FreeLookModule m = Lumina.modules() == null ? null : Lumina.modules().get(FreeLookModule.class);
		if (m != null) m.clearTarget();
	}

	@ModifyExpressionValue(method = "getFov", at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I"))
	private int lumina$customFov(int original) {
		FovChangerModule m = Lumina.modules().get(FovChangerModule.class);
		return m != null && m.enabled() ? m.fov.getInt() : original;
	}

	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float lumina$zoom(float fov, @Local(argsOnly = true) boolean useFovSetting) {
		if (!useFovSetting) return fov;
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		float result = zoom != null ? (float) (fov / zoom.zoomDivisor()) : fov;
		WaypointsModule.lastFov = result;
		return result;
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void lumina$hideHandWhileZoomed(float partialTick, boolean bl, Matrix4f matrix, CallbackInfo ci) {
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		if (zoom != null && zoom.hideHand.on() && zoom.zoomedIn()) ci.cancel();
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void lumina$devFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		if (Lumina.DEV) Lumina.devFrame();
	}

	/** After the world: motion blur (still on the world target, so it costs less), then the upscale for the GUI. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void lumina$endWorld(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		MotionBlurModule blur = Lumina.modules().get(MotionBlurModule.class);
		if (blur != null && blur.enabled() && renderLevel && minecraft.level != null) {
			PostChain chain = minecraft.getShaderManager().getPostChain(blur.chain(), LevelTargetBundle.MAIN_TARGETS);
			if (chain != null) chain.process(minecraft.getMainRenderTarget(), resourcePool);
		}
		if (RenderScale.active()) {
			RenderScale.end(minecraft);
			if (lumina$globals != null) lumina$globals.applyAsInt(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
		}
	}
}
