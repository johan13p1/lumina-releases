package dev.lumina.client.feature.hud;

import dev.lumina.client.mixin.BossHealthOverlayAccessor;
import dev.lumina.client.module.HudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.font.Fonts;
import dev.lumina.client.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

/** Moves and scales the vanilla boss bar. */
public final class BossBarModule extends HudModule {
	private final BoolSetting hide;

	public BossBarModule() {
		super("boss_bar", "Boss Bar", "Move, scale or hide boss bars", Icons.SHIELD, false, AnchorX.CENTER, AnchorY.TOP, 0, 3);
		hide = add(new BoolSetting("hide", "Hide Boss Bars", false));
	}

	public boolean hidden() {
		return hide.on();
	}

	private int count() {
		var overlay = mc().gui.getBossOverlay();
		int n = ((BossHealthOverlayAccessor) overlay).lumina$events().size();
		int max = Math.max(1, (mc().getWindow().getGuiScaledHeight() / 3 - 12) / 19 + 1);
		return Math.min(n, max);
	}

	@Override
	public float width() {
		return 182;
	}

	@Override
	public float height() {
		return Math.max(1, count()) * 19 - 4;
	}

	@Override
	public boolean shouldRender() {
		return !hide.on() && count() > 0;
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
		if (count() == 0) {
			if (preview) {
				Fonts.MEDIUM.draw(g, "Boss Bar", 91 - Fonts.MEDIUM.width("Boss Bar", 8) / 2f, -1, 8, 0xFFFFFFFF);
				Shapes.round(g, 0, 9, 182, 5, 2.5f, 0xFF3A1F4F);
				Shapes.round(g, 0, 9, 120, 5, 2.5f, 0xFFB44CE0);
			}
			return;
		}
		// The vanilla overlay draws centered at (screenWidth / 2, 12) with the name 9px above the bar.
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(-(g.guiWidth() / 2f - 91), -3);
		mc().gui.getBossOverlay().render(g);
		pose.popMatrix();
	}
}
