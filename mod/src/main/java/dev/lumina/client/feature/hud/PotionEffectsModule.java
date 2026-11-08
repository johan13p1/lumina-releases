package dev.lumina.client.feature.hud;

import dev.lumina.client.module.HudModule;
import dev.lumina.client.module.HudStyle;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.ModeSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class PotionEffectsModule extends HudModule {
	private static final float ROW = 17, PAD = 5, ICON = 12;

	private final BoolSetting icons;
	private final BoolSetting names;
	private final BoolSetting duration;
	private final BoolSetting level;
	private final ModeSetting nameColor;
	private final ColorSetting customNameColor;
	private final ColorSetting durationColor;
	private final BoolSetting blink;
	private final ModeSetting sort;
	private final BoolSetting perEffectBackground;
	public final BoolSetting hideVanilla;
	private final HudStyle style;

	public PotionEffectsModule() {
		super("potion_effects", "Potion Effects", "Shows active effects with custom colors", Icons.FLASK, true, AnchorX.RIGHT, AnchorY.BOTTOM, 4, 46);
		icons = add(new BoolSetting("icons", "Show Icons", true));
		names = add(new BoolSetting("names", "Show Names", true));
		level = add(new BoolSetting("level", "Show Level", true).visibleWhen(names::on));
		duration = add(new BoolSetting("duration", "Show Duration", true));
		blink = add(new BoolSetting("blink", "Blink When Expiring", true));
		sort = add(new ModeSetting("sort", "Sort By", "Duration", "Duration", "Name", "Vanilla"));
		hideVanilla = add(new BoolSetting("hide_vanilla", "Hide Vanilla Icons", true));
		add(new HeaderSetting("Colors"));
		nameColor = add(new ModeSetting("name_color_mode", "Name Color", "Effect", "Effect", "Custom"));
		customNameColor = add(new ColorSetting("name_color", "Custom Name Color", 0xFFF0F0F2).visibleWhen(() -> nameColor.is("Custom")));
		durationColor = add(new ColorSetting("duration_color", "Duration Color", 0xFF9A9AA3));
		perEffectBackground = add(new BoolSetting("separate_rows", "Separate Rows", false));
		style = style(false);
	}

	private record Entry(Holder<MobEffect> effect, int amplifier, int duration, boolean infinite, boolean ambient) {
	}

	private List<Entry> entries(boolean preview) {
		List<Entry> list = new ArrayList<>();
		var player = mc().player;
		if (player != null) {
			for (MobEffectInstance e : player.getActiveEffects()) {
				list.add(new Entry(e.getEffect(), e.getAmplifier(), e.getDuration(), e.isInfiniteDuration(), e.isAmbient()));
			}
		}
		if (list.isEmpty() && preview) {
			list.add(new Entry(MobEffects.SPEED, 1, 1820, false, false));
			list.add(new Entry(MobEffects.STRENGTH, 0, 640, false, false));
			list.add(new Entry(MobEffects.FIRE_RESISTANCE, 0, 9600, false, false));
		}
		switch (sort.get()) {
			case "Duration" -> list.sort(Comparator.comparingInt((Entry e) -> e.infinite ? Integer.MAX_VALUE : e.duration));
			case "Name" -> list.sort(Comparator.comparing((Entry e) -> e.effect.value().getDisplayName().getString()));
			default -> {
			}
		}
		return list;
	}

	private String name(Entry e) {
		String n = e.effect.value().getDisplayName().getString();
		if (level.on() && e.amplifier > 0) n += " " + roman(e.amplifier + 1);
		return n;
	}

	private static String time(Entry e) {
		if (e.infinite) return "∞";
		int seconds = e.duration / 20;
		int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}

	private float textBlockWidth(List<Entry> list) {
		float w = 0;
		for (Entry e : list) {
			float lw = 0;
			if (names.on()) lw += style.width(name(e), HudStyle.TEXT_SIZE);
			if (duration.on()) lw += (names.on() ? 6 : 0) + style.width(time(e), HudStyle.TEXT_SIZE);
			w = Math.max(w, lw);
		}
		return w;
	}

	@Override
	public float width() {
		List<Entry> list = entries(true);
		float w = PAD * 2 + textBlockWidth(list);
		if (icons.on()) w += ICON + (names.on() || duration.on() ? 5 : 0);
		return Math.max(w, 30);
	}

	@Override
	public float height() {
		int n = Math.max(1, entries(true).size());
		return PAD * 2 + n * ROW - (ROW - 12) + (perEffectBackground.on() ? 0 : 0);
	}

	@Override
	public boolean shouldRender() {
		return mc().player != null && !mc().player.getActiveEffects().isEmpty();
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
		List<Entry> list = entries(preview);
		float w = width(), h = height();
		boolean rightAligned = ax == AnchorX.RIGHT;
		if (!perEffectBackground.on()) style.drawBackground(g, 0, 0, w, h);
		float y = PAD;
		for (Entry e : list) {
			if (perEffectBackground.on()) style.drawBackground(g, 0, y - 3, w, ROW - 2);
			float alpha = 1f;
			if (blink.on() && !e.infinite && e.duration < 200) {
				alpha = 0.45f + 0.55f * (float) Math.abs(Math.cos(e.duration * Math.PI / 10.0));
			}
			float x = PAD;
			float contentW = (icons.on() ? ICON + 5 : 0) + rowTextWidth(e);
			if (rightAligned) x = w - PAD - contentW;
			if (icons.on()) {
				var pose = g.pose();
				pose.pushMatrix();
				pose.translate(x, y - 0.5f);
				pose.scale(ICON / 18f, ICON / 18f);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.effect), 0, 0, 18, 18, ARGB.white(alpha));
				pose.popMatrix();
				x += ICON + 5;
			}
			float ty = y + ICON / 2f - style.font().capHeight(HudStyle.TEXT_SIZE) / 2f - (style.font().ascent(HudStyle.TEXT_SIZE) - style.font().capHeight(HudStyle.TEXT_SIZE));
			if (names.on()) {
				int color = nameColor.is("Effect") ? ARGB.opaque(e.effect.value().getColor()) : customNameColor.color();
				color = lighten(color);
				String n = name(e);
				style.drawText(g, n, x, ty, HudStyle.TEXT_SIZE, ARGB.multiplyAlpha(color, alpha));
				x += style.width(n, HudStyle.TEXT_SIZE) + 6;
			}
			if (duration.on()) {
				style.drawText(g, time(e), x, ty, HudStyle.TEXT_SIZE, ARGB.multiplyAlpha(durationColor.color(), alpha));
			}
			y += ROW;
		}
	}

	private float rowTextWidth(Entry e) {
		float w = 0;
		if (names.on()) w += style.width(name(e), HudStyle.TEXT_SIZE);
		if (duration.on()) w += (names.on() ? 6 : 0) + style.width(time(e), HudStyle.TEXT_SIZE);
		return w;
	}

	/** Effect colors are often very dark; brighten them so they stay readable on a dark background. */
	private static int lighten(int color) {
		int r = ARGB.red(color), gr = ARGB.green(color), b = ARGB.blue(color);
		float lum = (0.299f * r + 0.587f * gr + 0.114f * b) / 255f;
		if (lum >= 0.45f) return color;
		float k = 0.45f / Math.max(0.05f, lum);
		return ARGB.color(ARGB.alpha(color), Math.min(255, Math.round(r * k + 30)), Math.min(255, Math.round(gr * k + 30)), Math.min(255, Math.round(b * k + 30)));
	}
}
