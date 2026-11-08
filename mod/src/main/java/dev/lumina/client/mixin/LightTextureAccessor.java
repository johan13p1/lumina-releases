package dev.lumina.client.mixin;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightTexture.class)
public interface LightTextureAccessor {
	/** Asks for the lightmap to be rebuilt on the next frame. */
	@Accessor("updateLightTexture")
	void lumina$setUpdateLightTexture(boolean update);
}
