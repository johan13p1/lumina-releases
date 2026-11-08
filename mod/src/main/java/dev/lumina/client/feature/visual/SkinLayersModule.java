package dev.lumina.client.feature.visual;

import dev.lumina.client.Lumina;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.NumberSetting;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Front-end for the bundled "3D Skin Layers" mod by tr7zw: renders the outer skin layer as real
 * 3D voxels. The settings are written into that mod's config through reflection, so a missing or
 * changed mod only disables this module instead of crashing.
 */
public final class SkinLayersModule extends Module {
	private final BoolSetting hat;
	private final BoolSetting jacket;
	private final BoolSetting sleeves;
	private final BoolSetting pants;
	private final BoolSetting skulls;
	private final NumberSetting lodDistance;
	private Object config;
	private boolean missing;

	public SkinLayersModule() {
		super("skin_layers_3d", "3D Skins", "Renders skin layers in 3D (3D Skin Layers)", Category.VISUAL, Icons.PERSON, true);
		add(new HeaderSetting("Layers"));
		hat = add(new BoolSetting("hat", "Hat", true).onChange(v -> apply()));
		jacket = add(new BoolSetting("jacket", "Jacket", true).onChange(v -> apply()));
		sleeves = add(new BoolSetting("sleeves", "Sleeves", true).onChange(v -> apply()));
		pants = add(new BoolSetting("pants", "Pants", true).onChange(v -> apply()));
		skulls = add(new BoolSetting("skulls", "Player Heads", true).onChange(v -> apply()));
		add(new HeaderSetting("Performance"));
		lodDistance = add(new NumberSetting("lod_distance", "3D Render Distance", 14, 4, 64, 1).suffix(" blocks").onChange(v -> apply()));
	}

	public boolean available() {
		return !missing && FabricLoader.getInstance().isModLoaded("skinlayers3d");
	}

	@Override
	public String description() {
		return available() ? super.description() : "Needs the 3D Skin Layers mod (installed by the launcher)";
	}

	@Override
	public void onReady() {
		apply();
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		apply();
	}

	private Object config() throws ReflectiveOperationException {
		if (config != null) return config;
		// 1.11.x keeps the config in a static field of the version independent base class
		Class<?> base = Class.forName("dev.tr7zw.skinlayers.versionless.ModBase");
		Field cfg = base.getField("config");
		config = cfg.get(null);
		return config;
	}

	private void apply() {
		if (!FabricLoader.getInstance().isModLoaded("skinlayers3d") || missing) return;
		try {
			Object cfg = config();
			if (cfg == null) return;
			boolean on = enabled();
			set(cfg, "enableHat", on && hat.on());
			set(cfg, "enableJacket", on && jacket.on());
			set(cfg, "enableLeftSleeve", on && sleeves.on());
			set(cfg, "enableRightSleeve", on && sleeves.on());
			set(cfg, "enableLeftPants", on && pants.on());
			set(cfg, "enableRightPants", on && pants.on());
			set(cfg, "enableSkulls", on && skulls.on());
			set(cfg, "enableSkullsItems", on && skulls.on());
			set(cfg, "renderDistanceLOD", lodDistance.getInt());
			try {
				Object instance = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase").getField("instance").get(null);
				if (instance != null) {
					Method write = instance.getClass().getMethod("writeConfig");
					write.invoke(instance);
				}
			} catch (ReflectiveOperationException ignored) {
				// not fatal: the values are still active for this session
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			missing = true;
			Lumina.LOG.warn("3D Skin Layers integration unavailable: {}", e.toString());
		}
	}

	private static void set(Object cfg, String name, Object value) {
		try {
			Field f = cfg.getClass().getField(name);
			if (f.getType() == boolean.class) f.setBoolean(cfg, (Boolean) value);
			else if (f.getType() == int.class) f.setInt(cfg, ((Number) value).intValue());
			else if (f.getType() == float.class) f.setFloat(cfg, ((Number) value).floatValue());
		} catch (ReflectiveOperationException ignored) {
		}
	}
}
