package dev.lumina.client.render;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.lumina.client.Lumina;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.BindGroupLayouts;

public final class LuminaPipelines {
	/** Anti-aliased circles / rounded corners / rings / soft shadows, see rounded.fsh. */
	public static final RenderPipeline ROUNDED = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
		.withLocation(Lumina.id("pipeline/rounded"))
		.withVertexShader(Lumina.id("core/rounded"))
		.withFragmentShader(Lumina.id("core/rounded"))
		.build();

	/** Texture clipped to a rounded square or circle, see masked.fsh. */
	public static final RenderPipeline MASKED = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
		.withLocation(Lumina.id("pipeline/masked"))
		.withVertexShader(Lumina.id("core/masked"))
		.withFragmentShader(Lumina.id("core/masked"))
		.build();

	/**
	 * The entity pipeline without the directional shading that dims every side of a model to
	 * 40-100 %, so capes keep the colors of their image. World light (day, night, caves) still applies.
	 */
	public static final RenderPipeline CAPE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
		.withLocation(Lumina.id("pipeline/cape"))
		.withShaderDefine("ALPHA_CUTOUT", 0.1F)
		.withShaderDefine("NO_CARDINAL_LIGHTING")
		.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
		.withCull(false)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();

	private LuminaPipelines() {
	}
}
