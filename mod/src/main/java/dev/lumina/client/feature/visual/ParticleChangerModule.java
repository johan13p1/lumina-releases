package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

public final class ParticleChangerModule extends Module {
	public final NumberSetting multiplier;
	public final BoolSetting alwaysSharpness;
	public final BoolSetting alwaysCrits;
	private final BoolSetting hideExplosions;
	public final BoolSetting hideBlockBreak;
	private final BoolSetting hidePotions;
	private final BoolSetting hideWeather;
	private final BoolSetting hideTotem;
	private final BoolSetting hideSmoke;

	public ParticleChangerModule() {
		super("particle_changer", "Particle Changer", "More hit particles, fewer annoying ones", Category.VISUAL, Icons.SPARKLE, false);
		add(new HeaderSetting("Hits"));
		multiplier = add(new NumberSetting("multiplier", "Hit Particle Multiplier", 2, 1, 10, 1).suffix("x"));
		alwaysSharpness = add(new BoolSetting("always_sharpness", "Always Show Sharpness Particles", false));
		alwaysCrits = add(new BoolSetting("always_crits", "Always Show Critical Particles", false));
		add(new HeaderSetting("Hide"));
		hideExplosions = add(new BoolSetting("hide_explosions", "Explosions", false));
		hideBlockBreak = add(new BoolSetting("hide_block_break", "Block Break", false));
		hidePotions = add(new BoolSetting("hide_potions", "Potion Swirls", false));
		hideWeather = add(new BoolSetting("hide_weather", "Rain Splashes", false));
		hideTotem = add(new BoolSetting("hide_totem", "Totem Of Undying", false));
		hideSmoke = add(new BoolSetting("hide_smoke", "Campfire Smoke", false));
	}

	public boolean hidden(ParticleOptions options) {
		if (!enabled()) return false;
		ParticleType<?> t = options.getType();
		if (hideExplosions.on() && (t == ParticleTypes.EXPLOSION || t == ParticleTypes.EXPLOSION_EMITTER)) return true;
		if (hidePotions.on() && (t == ParticleTypes.ENTITY_EFFECT || t == ParticleTypes.EFFECT || t == ParticleTypes.INSTANT_EFFECT)) return true;
		if (hideWeather.on() && (t == ParticleTypes.RAIN || t == ParticleTypes.SPLASH)) return true;
		if (hideTotem.on() && t == ParticleTypes.TOTEM_OF_UNDYING) return true;
		return hideSmoke.on() && (t == ParticleTypes.CAMPFIRE_COSY_SMOKE || t == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE);
	}
}
