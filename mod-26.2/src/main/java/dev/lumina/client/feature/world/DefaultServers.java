package dev.lumina.client.feature.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumina.client.Lumina;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.NbtIo;

/**
 * A few big servers are in the multiplayer list from the start. Each one is offered once per game
 * folder (every Minecraft version has its own servers.dat): a server you delete stays deleted, and
 * one you already have, also under another name or as hypixel.net instead of mc.hypixel.net, is not
 * added twice. A new list gets them in this order, an existing one gets the missing ones at the end.
 */
public final class DefaultServers {
	private static final String[][] SERVERS = {
		{"Hypixel", "mc.hypixel.net"},
		{"DonutSMP", "donutsmp.net"},
		{"Cytooxien", "cytooxien.net"},
		{"HugoSMP", "hugosmp.net"},
		{"HugoPvP", "hugopvp.net"},
		{"CubeCraft", "play.cubecraft.net"},
		{"GommeHD", "gommehd.net"},
	};
	/** In Lumina's config: game folder name -> addresses already offered there. */
	private static final String CONFIG_KEY = "default_servers";

	private DefaultServers() {
	}

	public static void addMissing(Minecraft mc) {
		try {
			add(mc);
		} catch (Exception e) {
			Lumina.LOG.warn("Couldn't add the default servers", e);
		}
	}

	private static void add(Minecraft mc) throws IOException {
		JsonObject ui = Lumina.config().ui;
		JsonObject all = ui.has(CONFIG_KEY) && ui.get(CONFIG_KEY).isJsonObject() ? ui.getAsJsonObject(CONFIG_KEY) : new JsonObject();
		String folder = mc.gameDirectory.getName();
		JsonArray offered = all.has(folder) && all.get(folder).isJsonArray() ? all.getAsJsonArray(folder) : new JsonArray();
		Set<String> done = new HashSet<>();
		for (JsonElement e : offered) {
			if (e.isJsonPrimitive()) done.add(e.getAsString());
		}
		boolean pending = false;
		for (String[] s : SERVERS) pending |= !done.contains(key(s[1]));
		if (!pending) return;

		// Minecraft reads a broken servers.dat as an empty list; saving that would drop the user's servers
		Path file = mc.gameDirectory.toPath().resolve("servers.dat");
		if (Files.exists(file)) NbtIo.read(file);

		ServerList list = new ServerList(mc);
		list.load();
		Set<String> have = new HashSet<>();
		for (int i = 0; i < list.size(); i++) have.add(key(list.get(i).ip));
		boolean changed = false;
		for (String[] s : SERVERS) {
			String k = key(s[1]);
			if (done.contains(k)) continue;
			offered.add(k);
			if (have.add(k)) {
				// a server joined only through Quick Play sits hidden in the list as "Minecraft Server"
				ServerData hidden = list.unhide(s[1]);
				if (hidden != null) hidden.name = s[0];
				else list.add(new ServerData(s[0], s[1], ServerData.Type.OTHER), false);
				changed = true;
			}
		}
		if (changed) list.save();
		all.add(folder, offered);
		ui.add(CONFIG_KEY, all);
		Lumina.config().save();
	}

	/** hypixel.net, mc.hypixel.net and MC.Hypixel.net:25565 are the same server. */
	static String key(String address) {
		String a = address.trim().toLowerCase(Locale.ROOT);
		if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
		if (a.endsWith(".")) a = a.substring(0, a.length() - 1);
		for (String prefix : new String[] {"mc.", "play.", "www."}) {
			if (a.startsWith(prefix) && a.indexOf('.', prefix.length()) > 0) {
				a = a.substring(prefix.length());
				break;
			}
		}
		return a;
	}
}
