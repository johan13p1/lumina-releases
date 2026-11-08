package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.feature.hud.SpotifyModule;
import dev.lumina.client.render.Theme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Draws nothing but a hint: it only frees the mouse, so the Spotify widget's buttons can be clicked. */
public final class SpotifyScreen extends Screen {
	private final SpotifyModule module;
	private final KeyMapping key;

	public SpotifyScreen(SpotifyModule module, KeyMapping key) {
		super(Component.literal("Spotify"));
		this.module = module;
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		McUI.textCenter(g, "Click the Spotify buttons  ·  " + key.getTranslatedKeyMessage().getString() + " or Esc closes", width / 2f, 12, Theme.alpha(McUI.WHITE, 1));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (module.click(event.x(), event.y())) McUI.click();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE || key.matches(event)) onClose();
		return true;
	}
}
