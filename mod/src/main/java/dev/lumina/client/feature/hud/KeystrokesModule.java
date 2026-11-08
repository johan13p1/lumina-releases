package dev.lumina.client.feature.hud;

import dev.lumina.client.module.HudModule;
import dev.lumina.client.module.HudStyle;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.NumberSetting;
import dev.lumina.client.util.CpsTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;

public final class KeystrokesModule extends HudModule {
	private final NumberSetting keySize;
	private final NumberSetting gap;
	private final BoolSetting mouse;
	private final BoolSetting showCps;
	private final BoolSetting space;
	private final ColorSetting pressedColor;
	private final ColorSetting pressedText;
	private final HudStyle style;
	private final Anim[] anims = new Anim[7];

	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", "Shows WASD, mouse and space presses", Icons.KEYBOARD, false, AnchorX.LEFT, AnchorY.TOP, 4, 120);
		keySize = add(new NumberSetting("key_size", "Key Size", 22, 14, 34, 1));
		gap = add(new NumberSetting("gap", "Gap", 2, 0, 6, 0.5));
		mouse = add(new BoolSetting("mouse", "Show Mouse Buttons", true));
		showCps = add(new BoolSetting("show_cps", "CPS On Mouse Buttons", true).visibleWhen(mouse::on));
		space = add(new BoolSetting("space", "Show Space Bar", true));
		style = style(false);
		add(new HeaderSetting("Pressed"));
		pressedColor = add(new ColorSetting("pressed_color", "Pressed Color", 0xE6F0F0F2));
		pressedText = add(new ColorSetting("pressed_text", "Pressed Text", 0xFF101012));
		for (int i = 0; i < anims.length; i++) anims[i] = new Anim(0, 22);
	}

	@Override
	public float width() {
		return keySize.getFloat() * 3 + gap.getFloat() * 2;
	}

	@Override
	public float height() {
		float k = keySize.getFloat(), gp = gap.getFloat();
		float h = k * 2 + gp;
		if (mouse.on()) h += gp + k * 0.8f;
		if (space.on()) h += gp + k * 0.45f;
		return h;
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
		Options o = mc().options;
		float k = keySize.getFloat(), gp = gap.getFloat();
		key(g, 0, label(o.keyUp, "W"), k + gp, 0, k, k, o.keyUp.isDown());
		key(g, 1, label(o.keyLeft, "A"), 0, k + gp, k, k, o.keyLeft.isDown());
		key(g, 2, label(o.keyDown, "S"), k + gp, k + gp, k, k, o.keyDown.isDown());
		key(g, 3, label(o.keyRight, "D"), (k + gp) * 2, k + gp, k, k, o.keyRight.isDown());
		float y = (k + gp) * 2;
		if (mouse.on()) {
			float mw = (width() - gp) / 2f, mh = k * 0.8f;
			String l = showCps.on() ? CpsTracker.left() + " CPS" : "LMB";
			String r = showCps.on() ? CpsTracker.right() + " CPS" : "RMB";
			key(g, 4, l, 0, y, mw, mh, o.keyAttack.isDown());
			key(g, 5, r, mw + gp, y, mw, mh, o.keyUse.isDown());
			y += mh + gp;
		}
		if (space.on()) {
			float sh = k * 0.45f;
			float pressed = anims[6].update(o.keyJump.isDown());
			float w = width();
			if (style.background.on() || pressed > 0) {
				int bg = Theme.mix(style.backgroundColor.color(), pressedColor.color(), pressed);
				Shapes.round(g, 0, y, w, sh, style.radius.getFloat(), bg);
			}
			int bar = Theme.mix(style.text(), pressedText.color(), pressed);
			Shapes.round(g, w / 2f - w * 0.18f, y + sh / 2f - 0.6f, w * 0.36f, 1.2f, 0.6f, Theme.alpha(bar, 0.9f));
		}
	}

	private static String label(KeyMapping mapping, String fallback) {
		String s = mapping.getTranslatedKeyMessage().getString();
		if (s.isEmpty() || s.length() > 3) return fallback;
		return s.toUpperCase();
	}

	private void key(GuiGraphics g, int index, String label, float x, float y, float w, float h, boolean down) {
		float t = anims[index].update(down);
		int bg = Theme.mix(style.backgroundColor.color(), pressedColor.color(), t);
		if (style.background.on() || t > 0) Shapes.round(g, x, y, w, h, style.radius.getFloat(), bg);
		if (style.border.on()) Shapes.roundOutline(g, x, y, w, h, style.radius.getFloat(), Shapes.hairline(), style.borderColor.color());
		int color = Theme.mix(style.text(), pressedText.color(), t);
		float size = label.length() > 2 ? HudStyle.TEXT_SIZE * 0.78f : HudStyle.TEXT_SIZE;
		float tw = style.width(label, size);
		float top = y + h / 2f - style.font().capHeight(size) / 2f - (style.font().ascent(size) - style.font().capHeight(size));
		style.drawText(g, label, x + (w - tw) / 2f, top, size, color);
	}
}
