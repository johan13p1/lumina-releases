package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.util.MacOS;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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
		long handle = Minecraft.getInstance().getWindow().handle();
		return switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(handle, key.getValue()) == GLFW.GLFW_PRESS;
			default -> false;
		};
	}

	public Component displayName() {
		InputConstants.Key key = key();
		if (key.equals(InputConstants.UNKNOWN)) return Component.literal("None");
		if (MacOS.IS_MAC) {
			// 26.2 (GLFW) names the modifier keys like on Windows; a Mac keyboard says Option and Command
			String mac = switch (key.getName()) {
				case "key.keyboard.left.alt" -> "Left Option";
				case "key.keyboard.right.alt" -> "Right Option";
				case "key.keyboard.left.win" -> "Left Command";
				case "key.keyboard.right.win" -> "Right Command";
				default -> null;
			};
			if (mac != null) return Component.literal(mac);
		}
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
