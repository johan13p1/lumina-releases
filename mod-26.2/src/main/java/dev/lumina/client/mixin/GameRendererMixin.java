package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.ZoomModule;
import dev.lumina.client.feature.visual.MotionBlurModule;
import dev.lumina.client.render.RenderScale;
import java.util.function.IntBinaryOperator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FOV and zoom live in {@link CameraMixin} since 26.1 (the camera computes the FOV). 26.2 still runs
 * the post effects right after the world inside render(), like 1.21.11, so motion blur is applied there.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	@Mutable
	private RenderTarget mainRenderTarget;

	@Shadow
	@Final
	private GameRenderState gameRenderState;

	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	/** The real main target while the world renders into the smaller one (see RenderScale). */
	@Unique
	private RenderTarget lumina$main;
	@Unique
	private float lumina$lineWidth;
	/** Whether the world was rendered this frame (render() skips it in menus). */
	@Unique
	private boolean lumina$worldRendered;

	/** Uploads the frame's global uniforms again with another screen size. */
	@Unique
	private IntBinaryOperator lumina$globals;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/world/phys/Vec3;Z)V"))
	private void lumina$keepGlobals(GlobalSettingsUniform uniform, int width, int height, double glint, long gameTime, DeltaTracker deltaTracker, int blur, Vec3 camera,
		boolean rgss, Operation<Void> original) {
		original.call(uniform, width, height, glint, gameTime, deltaTracker, blur, camera, rgss);
		lumina$globals = (w, h) -> {
			original.call(uniform, w, h, glint, gameTime, deltaTracker, blur, camera, rgss);
			return 0;
		};
	}

	/** The world renders into the smaller target; line widths and shaders that use the screen size follow. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
	private void lumina$beginWorld(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		lumina$worldRendered = true;
		boolean panoramic = gameRenderState.levelRenderState.cameraRenderState.isPanoramicMode;
		if (lumina$main != null || !RenderScale.begin(minecraft.getWindow(), mainRenderTarget, panoramic)) return;
		lumina$main = mainRenderTarget;
		mainRenderTarget = RenderScale.world();
		lumina$lineWidth = gameRenderState.windowRenderState.appropriateLineWidth;
		gameRenderState.windowRenderState.appropriateLineWidth = Math.max(2.5F, mainRenderTarget.width / 1920.0F * 2.5F);
		if (lumina$globals != null) lumina$globals.applyAsInt(mainRenderTarget.width, mainRenderTarget.height);
	}

	/** After the world and its post effects: motion blur (still on the world target, so it costs less), then back to the real target. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void lumina$endWorld(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		boolean world = lumina$worldRendered;
		lumina$worldRendered = false;
		MotionBlurModule blur = Lumina.modules().get(MotionBlurModule.class);
		if (world && blur != null && blur.enabled() && minecraft.level != null) {
			PostChain chain = minecraft.getShaderManager().getPostChain(blur.chain(), LevelTargetBundle.MAIN_TARGETS);
			if (chain != null) chain.process(mainRenderTarget, resourcePool);
		}
		if (lumina$main == null) return;
		mainRenderTarget = lumina$main;
		lumina$main = null;
		gameRenderState.windowRenderState.appropriateLineWidth = lumina$lineWidth;
		RenderScale.upscale(mainRenderTarget);
		if (lumina$globals != null) lumina$globals.applyAsInt(mainRenderTarget.width, mainRenderTarget.height);
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void lumina$hideHandWhileZoomed(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		if (zoom != null && zoom.hideHand.on() && zoom.zoomedIn()) ci.cancel();
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void lumina$devFrame(CallbackInfo ci) {
		if (Lumina.DEV) Lumina.devFrame();
	}
}
