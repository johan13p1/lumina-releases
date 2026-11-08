package dev.lumina.client.feature.visual;

import dev.lumina.client.mixin.LightTextureAccessor;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** See in the dark: lights the world like the night vision effect, without the effect. */
public final class FullbrightModule extends Module {
	public final NumberSetting strength;
	/** The strength the lightmap was last built with, -1 while off. */
	private float applied = -1;

	public FullbrightModule() {
		super("fullbright", "Fullbright", "See everything, even in caves and at night", Category.VISUAL, Icons.SUN, false);
		strength = add(new NumberSetting("strength", "Strength", 1, 0.1, 1, 0.05));
		withToggleKey();
	}

	@Override
	public void tick() {
		float now = enabled() ? strength.getFloat() : -1;
		if (now == applied) return;
		applied = now;
		// BadOptimizations (performance pack) only rebuilds the lightmap when vanilla inputs change,
		// so switching Fullbright or its strength has to ask for a rebuild itself
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameRenderer != null) ((LightTextureAccessor) ((dev.lumina.client.mixin.GameRendererAccessor) mc.gameRenderer).lumina$lightmap()).lumina$setUpdateLightTexture(true);
	}
}
