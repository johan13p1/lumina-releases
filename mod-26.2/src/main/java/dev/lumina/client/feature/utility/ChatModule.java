package dev.lumina.client.feature.utility;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class ChatModule extends Module {
	public final NumberSetting background;
	public final ModeSetting timestamps;
	private final ColorSetting timestampColor;
	public final BoolSetting stack;
	public final BoolSetting longHistory;
	public final BoolSetting keepOnLeave;

	public ChatModule() {
		super("chat", "Chat", "Background, timestamps, stacking and history", Category.UTILITY, Icons.MESSAGE, true);
		background = add(new NumberSetting("background", "Background Opacity", 0.35, 0, 1, 0.05));
		timestamps = add(new ModeSetting("timestamps", "Timestamps", "Off", "Off", "HH:mm", "HH:mm:ss", "h:mm a"));
		timestampColor = add(new ColorSetting("timestamp_color", "Timestamp Color", 0xFF8E8E96, false).visibleWhen(() -> !timestamps.is("Off")));
		stack = add(new BoolSetting("stack", "Stack Repeated Messages", true));
		longHistory = add(new BoolSetting("long_history", "Longer History (1000)", true));
		keepOnLeave = add(new BoolSetting("keep_on_leave", "Keep Chat When Switching Servers", false));
	}

	public int historySize() {
		return enabled() && longHistory.on() ? 1000 : 100;
	}

	public Component decorate(Component message) {
		if (!enabled() || timestamps.is("Off")) return message;
		String time = LocalTime.now().format(DateTimeFormatter.ofPattern(timestamps.get(), Locale.ROOT));
		MutableComponent prefix = Component.literal("[" + time + "] ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(timestampColor.color() & 0xFFFFFF)));
		return prefix.append(message);
	}
}
