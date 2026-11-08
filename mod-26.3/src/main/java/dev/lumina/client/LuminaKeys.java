package dev.lumina.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Key mappings that also show up in Minecraft's controls screen. */
public final class LuminaKeys {
	private LuminaKeys() {
	}

	public static KeyMapping register(String name, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lumina." + name, InputConstants.Type.KEYBOARD, defaultKey, Lumina.KEY_CATEGORY));
	}
}
