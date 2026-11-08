package dev.lumina.client.feature.visual;

import dev.lumina.client.Lumina;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.resources.Identifier;

/**
 * Frame blending motion blur. Uses a post chain with a persistent accumulation target; one chain
 * per strength level because post chain uniforms are fixed in their JSON.
 */
public final class MotionBlurModule extends Module {
	private final NumberSetting strength;

	public MotionBlurModule() {
		super("motion_blur", "Motion Blur", "Smooth frame blending motion blur", Category.VISUAL, Icons.WIND, false);
		strength = add(new NumberSetting("strength", "Strength", 5, 1, 10, 1));
	}

	public Identifier chain() {
		return Lumina.id("motion_blur_" + strength.getInt());
	}
}
