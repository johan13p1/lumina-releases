package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.nio.FloatBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLMouse;

/**
 * A key or mouse button. Can mirror a vanilla {@link KeyMapping}, in which case changes are also
 * visible in Minecraft's own controls menu.
 */
public class KeySetting extends Setting<String> {
	public static final String NONE = "key.keyboard.unknown";
	private final @Nullable KeyMapping mapping;
	/** True once the Lumina config had this key; only then it may change the key in options.txt. */
	private boolean saved;

	public KeySetting(String id, String name, String defaultKey) {
		super(id, name, defaultKey);
		this.mapping = null;
	}

	public KeySetting(String id, String name, KeyMapping mapping) {
		super(id, name, mapping.getDefaultKey().getName());
		this.mapping = mapping;
	}

	public @Nullable KeyMapping mapping() {
		return mapping;
	}

	public InputConstants.Key key() {
		if (mapping != null) return KeyMappingHelper.getBoundKeyOf(mapping);
		try {
			return InputConstants.getKey(value);
		} catch (RuntimeException e) {
			return InputConstants.UNKNOWN;
		}
	}

	public boolean isBound() {
		return !key().equals(InputConstants.UNKNOWN);
	}

	public void setKey(InputConstants.Key key) {
		if (mapping != null) {
			mapping.setKey(key);
			KeyMapping.resetMapping();
			Minecraft.getInstance().options.save();
		}
		set(key.getName());
	}

	/** Polls the physical key state. */
	public boolean isDown() {
		InputConstants.Key key = key();
		if (key.equals(InputConstants.UNKNOWN)) return false;
		return switch (key.getType()) {
			case KEYBOARD -> InputConstants.isKeyDown(key.getValue());
			// SDL mouse buttons start at 1; the state is a bit mask with bit (button - 1)
			case MOUSE -> key.getValue() > 0 && (SDLMouse.SDL_GetMouseState((FloatBuffer) null, (FloatBuffer) null) & (1 << (key.getValue() - 1))) != 0;
		};
	}

	public Component displayName() {
		InputConstants.Key key = key();
		if (key.equals(InputConstants.UNKNOWN)) return Component.literal("None");
		return key.getDisplayName();
	}

	@Override
	public void reset() {
		if (mapping != null) setKey(mapping.getDefaultKey());
		else super.reset();
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(mapping != null ? key().getName() : value);
	}

	@Override
	public void load(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) return;
		value = element.getAsString();
		saved = true;
		// Vanilla mappings are persisted in options.txt; the copy here is what other versions get (applySavedKey).
	}

	/**
	 * The Lumina config is shared by every Minecraft version, options.txt is not: a key changed in
	 * another version is put on the mapping once the game has read its options. True if it changed.
	 */
	public boolean applySavedKey() {
		if (mapping == null || !saved) return false;
		InputConstants.Key wanted;
		try {
			wanted = InputConstants.getKey(value);
		} catch (RuntimeException e) {
			return false;
		}
		if (wanted.equals(key())) return false;
		mapping.setKey(wanted);
		return true;
	}
}
