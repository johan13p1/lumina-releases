package dev.lumina.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * A textured quad clipped to a circle / rounded square with free UVs per corner (so the texture
 * can rotate inside a fixed frame). Used by the minimap.
 */
public record MaskedQuad(
	TextureSetup textureSetup,
	Matrix3x2f pose,
	float x0, float y0, float x1, float y1,
	float[] uv,
	int radiusByte,
	int alphaByte,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

	/**
	 * @param uv u,v for top-left, bottom-left, bottom-right, top-right
	 * @param radius corner radius relative to half the size (1 = circle)
	 */
	public static void draw(GuiGraphicsExtractor g, Identifier texture, float x, float y, float w, float h, float[] uv, float radius, float alpha) {
		AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		ScreenRectangle scissor = g.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1).transformMaxBounds(pose);
		if (scissor != null) bounds = scissor.intersection(bounds);
		g.guiRenderState.addGuiElement(new MaskedQuad(TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()), pose,
			x, y, x + w, y + h, uv, Math.round(Math.max(0, Math.min(1, radius)) * 255), Math.round(Math.max(0, Math.min(1, alpha)) * 255), scissor, bounds));
	}

	@Override
	public RenderPipeline pipeline() {
		return LuminaPipelines.MASKED;
	}

	@Override
	public void buildVertices(VertexConsumer vc) {
		vc.addVertexWith2DPose(pose, x0, y0).setUv(uv[0], uv[1]).setColor(0, 0, radiusByte, alphaByte);
		vc.addVertexWith2DPose(pose, x0, y1).setUv(uv[2], uv[3]).setColor(0, 255, radiusByte, alphaByte);
		vc.addVertexWith2DPose(pose, x1, y1).setUv(uv[4], uv[5]).setColor(255, 255, radiusByte, alphaByte);
		vc.addVertexWith2DPose(pose, x1, y0).setUv(uv[6], uv[7]).setColor(255, 0, radiusByte, alphaByte);
	}
}
