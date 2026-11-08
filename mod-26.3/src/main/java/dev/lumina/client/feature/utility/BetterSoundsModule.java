package dev.lumina.client.feature.utility;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

/** Per-sound-group volume control, e.g. louder hits or quieter rain. */
public final class BetterSoundsModule extends Module {
	private final NumberSetting hits;
	private final NumberSetting explosions;
	private final NumberSetting rain;
	private final NumberSetting footsteps;
	private final NumberSetting eating;
	private final NumberSetting cave;
	private final NumberSetting portals;
	private final NumberSetting anvil;
	private final NumberSetting ui;
	private final NumberSetting fireworks;
	private final NumberSetting villagers;

	public BetterSoundsModule() {
		super("better_sounds", "Better Sounds", "Volume control for specific sounds", Category.UTILITY, Icons.VOLUME, false);
		add(new HeaderSetting("Combat"));
		hits = add(new NumberSetting("hits", "Hit Sounds", 100, 0, 200, 5).suffix("%"));
		explosions = add(new NumberSetting("explosions", "Explosions", 100, 0, 200, 5).suffix("%"));
		fireworks = add(new NumberSetting("fireworks", "Fireworks", 100, 0, 200, 5).suffix("%"));
		add(new HeaderSetting("World"));
		rain = add(new NumberSetting("rain", "Rain & Thunder", 100, 0, 200, 5).suffix("%"));
		footsteps = add(new NumberSetting("footsteps", "Footsteps", 100, 0, 200, 5).suffix("%"));
		cave = add(new NumberSetting("cave", "Cave Ambience", 100, 0, 200, 5).suffix("%"));
		portals = add(new NumberSetting("portals", "Portals", 100, 0, 200, 5).suffix("%"));
		villagers = add(new NumberSetting("villagers", "Villagers", 100, 0, 200, 5).suffix("%"));
		add(new HeaderSetting("Other"));
		eating = add(new NumberSetting("eating", "Eating & Drinking", 100, 0, 200, 5).suffix("%"));
		anvil = add(new NumberSetting("anvil", "Anvils", 100, 0, 200, 5).suffix("%"));
		ui = add(new NumberSetting("ui", "Menu Clicks", 100, 0, 200, 5).suffix("%"));
	}

	/** Volume multiplier for a sound, 1 when unchanged. */
	public float multiplier(SoundInstance sound) {
		if (!enabled()) return 1;
		Identifier id = sound.getIdentifier();
		if (id == null) return 1;
		String p = id.getPath();
		NumberSetting s = null;
		if (p.contains(".attack.") || p.endsWith(".hurt") || p.contains("player.hurt") || p.contains(".hit")) s = hits;
		else if (p.contains("explode") || p.contains("explosion")) s = explosions;
		else if (p.startsWith("entity.firework")) s = fireworks;
		else if (p.startsWith("weather.")) s = rain;
		else if (p.endsWith(".step")) s = footsteps;
		else if (p.startsWith("ambient.cave") || p.startsWith("ambient.basalt") || p.startsWith("ambient.crimson") || p.startsWith("ambient.warped") || p.startsWith("ambient.soul") || p.startsWith("ambient.nether")) s = cave;
		else if (p.contains("portal")) s = portals;
		else if (p.startsWith("entity.villager") || p.startsWith("entity.wandering_trader")) s = villagers;
		else if (p.contains("eat") || p.contains("drink") || p.contains("burp")) s = eating;
		else if (p.startsWith("block.anvil")) s = anvil;
		else if (p.startsWith("ui.")) s = ui;
		return s == null ? 1 : s.getFloat() / 100f;
	}
}
