package dev.lumina.client.feature.camera;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.NumberSetting;

public final class FovChangerModule extends Module {
	public final NumberSetting fov;
	public final BoolSetting staticFov;
	public final BoolSetting keepBow;

	public FovChangerModule() {
		super("fov_changer", "FOV Changer", "Any FOV you like and no FOV jumps when sprinting", Category.CAMERA, Icons.MONITOR, false);
		fov = add(new NumberSetting("fov", "Field Of View", 90, 30, 150, 1).suffix("°"));
		staticFov = add(new BoolSetting("static", "Disable Speed FOV Changes", true));
		keepBow = add(new BoolSetting("keep_bow", "Keep Bow Zoom", true).visibleWhen(staticFov::on));
	}
}
