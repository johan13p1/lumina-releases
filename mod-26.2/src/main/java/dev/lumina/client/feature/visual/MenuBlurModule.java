package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.NumberSetting;

/** Background blur for menus, including in-game screens like the inventory. */
public final class MenuBlurModule extends Module {
	public final NumberSetting radius;
	public final BoolSetting inGameScreens;
	public final BoolSetting luminaMenu;
	public final BoolSetting darken;

	public MenuBlurModule() {
		super("menu_blur", "Menu Blur", "Blur the game behind menus and inventories", Category.VISUAL, Icons.APP_WINDOW, true);
		radius = add(new NumberSetting("radius", "Blur Strength", 5, 1, 10, 1).onChange(v -> apply()));
		inGameScreens = add(new BoolSetting("in_game", "Blur Inventories", true));
		luminaMenu = add(new BoolSetting("lumina_menu", "Blur Behind Lumina Menu", true));
		darken = add(new BoolSetting("darken", "Darken Background", true));
	}

	@Override
	public void onReady() {
		radius.load(new com.google.gson.JsonPrimitive(Math.max(1, mc().options.menuBackgroundBlurriness().get())));
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		if (mc().options != null) mc().options.menuBackgroundBlurriness().set(0);
	}

	private void apply() {
		if (mc().options == null || !enabled()) return;
		mc().options.menuBackgroundBlurriness().set(radius.getInt());
	}
}
