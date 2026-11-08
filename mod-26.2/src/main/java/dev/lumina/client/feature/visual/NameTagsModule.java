package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.NumberSetting;

public final class NameTagsModule extends Module {
	public final BoolSetting showOwn;
	public final NumberSetting background;
	public final BoolSetting shadow;
	public final NumberSetting scale;

	public NameTagsModule() {
		super("name_tags", "Name Tags", "Style name tags and see your own in F5", Category.VISUAL, Icons.TAG, false);
		showOwn = add(new BoolSetting("show_own", "Show Own Name Tag", true));
		background = add(new NumberSetting("background", "Background Opacity", 0.25, 0, 1, 0.05));
		shadow = add(new BoolSetting("shadow", "Text Shadow", false));
		scale = add(new NumberSetting("scale", "Scale", 1, 0.5, 2, 0.05).suffix("x"));
	}
}
