package dev.lumina.client.feature.utility;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.util.MacOS;

/** Global client options. Always on. */
public final class ClientSettingsModule extends Module {
	public final BoolSetting hideInDebug;
	public final BoolSetting customMainMenu;
	public final BoolSetting inventoryWatermark;
	public final BoolSetting sharpUi;
	public final BoolSetting retina;

	public ClientSettingsModule() {
		super("client", "Lumina Settings", "General client options", Category.UTILITY, Icons.SETTINGS, true);
		add(new HeaderSetting("Menus"));
		customMainMenu = add(new BoolSetting("custom_main_menu", "Lumina Main Menu", true));
		inventoryWatermark = add(new BoolSetting("inventory_watermark", "Lumina Mark In Inventories", true));
		add(new HeaderSetting("HUD"));
		hideInDebug = add(new BoolSetting("hide_in_debug", "Hide HUD With F3", true));
		add(new HeaderSetting("Mac").visibleWhen(() -> MacOS.IS_MAC));
		sharpUi = add(new BoolSetting("sharp_ui", "Sharp Menus & HUD (Needs Restart)", true)
			.describe("Draws menus, HUD and text at the full Retina resolution; the world keeps the faster resolution. Costs about 1 ms per frame, off gives the most FPS. Applies after a restart.")
			.visibleWhen(() -> MacOS.IS_MAC));
		retina = add(new BoolSetting("retina", "Retina Resolution", false)
			.describe("Renders the world at the full Retina resolution too. Sharper, but roughly halves the FPS. With Sharp Menus off it applies after a restart.")
			.visibleWhen(() -> MacOS.IS_MAC));
	}

	@Override
	public boolean canToggle() {
		return false;
	}

	@Override
	public void setEnabled(boolean on) {
		super.setEnabled(true);
	}
}
