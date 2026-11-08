package dev.lumina.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.cosmetics.CosmeticsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Your particle effect also shows around your player in menus: the inventory and the Cosmetics
 * page draw the player as a picture-in-picture, and the particles join that 3D scene.
 */
@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {
	@Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
		at = @At("TAIL"))
	private void lumina$particles(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player == null || Lumina.modules() == null) return;
		if (!(state.renderState() instanceof AvatarRenderState avatar) || avatar.id != player.getId()) return;
		CosmeticsModule cosmetics = Lumina.modules().get(CosmeticsModule.class);
		if (cosmetics == null) return;
		cosmetics.particles.renderPreview(poseStack, collector);
	}
}
