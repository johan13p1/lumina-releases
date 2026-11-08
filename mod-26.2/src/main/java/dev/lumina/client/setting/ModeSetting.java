package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;

public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String id, String name, String defaultValue, String... modes) {
		super(id, name, defaultValue);
		this.modes = List.of(modes);
	}

	public List<String> modes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equals(mode);
	}

	public int index() {
		return Math.max(0, modes.indexOf(value));
	}

	public void cycle(int dir) {
		int i = (index() + dir + modes.size()) % modes.size();
		set(modes.get(i));
	}

	@Override
	protected String sanitize(String v) {
		return modes.contains(v) ? v : defaultValue();
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
