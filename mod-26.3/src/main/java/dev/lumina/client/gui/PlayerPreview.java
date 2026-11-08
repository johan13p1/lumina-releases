package dev.lumina.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** Your player as a turnable 3D model in a menu, optionally wearing another skin. */
final class PlayerPreview {
	private PlayerPreview() {
	}

	/** Draws the player into the box; {@code yaw} is the body rotation (180 = facing you). */
	static void render(GuiGraphicsExtractor g, float x, float y, float w, float h, float yaw, @Nullable PlayerSkin skin) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		var renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
		EntityRenderState state = renderer.createRenderState(mc.player, 1.0F);
		state.lightCoords = 0xF000F0;
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = yaw;
			living.yRot = 0;
			living.xRot = 0;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		// the render state picks the slim or classic model from its skin
		if (skin != null && state instanceof AvatarRenderState avatar) avatar.skin = skin;
		// the preview is a picture-in-picture: it needs real screen coordinates
		Matrix3x2fStack pose = g.pose();
		Vector2f tl = pose.transformPosition(x, y, new Vector2f()), br = pose.transformPosition(x + w, y + h, new Vector2f());
		float scale = (br.y - tl.y) / 2.6f;
		Vector3f offset = new Vector3f(0, state.boundingBoxHeight / 2f + 0.0625F, 0);
		g.entity(state, scale, offset, new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(),
			Math.round(tl.x), Math.round(tl.y), Math.round(br.x), Math.round(br.y));
	}
}
