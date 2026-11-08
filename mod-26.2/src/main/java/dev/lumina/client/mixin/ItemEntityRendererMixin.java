package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.ItemPhysicsModule;
import dev.lumina.client.feature.visual.ItemPhysicsState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
	private void lumina$extractPhysics(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
		Vec3 v = entity.getDeltaMovement();
		((ItemPhysicsState) state).lumina$set(entity.onGround(), entity.isInWater() || entity.isInLava(), (float) v.length(), entity.getId());
	}

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")
	)
	private void lumina$physicsTranslate(PoseStack pose, float x, float y, float z, Operation<Void> original,
		ItemEntityRenderState state, PoseStack poseArg, SubmitNodeCollector collector, CameraRenderState camera) {
		ItemPhysicsModule module = Lumina.modules().get(ItemPhysicsModule.class);
		if (module == null || !module.enabled()) {
			original.call(pose, x, y, z);
			return;
		}
		ItemPhysicsState phys = (ItemPhysicsState) state;
		AABB box = state.item.getModelBoundingBox();
		boolean flat = box.getZsize() <= 0.0625F;
		if (phys.lumina$onGround()) {
			// rest on the ground: flat items lie on their side, blocks sit on their bottom face
			float lift = flat ? (float) box.getZsize() / 2f + 0.005f : -(float) box.minY;
			pose.translate(0, lift, 0);
		} else {
			pose.translate(0, (float) (-box.minY + box.getYsize() / 2), 0);
		}
	}

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V")
	)
	private void lumina$physicsRotate(PoseStack pose, Quaternionfc rotation, Operation<Void> original,
		ItemEntityRenderState state, PoseStack poseArg, SubmitNodeCollector collector, CameraRenderState camera) {
		ItemPhysicsModule module = Lumina.modules().get(ItemPhysicsModule.class);
		if (module == null || !module.enabled()) {
			original.call(pose, rotation);
			return;
		}
		ItemPhysicsState phys = (ItemPhysicsState) state;
		AABB box = state.item.getModelBoundingBox();
		boolean flat = box.getZsize() <= 0.0625F;
		// stable random yaw per entity
		float yaw = (phys.lumina$entityId() * 137.508f) % 360f;
		if (phys.lumina$onGround()) {
			if (flat || module.blocksFlat.on()) pose.mulPose(Axis.YP.rotationDegrees(yaw));
			if (flat) pose.mulPose(Axis.XP.rotationDegrees(90));
		} else {
			float speed = module.spinSpeed.getFloat() * (phys.lumina$inFluid() ? 0.25f : 1f);
			float tumble = state.ageInTicks * 18f * speed * Math.min(1.5f, 0.4f + phys.lumina$speed() * 4f);
			pose.mulPose(Axis.YP.rotationDegrees(yaw));
			pose.mulPose(Axis.XP.rotationDegrees(tumble));
		}
	}
}
