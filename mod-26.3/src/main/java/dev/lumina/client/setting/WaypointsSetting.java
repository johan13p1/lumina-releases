package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Placeholder that makes the settings page show the waypoint list of the current world. */
public class WaypointsSetting extends Setting<String> {
	public WaypointsSetting() {
		super("waypoint_list", "Waypoints", "");
	}

	@Override
	public JsonElement save() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void load(JsonElement element) {
	}
}
