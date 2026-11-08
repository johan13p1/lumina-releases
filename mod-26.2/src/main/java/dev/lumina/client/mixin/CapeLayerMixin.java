package dev.lumina.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.cosmetics.CapeLibrary;
import dev.lumina.client.feature.cosmetics.CapeRenderer;
import dev.lumina.client.feature.cosmetics.CapeTexture;
import dev.lumina.client.feature.cosmetics.CosmeticsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumina capes replace the vanilla one: yours, and those of other Lumina players. */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin extends RenderLayer<AvatarRenderState, PlayerModel> {
	protected CapeLayerMixin(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Shadow
	protected abstract boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType type);

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
		at = @At("HEAD"), cancellable = true)
	private void lumina$cape(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player == null || Lumina.modules() == null) return;
		CosmeticsModule cosmetics = Lumina.modules().get(CosmeticsModule.class);
		if (cosmetics == null) return;
		CapeLibrary.Entry entry = cosmetics.capeOf(state.id);
		if (entry == null) return;
		ci.cancel();
		if (state.isInvisible || !state.showCape || hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) return;
		CapeTexture tex = cosmetics.capes.texture(entry);
		if (tex == null) return;
		CapeRenderer.submit(poseStack, collector, light, state, getParentModel(), tex, cosmetics.capePhysics.on(),
			hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID));
	}
}
