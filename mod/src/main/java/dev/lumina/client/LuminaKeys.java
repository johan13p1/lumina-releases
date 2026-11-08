package dev.lumina.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Key mappings that also show up in Minecraft's controls screen. */
public final class LuminaKeys {
	private LuminaKeys() {
	}

	public static KeyMapping register(String name, int defaultKey) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lumina." + name, InputConstants.Type.KEYSYM, defaultKey, Lumina.KEY_CATEGORY));
	}
}
