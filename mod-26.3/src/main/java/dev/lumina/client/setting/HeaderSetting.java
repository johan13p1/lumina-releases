package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Not a real setting: a section title inside a module's settings page. */
public class HeaderSetting extends Setting<String> {
	public HeaderSetting(String title) {
		super("header_" + title.toLowerCase().replace(' ', '_'), title, title);
	}

	@Override
	public JsonElement save() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void load(JsonElement element) {
	}
}
