package dev.lumina.client.hud;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.hud.BossBarModule;
import dev.lumina.client.feature.hud.PotionEffectsModule;
import dev.lumina.client.feature.hud.ScoreboardModule;
import dev.lumina.client.feature.visual.AttackIndicatorModule;
import dev.lumina.client.feature.visual.CrosshairModule;
import dev.lumina.client.feature.visual.DamageTintModule;
import dev.lumina.client.feature.world.WaypointsModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.level.GameType;

/** Swaps vanilla HUD parts (scoreboard, effects, boss bar, crosshair) for Lumina's versions. */
public final class VanillaHudReplacements {
	private VanillaHudReplacements() {
	}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (g, tick) -> {
			ScoreboardModule m = Lumina.modules().get(ScoreboardModule.class);
			if (m == null || !m.enabled()) vanilla.render(g, tick);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (g, tick) -> {
			PotionEffectsModule m = Lumina.modules().get(PotionEffectsModule.class);
			if (m == null || !m.enabled() || !m.hideVanilla.on()) vanilla.render(g, tick);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, vanilla -> (g, tick) -> {
			BossBarModule m = Lumina.modules().get(BossBarModule.class);
			if (m == null || !m.enabled()) vanilla.render(g, tick);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, tick) -> {
			Minecraft mc = Minecraft.getInstance();
			CrosshairModule crosshair = Lumina.modules().get(CrosshairModule.class);
			AttackIndicatorModule indicator = Lumina.modules().get(AttackIndicatorModule.class);
			boolean custom = crosshair != null && crosshair.enabled();
			if (!custom) {
				vanilla.render(g, tick);
			} else if (shouldDrawCrosshair(mc, crosshair)) {
				g.nextStratum();
				crosshair.render(g);
			}
			if (indicator != null && indicator.enabled() && (mc.options.getCameraType().isFirstPerson() || custom && crosshair.thirdPerson.on())) {
				indicator.render(g);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Lumina.id("damage_tint"), (g, tick) -> {
			DamageTintModule m = Lumina.modules().get(DamageTintModule.class);
			if (m != null && m.enabled()) m.render(g);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Lumina.id("waypoint_labels"), (g, tick) -> {
			WaypointsModule m = Lumina.modules().get(WaypointsModule.class);
			if (m != null && m.enabled()) m.renderLabels(g, tick.getGameTimeDeltaPartialTick(false));
		});
	}

	private static boolean shouldDrawCrosshair(Minecraft mc, CrosshairModule m) {
		if (mc.player == null || mc.gameMode == null) return false;
		if (!mc.options.getCameraType().isFirstPerson() && !m.thirdPerson.on()) return false;
		if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return false;
		if (m.hideInScreens.on() && mc.screen != null) return false;
		return !mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR);
	}
}
