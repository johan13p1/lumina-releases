package dev.lumina.client.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** A list of hotkeys that each send a chat message or command. */
public class MacroSetting extends Setting<List<MacroSetting.Macro>> {
	public static final class Macro {
		public final KeySetting key;
		public final TextSetting text;
		public boolean wasDown;

		public Macro(String keyName, String message) {
			this.key = new KeySetting("key", "Key", KeySetting.NONE);
			this.key.load(new com.google.gson.JsonPrimitive(keyName));
			this.text = new TextSetting("text", "Message", "", 256);
			this.text.set(message);
		}
	}

	public MacroSetting(String id, String name) {
		super(id, name, new ArrayList<>());
		this.value = new ArrayList<>();
	}

	public List<Macro> macros() {
		return value;
	}

	public Macro add() {
		Macro m = new Macro(KeySetting.NONE, "");
		value.add(m);
		SettingChanges.markDirty();
		return m;
	}

	public void remove(Macro macro) {
		value.remove(macro);
		SettingChanges.markDirty();
	}

	@Override
	public void reset() {
		value.clear();
		SettingChanges.markDirty();
	}

	@Override
	public JsonElement save() {
		JsonArray arr = new JsonArray();
		for (Macro m : value) {
			JsonObject o = new JsonObject();
			o.addProperty("key", m.key.get());
			o.addProperty("text", m.text.get());
			arr.add(o);
		}
		return arr;
	}

	@Override
	public void load(JsonElement element) {
		value.clear();
		if (element == null || !element.isJsonArray()) return;
		for (JsonElement e : element.getAsJsonArray()) {
			if (!e.isJsonObject()) continue;
			JsonObject o = e.getAsJsonObject();
			value.add(new Macro(o.has("key") ? o.get("key").getAsString() : KeySetting.NONE, o.has("text") ? o.get("text").getAsString() : ""));
		}
	}
}
