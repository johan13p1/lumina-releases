package dev.lumina.client.feature.utility;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;

public final class TabEditorModule extends Module {
	public final ColorSetting background;
	public final ColorSetting rowBackground;
	public final BoolSetting numericPing;
	public final BoolSetting hideHeader;
	public final BoolSetting hideFooter;

	public TabEditorModule() {
		super("tab_editor", "Tab Editor", "Restyle the player list and show ping as numbers", Category.UTILITY, Icons.USERS, false);
		background = add(new ColorSetting("background", "Background", 0x800B0B0D));
		rowBackground = add(new ColorSetting("row_background", "Row Background", 0x14FFFFFF));
		numericPing = add(new BoolSetting("numeric_ping", "Ping As Number", true));
		hideHeader = add(new BoolSetting("hide_header", "Hide Header", false));
		hideFooter = add(new BoolSetting("hide_footer", "Hide Footer", false));
	}

	public static int pingColor(int ms) {
		if (ms < 0) return 0xFF8E8E96;
		if (ms < 80) return 0xFF7FD48A;
		if (ms < 150) return 0xFFE5D36B;
		if (ms < 300) return 0xFFE89A4F;
		return 0xFFE5484D;
	}
}
