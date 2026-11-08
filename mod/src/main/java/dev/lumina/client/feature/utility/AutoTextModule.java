package dev.lumina.client.feature.utility;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.MacroSetting;
import dev.lumina.client.setting.NumberSetting;

/** Hotkeys that send a chat message or run a command. */
public final class AutoTextModule extends Module {
	private final MacroSetting macros;
	private final NumberSetting cooldown;
	private long lastSent;

	public AutoTextModule() {
		super("auto_text", "Auto Text Hotkey", "Send messages or commands with a key", Category.UTILITY, Icons.COMMAND, true);
		macros = add(new MacroSetting("macros", "Hotkeys"));
		cooldown = add(new NumberSetting("cooldown", "Cooldown", 1, 0, 10, 0.5).suffix("s"));
	}

	@Override
	public void tick() {
		if (!enabled() || mc().player == null) return;
		for (MacroSetting.Macro m : macros.macros()) {
			boolean down = mc().screen == null && m.key.isDown();
			if (down && !m.wasDown) send(m.text.get());
			m.wasDown = down;
		}
	}

	private void send(String text) {
		if (text == null || text.isBlank()) return;
		long now = System.currentTimeMillis();
		if (now - lastSent < cooldown.get() * 1000) return;
		lastSent = now;
		var conn = mc().player.connection;
		if (text.startsWith("/")) conn.sendCommand(text.substring(1));
		else conn.sendChat(text);
	}
}
