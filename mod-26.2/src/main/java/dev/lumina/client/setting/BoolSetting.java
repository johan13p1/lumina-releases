package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, String name, boolean defaultValue) {
		super(id, name, defaultValue);
	}

	public boolean on() {
		return value;
	}

	public void toggle() {
		set(!value);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) value = element.getAsBoolean();
	}
}
