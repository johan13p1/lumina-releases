package dev.lumina.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.lumina.client.gui.HudEditorScreen;
import dev.lumina.client.gui.LuminaScreen;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development helpers (only active with -Dlumina.dev=true). A comma separated script in
 * run/lumina-shots.txt walks through screens and saves screenshots, so UI work can be reviewed
 * without a human at the keyboard. Steps: title, menu, settings:<module>, world, cmd:<command>,
 * hud, editor, inventory, options, turn:<dx>/<dy>, press:<key>, waypoint:<name>,
 * reload, loaded, frames:<label>, wait:<ticks>, ingame, close, quit.
 */
final class DevHooks {
	private static final Deque<Runnable> steps = new ArrayDeque<>();
	private static int wait;
	private static BooleanSupplier until;
	private static final java.util.List<Long> frameTimes = new java.util.ArrayList<>();
	private static long lastFrame;

	private DevHooks() {
	}

	/** Records the time between rendered frames for the "frames" step. */
	static void frame() {
		long now = System.nanoTime();
		if (lastFrame != 0 && frameTimes.size() < 100_000) frameTimes.add(now - lastFrame);
		lastFrame = now;
	}

	static void start() {
		Minecraft mc = Minecraft.getInstance();
		// tests run next to whoever is working on this machine: never make a sound
		mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
		String script = System.getProperty("lumina.shots", "");
		File file = new File(mc.gameDirectory, "lumina-shots.txt");
		if (script.isEmpty() && file.isFile()) {
			try {
				script = java.nio.file.Files.readString(file.toPath()).trim();
			} catch (java.io.IOException ignored) {
			}
		}
		if (script.isEmpty()) return;
		steps.add(() -> wait = 60);
		// one step per line, or comma separated for short scripts
		String[] parts = script.contains("\n") ? script.split("\\r?\\n") : script.split(",");
		for (String raw : parts) {
			if (raw.isBlank()) continue;
			String s = raw.trim();
			String arg = s.contains(":") ? s.substring(s.indexOf(':') + 1) : "";
			String cmd = s.contains(":") ? s.substring(0, s.indexOf(':')) : s;
			switch (cmd) {
				case "title" -> {
					steps.add(() -> {
						mc.gui.setScreen(new TitleScreen());
						wait = 40;
					});
					steps.add(() -> shot("title"));
				}
				case "menu" -> {
					steps.add(() -> {
						mc.gui.setScreen(new LuminaScreen(null));
						wait = 30;
					});
					steps.add(() -> shot(mc.level == null ? "menu" : "menu-ingame"));
				}
				case "settings" -> {
					steps.add(() -> {
						var module = Lumina.modules().byId(arg.isEmpty() ? "fps" : arg);
						mc.gui.setScreen(new LuminaScreen(null).openModule(module));
						wait = 30;
					});
					steps.add(() -> shot("settings-" + (arg.isEmpty() ? "fps" : arg)));
				}
				case "world" -> steps.add(() -> {
					openTestWorld(mc);
					until = () -> mc.level != null && mc.player != null && mc.gui.screen() == null;
					wait = 100;
				});
				case "serverpack" -> steps.add(() -> {
					// serverpack:<zip> loads a pack the way a server sends one (for testing what happens on quit)
					var packs = mc.getDownloadedPackSource();
					packs.allowServerPacks();
					packs.pushLocalPack(java.util.UUID.randomUUID(), java.nio.file.Path.of(arg));
					wait = 100;
				});
				case "ingame" -> steps.add(() -> {
					// waits for a world, e.g. a server joined with --quickPlayMultiplayer
					until = () -> mc.level != null && mc.player != null && mc.gui.screen() == null;
					wait = 40;
				});
				case "cmd" -> steps.add(() -> {
					if (mc.player != null) mc.player.connection.sendCommand(arg);
					wait = 10;
				});
				case "hud" -> steps.add(() -> shot("hud"));
				case "inventory" -> steps.add(() -> {
					if (mc.player != null) mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
					wait = 20;
				});
				case "options" -> steps.add(() -> {
					mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(mc.gui.screen(), mc.options, mc.level != null));
					wait = 20;
				});
				case "packs" -> steps.add(() -> {
					mc.gui.setScreen(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(mc.getResourcePackRepository(), repo -> {}, mc.getResourcePackDirectory(), net.minecraft.network.chat.Component.translatable("resourcePack.title")));
					wait = 40;
				});
				case "press" -> steps.add(() -> {
					// press:<mapping> clicks a key mapping once, like a real key press (uses its default key)
					net.minecraft.client.KeyMapping mapping = net.minecraft.client.KeyMapping.get(arg);
					if (mapping != null) net.minecraft.client.KeyMapping.click(mapping.getDefaultKey());
					wait = 10;
				});
				case "waypoint" -> steps.add(() -> {
					var wps = Lumina.modules().get(dev.lumina.client.feature.world.WaypointsModule.class);
					if (wps != null && mc.player != null) {
						var wp = wps.addAtPlayer();
						if (!arg.isEmpty()) wp.name = arg;
						wps.store.save();
					}
				});
				case "cosmetics" -> {
					steps.add(() -> {
						Lumina.config().ui.addProperty("menu_category", "COSMETICS");
						mc.gui.setScreen(new LuminaScreen(null));
						wait = 40;
					});
					steps.add(() -> shot("cosmetics"));
				}
				case "page" -> {
					// page:<MODS|COSMETICS|SKINS>[/effects][@ticks] opens the Lumina menu on that page (then waits 40 ticks or as given)
					int pageWait = arg.contains("@") ? Integer.parseInt(arg.substring(arg.indexOf("@") + 1)) : 40;
					String[] p = (arg.contains("@") ? arg.substring(0, arg.indexOf("@")) : arg).split("/");
					steps.add(() -> {
						Lumina.config().ui.addProperty("menu_category", p[0]);
						if (p.length > 1) Lumina.config().ui.addProperty("cosmetics_tab", p[1]);
						mc.gui.setScreen(new LuminaScreen(null));
						wait = pageWait;
					});
				}
				case "drop" -> steps.add(() -> {
					// drop:<file>[|<file>...] simulates dragging files onto the window
					java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>();
					for (String f : arg.split("\\|")) files.add(java.nio.file.Path.of(f));
					if (mc.gui.screen() != null) mc.gui.screen().onFilesDrop(files);
					wait = 20;
				});
				case "click" -> steps.add(() -> {
					// click:<x>/<y>[/<button>] in window pixels, as read off a screenshot: a real click on the open screen
					String[] xy = arg.split("/");
					double scale = mc.getWindow().getGuiScale();
					var event = new net.minecraft.client.input.MouseButtonEvent(Double.parseDouble(xy[0]) / scale, Double.parseDouble(xy[1]) / scale,
						new net.minecraft.client.input.MouseButtonInfo(xy.length > 2 ? Integer.parseInt(xy[2]) : 0, 0));
					if (mc.gui.screen() != null) {
						mc.gui.screen().mouseClicked(event, false);
						if (mc.gui.screen() != null) mc.gui.screen().mouseReleased(event);
					}
					wait = 10;
				});
				case "reload" -> steps.add(() -> {
					mc.reloadResourcePacks();
					wait = Integer.parseInt(arg.isEmpty() ? "10" : arg);
				});
				case "loaded" -> steps.add(() -> {
					until = () -> mc.gui.overlay() == null;
					wait = 20;
				});
				case "frames" -> steps.add(() -> {
					// frame times since the last "frames" step: average, 99th percentile and worst frame
					long[] t = frameTimes.stream().mapToLong(Long::longValue).sorted().toArray();
					frameTimes.clear();
					if (t.length == 0) return;
					double avg = java.util.Arrays.stream(t).average().orElse(0) / 1e6;
					Lumina.LOG.info("[dev] frames {}: n={} avg={}ms p99={}ms max={}ms fps={}", arg, t.length, String.format("%.2f", avg),
						String.format("%.2f", t[(int) (t.length * 0.99)] / 1e6), String.format("%.2f", t[t.length - 1] / 1e6), mc.getFps());
				});
				case "turn" -> steps.add(() -> {
					String[] d = arg.split("/");
					var freeLook = Lumina.modules().get(dev.lumina.client.feature.camera.FreeLookModule.class);
					if (freeLook != null) freeLook.turn(Double.parseDouble(d[0]), Double.parseDouble(d[1]));
				});
				case "editor" -> {
					steps.add(() -> {
						mc.gui.setScreen(new HudEditorScreen(null));
						wait = 30;
					});
					steps.add(() -> shot("editor"));
				}
				case "wait" -> steps.add(() -> wait = Integer.parseInt(arg));
				case "hold", "release" -> steps.add(() -> {
					net.minecraft.client.KeyMapping mapping = net.minecraft.client.KeyMapping.get(arg);
					if (mapping != null) mapping.setDown(cmd.equals("hold"));
					else Lumina.LOG.warn("[dev] no key mapping {}", arg);
					wait = 5;
				});
				case "look" -> steps.add(() -> {
					String[] yp = arg.split("/");
					if (mc.player != null) {
						mc.player.setYRot(Float.parseFloat(yp[0]));
						mc.player.setXRot(Float.parseFloat(yp[1]));
					}
				});
				case "dump" -> steps.add(() -> {
					if (mc.level == null) return;
					var cam = mc.getCameraEntity();
					if (cam != null) Lumina.LOG.info("[dev] camera {} pos={} rot={}/{}", cam.getClass().getSimpleName(), cam.position(), cam.getYRot(), cam.getXRot());
					for (var e : mc.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.item.ItemEntity item) {
							Lumina.LOG.info("[dev] entity {} {} pos={} ground={} motion={}", item.getId(), item.getItem(), item.position(), item.onGround(), item.getDeltaMovement());
						}
					}
					if (mc.player != null) {
						Lumina.LOG.info("[dev] player pos={} rot={}/{}", mc.player.position(), mc.player.getYRot(), mc.player.getXRot());
						for (var e : mc.level.entitiesForRendering()) {
							if (e != mc.player && e.distanceTo(mc.player) < 10) Lumina.LOG.info("[dev] near {} {} {}", e.getId(), e.getClass().getSimpleName(), e.position());
						}
					}
				});
				case "prop" -> steps.add(() -> {
					String[] kv = arg.split("=", 2);
					System.setProperty(kv[0], kv.length > 1 ? kv[1] : "");
				});
				case "hidegui" -> steps.add(() -> { if (mc.gui.hud.isHidden() != !arg.equals("off")) mc.gui.hud.toggle(); });
				case "f3" -> steps.add(() -> {
					mc.debugEntries.toggleDebugOverlay();
					wait = 10;
				});
				case "fly" -> steps.add(() -> {
					if (mc.player != null) {
						mc.player.getAbilities().flying = true;
						mc.player.onUpdateAbilities();
					}
				});
				case "view" -> steps.add(() -> mc.options.setCameraType(net.minecraft.client.CameraType.values()[Integer.parseInt(arg)]));
				case "slot" -> steps.add(() -> {
					if (mc.player != null) mc.player.getInventory().setSelectedSlot(Integer.parseInt(arg));
				});
				case "shot" -> steps.add(() -> shot(arg));
				case "burst" -> {
					// burst:<name>/<count>/<ticks>: a screenshot every few ticks, to see motion
					String[] b = arg.split("/");
					for (int k = 0; k < Integer.parseInt(b[1]); k++) {
						String name = b[0] + "-" + k;
						steps.add(() -> {
							shot(name);
							wait = Integer.parseInt(b[2]) - 1;
						});
					}
				}
				case "set" -> steps.add(() -> {
					// set:<module>.<setting>=<value>
					String[] kv = arg.split("=", 2);
					String[] ms = kv[0].split("\\.", 2);
					var module = Lumina.modules().byId(ms[0]);
					if (module == null) return;
					for (var setting : module.settings()) {
						if (setting.id().equals(ms[1])) setting.load(com.google.gson.JsonParser.parseString(kv[1]));
					}
				});
				case "key" -> steps.add(() -> {
					// key:<module>.<setting>=<key name>, e.g. key:freelook.fly_key=key.keyboard.b, like picking it in the menu
					String[] kv = arg.split("=", 2);
					String[] ms = kv[0].split("\\.", 2);
					var module = Lumina.modules().byId(ms[0]);
					if (module == null) return;
					for (var setting : module.settings()) {
						if (setting.id().equals(ms[1]) && setting instanceof dev.lumina.client.setting.KeySetting k) {
							k.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(kv[1]));
						}
					}
				});
				case "on", "off" -> steps.add(() -> {
					var module = Lumina.modules().byId(arg);
					if (module != null) module.setEnabled(cmd.equals("on"));
				});
				case "close" -> steps.add(() -> {
					mc.gui.setScreen(null);
					wait = 20;
				});
				case "quit" -> steps.add(mc::stop);
				default -> Lumina.LOG.warn("[dev] unknown step {}", s);
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (until != null) {
				if (!until.getAsBoolean()) return;
				until = null;
			}
			if (wait > 0) {
				wait--;
				return;
			}
			Runnable next = steps.pollFirst();
			if (next != null) {
				try {
					next.run();
				} catch (RuntimeException e) {
					Lumina.LOG.error("[dev] step failed", e);
				}
			}
		});
	}

	private static void openTestWorld(Minecraft mc) {
		String name = "lumina-test";
		if (mc.getLevelSource().levelExists(name)) {
			mc.createWorldOpenFlows().openWorld(name, () -> mc.gui.setScreen(new TitleScreen()));
			return;
		}
		WorldDataConfiguration data = WorldDataConfiguration.DEFAULT;
		LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, data);
		mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20251209L, true, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
	}

	private static void shot(String name) {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.gameRenderer.mainRenderTarget();
		Screenshot.grab(mc.gameDirectory, "lumina-" + name + ".png", target, 1, msg -> Lumina.LOG.info("[dev] screenshot {}: {}", name, msg.getString()));
		wait = 10;
	}
}
