package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;

/** Brings back the enchantment glint on potions. */
public final class ShinyPotsModule extends Module {
	public final BoolSetting arrows;

	public ShinyPotsModule() {
		super("shiny_pots", "Shiny Pots", "Potions get the enchantment glint again", Category.VISUAL, Icons.FLASK_ROUND, false);
		arrows = add(new BoolSetting("tipped_arrows", "Tipped Arrows Too", false));
	}
}
