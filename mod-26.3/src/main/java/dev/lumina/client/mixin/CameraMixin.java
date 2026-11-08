package dev.lumina.client.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.FovChangerModule;
import dev.lumina.client.feature.camera.FreeLookModule;
import dev.lumina.client.feature.camera.ZoomModule;
import dev.lumina.client.feature.world.WaypointsModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Vec3 position;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float lumina$freeLookYaw(Entity entity, float partialTick, Operation<Float> original) {
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		if (m != null && m.active() && entity == Minecraft.getInstance().player) return m.yaw();
		return original.call(entity, partialTick);
	}

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float lumina$freeLookPitch(Entity entity, float partialTick, Operation<Float> original) {
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		if (m != null && m.active() && entity == Minecraft.getInstance().player) return m.pitch();
		return original.call(entity, partialTick);
	}

	@ModifyExpressionValue(method = "calculateFov", at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I"))
	private int lumina$customFov(int original) {
		FovChangerModule m = Lumina.modules().get(FovChangerModule.class);
		return m != null && m.enabled() ? m.fov.getInt() : original;
	}

	/** World FOV only; the hand uses calculateHudFov and is not zoomed. */
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float lumina$zoom(float fov) {
		ZoomModule zoom = Lumina.modules().get(ZoomModule.class);
		float result = zoom != null ? (float) (fov / zoom.zoomDivisor()) : fov;
		WaypointsModule.lastFov = result;
		return result;
	}

	/** The spectator camera replaces where the camera ended up; everything after this frame uses it. */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void lumina$spectatorCamera(CallbackInfo ci) {
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		if (m == null || !m.flying()) return;
		setRotation(m.yaw(), m.pitch());
		setPosition(m.nextFlyPosition(position));
	}
}
