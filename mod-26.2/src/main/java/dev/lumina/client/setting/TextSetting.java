package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TextSetting extends Setting<String> {
	private final int maxLength;

	public TextSetting(String id, String name, String defaultValue, int maxLength) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String v) {
		if (v == null) return "";
		return v.length() > maxLength ? v.substring(0, maxLength) : v;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) value = sanitize(element.getAsString());
	}
}
