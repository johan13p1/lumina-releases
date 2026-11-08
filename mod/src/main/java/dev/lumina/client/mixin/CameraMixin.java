package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.FreeLookModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Vec3 position;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float lumina$freeLookYaw(Entity entity, float partialTick, Operation<Float> original) {
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		if (m != null && m.active() && entity == Minecraft.getInstance().player) return m.yaw();
		return original.call(entity, partialTick);
	}

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float lumina$freeLookPitch(Entity entity, float partialTick, Operation<Float> original) {
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		if (m != null && m.active() && entity == Minecraft.getInstance().player) return m.pitch();
		return original.call(entity, partialTick);
	}

	/** The spectator camera replaces where the camera ended up; everything after this frame uses it. */
	@Inject(method = "setup", at = @At("TAIL"))
	private void lumina$spectatorCamera(CallbackInfo ci) {
		FreeLookModule m = Lumina.modules().get(FreeLookModule.class);
		if (m == null || !m.flying()) return;
		setRotation(m.yaw(), m.pitch());
		setPosition(m.nextFlyPosition(position));
	}
}
