package dev.lumina.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumina.client.setting.KeySetting;
import dev.lumina.client.setting.Setting;
import dev.lumina.client.setting.SettingChanges;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final String icon;
	private final boolean defaultEnabled;
	private boolean enabled;
	private final List<Setting<?>> settings = new ArrayList<>();
	/** Optional key that toggles the module; added to the settings list by {@link #withToggleKey()}. */
	public final KeySetting toggleKey = new KeySetting("toggle_key", "Toggle Keybind", KeySetting.NONE);
	private boolean hasToggleKey;
	boolean toggleKeyWasDown;

	protected Module(String id, String name, String description, Category category, String icon, boolean defaultEnabled) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
	}

	protected static Minecraft mc() {
		return Minecraft.getInstance();
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	protected void withToggleKey() {
		if (!hasToggleKey) {
			hasToggleKey = true;
			settings.add(toggleKey);
		}
	}

	public boolean hasToggleKey() {
		return hasToggleKey;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public String icon() {
		return icon;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean enabled() {
		return enabled;
	}

	/** False keeps the module out of the menu's module grid (it has its own page instead). */
	public boolean listed() {
		return true;
	}

	/** Whether the module can be switched off at all (e.g. pure option holders could say no). */
	public boolean canToggle() {
		return true;
	}

	public void setEnabled(boolean on) {
		if (this.enabled == on) return;
		this.enabled = on;
		SettingChanges.markDirty();
		try {
			if (on) onEnable();
			else onDisable();
		} catch (RuntimeException e) {
			dev.lumina.client.Lumina.LOG.error("Error while toggling {}", id, e);
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick, enabled or not. */
	public void tick() {
	}

	/** Called once after the config was loaded and Minecraft is ready. */
	public void onReady() {
	}

	/** Height of a live preview shown above the module's settings; 0 means none. */
	public float previewHeight() {
		return 0;
	}

	public void renderPreview(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
	}

	public void resetSettings() {
		for (Setting<?> s : settings) s.reset();
	}

	public JsonObject save() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		JsonObject s = new JsonObject();
		for (Setting<?> setting : settings) {
			JsonElement e = setting.save();
			if (!e.isJsonNull()) s.add(setting.id(), e);
		}
		o.add("settings", s);
		return o;
	}

	public void load(JsonObject o) {
		if (o.has("enabled")) this.enabled = o.get("enabled").getAsBoolean();
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject s = o.getAsJsonObject("settings");
			for (Setting<?> setting : settings) {
				if (s.has(setting.id())) {
					try {
						setting.load(s.get(setting.id()));
					} catch (RuntimeException ignored) {
					}
				}
			}
		}
	}

	public boolean defaultEnabled() {
		return defaultEnabled;
	}
}
