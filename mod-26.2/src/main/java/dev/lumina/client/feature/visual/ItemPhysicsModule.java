package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.NumberSetting;

/** Dropped items lie flat on the ground and tumble while falling. */
public final class ItemPhysicsModule extends Module {
	public final NumberSetting spinSpeed;
	public final BoolSetting blocksFlat;

	public ItemPhysicsModule() {
		super("item_physics", "Item Physics", "Dropped items lie flat and tumble realistically", Category.VISUAL, Icons.PACKAGE, false);
		spinSpeed = add(new NumberSetting("spin_speed", "Tumble Speed", 1, 0, 3, 0.1).suffix("x"));
		blocksFlat = add(new BoolSetting("blocks_flat", "Rotate Blocks Randomly", true));
	}
}
