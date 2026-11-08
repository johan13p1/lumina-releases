package dev.lumina.client.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapRenderStateExtractor.class)
public interface LightTextureAccessor {
	/** Asks for the lightmap to be rebuilt on the next frame. */
	@Accessor("needsUpdate")
	void lumina$setUpdateLightTexture(boolean update);
}
