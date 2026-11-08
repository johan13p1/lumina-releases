package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.ZoomModule;
import dev.lumina.client.feature.visual.MotionBlurModule;
import dev.lumina.client.render.RenderScale;
import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FOV and zoom live in {@link CameraMixin} since 26.1 (the camera computes the FOV). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private List<Identifier> requestedPostEffects;

	@Shadow
	@Final
	@Mutable
	private RenderTarget mainRenderTarget;

	@Shadow
	@Final
	@Mutable
	private RenderTarget hud3DTarget;

	@Shadow
	@Final
	private GameRenderState gameRenderState;

	/** The real targets while the world renders into the smaller ones (see RenderScale). */
	@Unique
	private RenderTarget lumina$main;
	@Unique
	private RenderTarget lumina$hud3D;
	@Unique
	private float lumina$lineWidth;

	/** Uploads the frame's global uniforms again with another screen size. */
	@Unique
	private IntBinaryOperator lumina$globals;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJFILnet/minecraft/world/phys/Vec3;Z)V"))
	private void lumina$keepGlobals(GlobalSettingsUniform uniform, int width, int height, double glint, long gameTime, float partialTicks, int blur, Vec3 camera,
		boolean rgss, Operation<Void> original) {
		original.call(uniform, width, height, glint, gameTime, partialTicks, blur, camera, rgss);
		lumina$globals = (w, h) -> {
			original.call(uniform, w, h, glint, gameTime, partialTicks, blur, camera, rgss);
			return 0;
		};
	}

	/** The world renders into the smaller targets; line widths and shaders that use the screen size follow. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V"))
	private void lumina$beginWorld(CallbackInfo ci) {
		boolean panoramic = gameRenderState.levelRenderState.cameraRenderState.isPanoramicMode;
		if (lumina$main != null || !RenderScale.begin(minecraft.getWindow(), mainRenderTarget, panoramic)) return;
		lumina$main = mainRenderTarget;
		lumina$hud3D = hud3DTarget;
		mainRenderTarget = RenderScale.world();
		hud3DTarget = RenderScale.hudDepth();
		lumina$lineWidth = gameRenderState.windowRenderState.appropriateLineWidth;
		gameRenderState.windowRenderState.appropriateLineWidth = Math.max(2.5F, mainRenderTarget.width / 1920.0F * 2.5F);
		if (lumina$globals != null) lumina$globals.applyAsInt(mainRenderTarget.width, mainRenderTarget.height);
	}

	/** After the world and its post effects (motion blur included): back to the real targets, world scaled up. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void lumina$endWorld(CallbackInfo ci) {
		if (lumina$main == null) return;
		mainRenderTarget = lumina$main;
		hud3DTarget = lumina$hud3D;
		lumina$main = lumina$hud3D = null;
		gameRenderState.windowRenderState.appropriateLineWidth = lumina$lineWidth;
		RenderScale.upscale(mainRenderTarget);
		if (lumina$globals != null) lumina$globals.applyAsInt(mainRenderTarget.width, mainRenderTarget.height);
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void lumina$hideHandWhileZoomed(CameraRenderState cameraState, PlayerRenderState playerState, GpuTextureView depth, CallbackInfo ci) {
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		if (zoom != null && zoom.hideHand.on() && zoom.zoomedIn()) ci.cancel();
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void lumina$devFrame(CallbackInfo ci) {
		if (Lumina.DEV) Lumina.devFrame();
	}

	/** Motion blur is a post effect chain that runs after the world, like the vanilla ones. */
	@Inject(method = "update", at = @At("TAIL"))
	private void lumina$motionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
		MotionBlurModule blur = Lumina.modules().get(MotionBlurModule.class);
		if (blur == null || !blur.enabled() || minecraft.level == null) return;
		requestedPostEffects.add(blur.chain());
	}
}
