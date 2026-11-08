package dev.lumina.client.feature.utility;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;

public final class InventoryModule extends Module {
	public final BoolSetting customBackground;
	public final ColorSetting backgroundColor;
	public final BoolSetting hideEffects;

	public InventoryModule() {
		super("inventory", "Inventory", "Cleaner inventory background and effects", Category.UTILITY, Icons.BACKPACK, false);
		customBackground = add(new BoolSetting("custom_background", "Custom Background", true));
		backgroundColor = add(new ColorSetting("background_color", "Background Color", 0x660A0A0C).visibleWhen(customBackground::on));
		hideEffects = add(new BoolSetting("hide_effects", "Hide Effects In Inventory", false));
	}
}
