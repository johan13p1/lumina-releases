package dev.lumina.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lumina.client.Lumina;
import net.minecraft.client.renderer.RenderPipelines;

public final class LuminaPipelines {
	/** Anti-aliased circles / rounded corners / rings / soft shadows, see rounded.fsh. */
	public static final RenderPipeline ROUNDED = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(Lumina.id("pipeline/rounded"))
		.withVertexShader(Lumina.id("core/rounded"))
		.withFragmentShader(Lumina.id("core/rounded"))
		.withSampler("Sampler0")
		.withBlend(BlendFunction.TRANSLUCENT)
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	/** Texture clipped to a rounded square or circle, see masked.fsh. */
	public static final RenderPipeline MASKED = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(Lumina.id("pipeline/masked"))
		.withVertexShader(Lumina.id("core/masked"))
		.withFragmentShader(Lumina.id("core/masked"))
		.withSampler("Sampler0")
		.withBlend(BlendFunction.TRANSLUCENT)
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	/**
	 * The entity pipeline without the directional shading that dims every side of a model to
	 * 40-100 %, so capes keep the colors of their image. World light (day, night, caves) still applies.
	 */
	public static final RenderPipeline CAPE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
		.withLocation(Lumina.id("pipeline/cape"))
		.withShaderDefine("ALPHA_CUTOUT", 0.1F)
		.withShaderDefine("NO_CARDINAL_LIGHTING")
		.withSampler("Sampler1")
		.withCull(false)
		.build();

	private LuminaPipelines() {
	}
}
