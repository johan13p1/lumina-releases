package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Anim;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Theme;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

/** Replaces the vanilla crosshair attack indicator with a minimal bar or ring. */
public final class AttackIndicatorModule extends Module {
	private final ModeSetting style;
	private final NumberSetting size;
	private final NumberSetting offset;
	private final ColorSetting color;
	private final ColorSetting background;
	private final ColorSetting readyColor;
	private final BoolSetting showReady;
	private final Anim fade = new Anim(0, 14);

	public AttackIndicatorModule() {
		super("attack_indicator", "Attack Indicator", "A cleaner attack cooldown indicator", Category.VISUAL, Icons.SWORDS, false);
		style = add(new ModeSetting("style", "Style", "Bar", "Bar", "Ring"));
		size = add(new NumberSetting("size", "Size", 16, 8, 32, 1));
		offset = add(new NumberSetting("offset", "Distance From Center", 10, 4, 30, 1));
		color = add(new ColorSetting("color", "Progress Color", 0xFFF0F0F2));
		background = add(new ColorSetting("background", "Background Color", 0x66000000));
		showReady = add(new BoolSetting("show_ready", "Flash When Target Ready", true));
		readyColor = add(new ColorSetting("ready_color", "Ready Color", 0xFF7FD48A).visibleWhen(showReady::on));
	}

	public void render(GuiGraphicsExtractor g) {
		var player = mc().player;
		if (player == null) return;
		float strength = player.getAttackStrengthScale(0);
		boolean ready = showReady.on() && mc().crosshairPickEntity instanceof LivingEntity target && target.isAlive()
			&& strength >= 1 && player.getCurrentItemAttackStrengthDelay() > 5;
		float visible = fade.update(strength < 1 || ready);
		if (visible < 0.01f) return;
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
		int fg = strength >= 1 && ready ? readyColor.color() : color.color();
		if (style.is("Bar")) {
			float w = size.getFloat(), h = 2;
			float x = cx - w / 2f, y = cy + offset.getFloat();
			Shapes.round(g, x, y, w, h, 1, Theme.alpha(background.color(), visible));
			Shapes.round(g, x, y, w * Math.min(1, strength), h, 1, Theme.alpha(fg, visible));
		} else {
			float r = size.getFloat() / 2f;
			Shapes.ring(g, cx, cy, r + offset.getFloat() * 0.3f, 1.2f, Theme.alpha(background.color(), visible));
			// progress as a shrinking gap: draw dots around the ring
			int steps = 48;
			int filled = Math.round(steps * Math.min(1, strength));
			float rr = r + offset.getFloat() * 0.3f;
			for (int i = 0; i < filled; i++) {
				double a = -Math.PI / 2 + i * (Math.PI * 2 / steps);
				Shapes.circle(g, cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, 0.75f, Theme.alpha(fg, visible));
			}
		}
	}
}
