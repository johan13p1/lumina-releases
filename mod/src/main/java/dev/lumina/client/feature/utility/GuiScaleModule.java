package dev.lumina.client.feature.utility;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.ModeSetting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jspecify.annotations.Nullable;

/** Separate GUI scales for inventories and menus, independent of the HUD scale. */
public final class GuiScaleModule extends Module {
	private static final String[] SCALES = {"Default", "Auto", "1", "2", "3", "4", "5", "6"};
	private final ModeSetting inventory;
	private final ModeSetting menus;

	public GuiScaleModule() {
		super("gui_scale", "GUI Scale", "Different GUI scales for inventories and menus", Category.UTILITY, Icons.SCALING, false);
		inventory = add(new ModeSetting("inventory", "Inventory Scale", "Default", SCALES).onChange(v -> refresh()));
		menus = add(new ModeSetting("menus", "Menu Scale", "Default", SCALES).onChange(v -> refresh()));
	}

	@Override
	protected void onEnable() {
		refresh();
	}

	@Override
	protected void onDisable() {
		refresh();
	}

	private void refresh() {
		if (mc().getWindow() != null && mc().screen != null) mc().resizeDisplay();
	}

	/** Scale to use while {@code screen} is open, or -1 to keep the normal one. */
	public int scaleFor(@Nullable Screen screen, int normal, int auto) {
		if (!enabled() || screen == null || screen instanceof dev.lumina.client.gui.LuminaScreen) return -1;
		String mode = screen instanceof AbstractContainerScreen<?> ? inventory.get() : menus.get();
		return switch (mode) {
			case "Default" -> -1;
			case "Auto" -> auto;
			default -> Math.min(auto, Integer.parseInt(mode));
		};
	}
}
