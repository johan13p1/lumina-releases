package dev.lumina.client.feature;

import dev.lumina.client.feature.camera.FovChangerModule;
import dev.lumina.client.feature.cosmetics.CosmeticsModule;
import dev.lumina.client.feature.camera.FreeLookModule;
import dev.lumina.client.feature.camera.ZoomModule;
import dev.lumina.client.feature.hud.BossBarModule;
import dev.lumina.client.feature.hud.ClockModule;
import dev.lumina.client.feature.hud.CoordinatesModule;
import dev.lumina.client.feature.hud.CpsModule;
import dev.lumina.client.feature.hud.FpsModule;
import dev.lumina.client.feature.hud.KeystrokesModule;
import dev.lumina.client.feature.hud.MemoryModule;
import dev.lumina.client.feature.hud.MinimapModule;
import dev.lumina.client.feature.hud.MomentumModule;
import dev.lumina.client.feature.hud.PingModule;
import dev.lumina.client.feature.hud.PotionEffectsModule;
import dev.lumina.client.feature.hud.ScoreboardModule;
import dev.lumina.client.feature.hud.SpotifyModule;
import dev.lumina.client.feature.hud.StopwatchModule;
import dev.lumina.client.feature.utility.AutoTextModule;
import dev.lumina.client.feature.utility.BetterSoundsModule;
import dev.lumina.client.feature.utility.ChatModule;
import dev.lumina.client.feature.utility.ClientSettingsModule;
import dev.lumina.client.feature.utility.GuiScaleModule;
import dev.lumina.client.feature.utility.InventoryModule;
import dev.lumina.client.feature.utility.TabEditorModule;
import dev.lumina.client.feature.visual.AttackIndicatorModule;
import dev.lumina.client.feature.visual.BlockOutlineModule;
import dev.lumina.client.feature.visual.ChunkBordersModule;
import dev.lumina.client.feature.visual.CrosshairModule;
import dev.lumina.client.feature.visual.DamageTintModule;
import dev.lumina.client.feature.visual.GlintColorizerModule;
import dev.lumina.client.feature.visual.HitColorModule;
import dev.lumina.client.feature.visual.FullbrightModule;
import dev.lumina.client.feature.visual.HitboxModule;
import dev.lumina.client.feature.visual.ItemPhysicsModule;
import dev.lumina.client.feature.visual.MenuBlurModule;
import dev.lumina.client.feature.visual.MotionBlurModule;
import dev.lumina.client.feature.visual.NameTagsModule;
import dev.lumina.client.feature.visual.ParticleChangerModule;
import dev.lumina.client.feature.visual.ShinyPotsModule;
import dev.lumina.client.feature.visual.SkinLayersModule;
import dev.lumina.client.feature.world.WaypointsModule;
import dev.lumina.client.feature.world.WeatherChangerModule;
import dev.lumina.client.module.ModuleManager;

public final class Modules {
	private Modules() {
	}

	public static void registerAll(ModuleManager m) {
		// HUD
		m.register(new FpsModule());
		m.register(new CpsModule());
		m.register(new CoordinatesModule());
		m.register(new KeystrokesModule());
		m.register(new PotionEffectsModule());
		m.register(new PingModule());
		m.register(new ScoreboardModule());
		m.register(new BossBarModule());
		m.register(new MinimapModule());
		m.register(new ClockModule());
		m.register(new StopwatchModule());
		m.register(new MemoryModule());
		m.register(new MomentumModule());
		m.register(new SpotifyModule());

		// Visual
		m.register(new CrosshairModule());
		m.register(new AttackIndicatorModule());
		m.register(new HitColorModule());
		m.register(new DamageTintModule());
		m.register(new BlockOutlineModule());
		m.register(new GlintColorizerModule());
		m.register(new ShinyPotsModule());
		m.register(new ItemPhysicsModule());
		m.register(new SkinLayersModule());
		m.register(new NameTagsModule());
		m.register(new ParticleChangerModule());
		m.register(new MotionBlurModule());
		m.register(new MenuBlurModule());
		m.register(new HitboxModule());
		m.register(new FullbrightModule());
		m.register(new CosmeticsModule());

		// Camera
		m.register(new ZoomModule());
		m.register(new FreeLookModule());
		m.register(new FovChangerModule());

		// World
		m.register(new WaypointsModule());
		m.register(new WeatherChangerModule());
		m.register(new ChunkBordersModule());

		// Utility
		m.register(new ChatModule());
		m.register(new TabEditorModule());
		m.register(new AutoTextModule());
		m.register(new BetterSoundsModule());
		m.register(new InventoryModule());
		m.register(new GuiScaleModule());
		m.register(new ClientSettingsModule());
	}
}
