package dev.lumina.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.util.MacOS;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Minimal single line text field. */
public final class TextInput {
	private String text = "";
	private int cursor;
	private boolean focused;
	private final int maxLength;
	private final String placeholder;
	private final Consumer<String> onChange;
	private long blinkStart = System.currentTimeMillis();
	private float scroll;

	public TextInput(String initial, int maxLength, String placeholder, Consumer<String> onChange) {
		this.text = initial == null ? "" : initial;
		this.cursor = this.text.length();
		this.maxLength = maxLength;
		this.placeholder = placeholder;
		this.onChange = onChange;
	}

	public String text() {
		return text;
	}

	public void setText(String s) {
		text = s == null ? "" : s;
		cursor = Math.min(cursor, text.length());
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean f) {
		if (f && !focused) blinkStart = System.currentTimeMillis();
		focused = f;
		if (f) cursor = text.length();
	}

	/** Drawn with Minecraft's text field and font. */
	void renderVanilla(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
		McUI.textField(g, x, y, w, h, focused, alpha);
		float pad = 4;
		float innerW = w - pad * 2;
		float cursorX = McUI.width(text.substring(0, cursor));
		if (cursorX - scroll > innerW) scroll = cursorX - innerW;
		if (cursorX - scroll < 0) scroll = cursorX;
		float cy = y + h / 2f;
		McUI.scissor(g, x + pad - 1, y, w - pad * 2 + 2, h);
		if (text.isEmpty() && !focused) McUI.text(g, placeholder, x + pad, cy, Theme.alpha(McUI.DARK_GRAY, alpha));
		else McUI.text(g, text, x + pad - scroll, cy, Theme.alpha(McUI.WHITE, alpha));
		if (focused && ((System.currentTimeMillis() - blinkStart) / 530) % 2 == 0) {
			Shapes.rect(g, Math.round(x + pad + cursorX - scroll), Math.round(cy) - 5, 1, 10, Theme.alpha(McUI.WHITE, alpha));
		}
		g.disableScissor();
	}

	public boolean keyPressed(KeyEvent e) {
		if (!focused) return false;
		int key = e.key();
		// macOS: ⌥ works word by word, ⌘ jumps to the line ends; elsewhere Ctrl works word by word
		boolean word = MacOS.wordModifier(e);
		boolean line = MacOS.lineModifier(e);
		blinkStart = System.currentTimeMillis();
		switch (key) {
			case InputConstants.KEY_BACKSPACE -> {
				if (cursor > 0) {
					int from = line ? 0 : word ? wordStart(cursor) : cursor - 1;
					text = text.substring(0, from) + text.substring(cursor);
					cursor = from;
					changed();
				}
			}
			case InputConstants.KEY_DELETE -> {
				if (cursor < text.length()) {
					text = text.substring(0, cursor) + text.substring(cursor + 1);
					changed();
				}
			}
			case InputConstants.KEY_LEFT -> cursor = line ? 0 : Math.max(0, word ? wordStart(cursor) : cursor - 1);
			case InputConstants.KEY_RIGHT -> cursor = line ? text.length() : Math.min(text.length(), word ? wordEnd(cursor) : cursor + 1);
			case InputConstants.KEY_HOME -> cursor = 0;
			case InputConstants.KEY_END -> cursor = text.length();
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_ESCAPE -> focused = false;
			case InputConstants.KEY_V -> {
				if (e.isPaste()) insert(Minecraft.getInstance().keyboardHandler.getClipboard());
				else return true;
			}
			case InputConstants.KEY_A -> {
				if (e.isSelectAll()) cursor = text.length();
			}
			default -> {
				return true;
			}
		}
		return true;
	}

	public boolean charTyped(CharacterEvent e) {
		if (!focused) return false;
		if (e.isAllowedChatCharacter()) insert(e.codepointAsString());
		return true;
	}

	private void insert(String s) {
		if (s == null || s.isEmpty()) return;
		s = s.replace("\n", "").replace("\r", "");
		int room = maxLength - text.length();
		if (room <= 0) return;
		if (s.length() > room) s = s.substring(0, room);
		text = text.substring(0, cursor) + s + text.substring(cursor);
		cursor += s.length();
		changed();
	}

	private int wordEnd(int from) {
		int i = from;
		while (i < text.length() && text.charAt(i) == ' ') i++;
		while (i < text.length() && text.charAt(i) != ' ') i++;
		return i;
	}

	private int wordStart(int from) {
		int i = from - 1;
		while (i > 0 && text.charAt(i - 1) == ' ') i--;
		while (i > 0 && text.charAt(i - 1) != ' ') i--;
		return Math.max(0, i);
	}

	private void changed() {
		if (onChange != null) onChange.accept(text);
	}
}
