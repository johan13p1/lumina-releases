package dev.lumina.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("lightmapRenderStateExtractor")
	LightmapRenderStateExtractor lumina$lightmap();
}
