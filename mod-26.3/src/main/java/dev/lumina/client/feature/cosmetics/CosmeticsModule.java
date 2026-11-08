package dev.lumina.client.feature.cosmetics;

import dev.lumina.client.module.Category;
import dev.lumina.client.online.LuminaOnline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jspecify.annotations.Nullable;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.NumberSetting;
import dev.lumina.client.setting.TextSetting;

/**
 * Your cape and particle effect. Other Lumina players see them too (through LuminaOnline). Picked
 * on the Cosmetics page of the Lumina menu; this module holds the choice and the fine tuning.
 */
public final class CosmeticsModule extends Module {
	public final TextSetting cape;
	public final TextSetting effect;
	public final BoolSetting capePhysics;
	public final ColorSetting effectColor;
	public final NumberSetting density;
	public final BoolSetting firstPerson;
	public final CapeLibrary capes = new CapeLibrary();
	public final ParticleEffects particles = new ParticleEffects(this);

	public CosmeticsModule() {
		super("cosmetics", "Cosmetics", "Your cape and particle effects", Category.VISUAL, Icons.SPARKLES, true);
		cape = add(new TextSetting("cape", "Cape", "", 128).visibleWhen(() -> false));
		effect = add(new TextSetting("effect", "Effect", "", 64).visibleWhen(() -> false));
		add(new HeaderSetting("Cape"));
		// with Wavey Capes installed, it moves the capes and has its own settings
		capePhysics = add(new BoolSetting("cape_physics", "Waving Cape", true).visibleWhen(() -> !WaveyCapesBridge.active()));
		add(new HeaderSetting("Particles"));
		effectColor = add(new ColorSetting("effect_color", "Effect Color", 0xFFB9C6FF, false));
		density = add(new NumberSetting("density", "Amount", 1, 0.25, 2, 0.05).suffix("x"));
		firstPerson = add(new BoolSetting("first_person", "Show In First Person", false));
	}

	/** Cosmetics have their own page in the menu instead of a card in the module grid. */
	@Override
	public boolean listed() {
		return false;
	}

	/** The equipped cape, or null. */
	public CapeLibrary.Entry equippedCape() {
		if (!enabled() || cape.get().isEmpty()) return null;
		return capes.find(cape.get());
	}

	/**
	 * The cape to draw on the player with this entity id: yours, or another Lumina player's if it is
	 * one that ships with Lumina (so this client has the file). Null for none.
	 */
	public CapeLibrary.@Nullable Entry capeOf(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		if (entityId == mc.player.getId()) return equippedCape();
		if (mc.level == null || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer other)) return null;
		LuminaOnline.Remote remote = LuminaOnline.remote(other.getUUID());
		if (remote == null || remote.cape() == null) return null;
		CapeLibrary.Entry entry = capes.find(remote.cape());
		return entry != null && entry.bundled() ? entry : null;
	}

	@Override
	public void onReady() {
		capes.reload();
		// ready before anyone looks: the Cosmetics page and other players' capes show at once
		capes.preload();
		particles.ensureAtlasForGui();
	}

	@Override
	public void tick() {
		capes.uploadDecoded();
		particles.tick();
	}
}
