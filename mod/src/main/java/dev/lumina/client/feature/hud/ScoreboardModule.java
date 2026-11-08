package dev.lumina.client.feature.hud;

import dev.lumina.client.module.HudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.font.Fonts;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/** Re-implements the sidebar so it can be moved, scaled and restyled. */
public final class ScoreboardModule extends HudModule {
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
		.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final float LINE = 9.5f, PAD = 3;

	private final BoolSetting hideNumbers;
	private final BoolSetting titleBackground;
	private final ColorSetting background;
	private final ColorSetting titleColor;
	private final NumberSetting radius;
	private final ModeSetting font;
	private final BoolSetting shadow;
	private final ColorSetting numberColor;

	public ScoreboardModule() {
		super("scoreboard", "Scoreboard", "Move, scale and restyle the sidebar", Icons.LIST, true, AnchorX.RIGHT, AnchorY.CENTER, 2, 0);
		hideNumbers = add(new BoolSetting("hide_numbers", "Hide Red Numbers", true));
		font = add(new ModeSetting("font", "Font", "Minecraft", "Minecraft", "Lumina"));
		shadow = add(new BoolSetting("shadow", "Text Shadow", false));
		add(new HeaderSetting("Background"));
		background = add(new ColorSetting("background", "Background", 0x660B0B0D));
		titleBackground = add(new BoolSetting("title_background", "Title Background", true));
		titleColor = add(new ColorSetting("title_color", "Title Background Color", 0x800B0B0D).visibleWhen(titleBackground::on));
		radius = add(new NumberSetting("radius", "Corner Radius", 3, 0, 8, 0.5));
		numberColor = add(new ColorSetting("number_color", "Number Color", 0xFFFF5555).visibleWhen(() -> !hideNumbers.on()));
	}

	private record Row(Component name, Component score) {
	}

	private record Data(Component title, List<Row> rows) {
	}

	private @Nullable Data data(boolean preview) {
		var level = mc().level;
		var player = mc().player;
		if (level != null && player != null) {
			Scoreboard sb = level.getScoreboard();
			Objective objective = null;
			PlayerTeam team = sb.getPlayersTeam(player.getScoreboardName());
			if (team != null) {
				DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
				if (slot != null) objective = sb.getDisplayObjective(slot);
			}
			if (objective == null) objective = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (objective != null) {
				NumberFormat fmt = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
				List<Row> rows = new ArrayList<>();
				for (PlayerScoreEntry e : sb.listPlayerScores(objective).stream().filter(e -> !e.isHidden()).sorted(ORDER).limit(15).toList()) {
					PlayerTeam t = sb.getPlayersTeam(e.owner());
					rows.add(new Row(PlayerTeam.formatNameForTeam(t, e.ownerName()), e.formatValue(fmt)));
				}
				return new Data(objective.getDisplayName(), rows);
			}
		}
		if (!preview) return null;
		List<Row> rows = new ArrayList<>();
		String[] sample = {"", "Players: 12", "Kills: 3", "", "Map: Lighthouse", "Mode: Solo", "", "lumina.client"};
		for (int i = 0; i < sample.length; i++) rows.add(new Row(Component.literal(sample[i]), Component.literal(String.valueOf(sample.length - i))));
		return new Data(Component.literal("LUMINA"), rows);
	}

	private boolean useVanilla(Component c) {
		if (font.is("Minecraft") || Fonts.MEDIUM.isVanillaFallback()) return true;
		// Server fonts (icons, custom glyphs) only exist in the vanilla renderer.
		boolean[] custom = {false};
		c.getVisualOrderText().accept((i, style, cp) -> {
			if (!(style.getFont() instanceof FontDescription.Resource r) || !r.equals(FontDescription.DEFAULT)) custom[0] = true;
			return !custom[0];
		});
		return custom[0];
	}

	private float textWidth(Component c) {
		if (useVanilla(c)) return mc().font.width(c);
		return Fonts.MEDIUM.width(c.getVisualOrderText(), 9, Fonts.BOLD);
	}

	private void drawText(GuiGraphics g, Component c, float x, float y, int color) {
		if (useVanilla(c)) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, y + 0.5f);
			g.drawString(mc().font, c, 0, 0, color, shadow.on());
			pose.popMatrix();
		} else {
			FormattedCharSequence seq = c.getVisualOrderText();
			if (shadow.on()) Fonts.MEDIUM.draw(g, recolor(seq), x + 0.6f, y + 0.6f, 9, 0x8C000000, Fonts.BOLD);
			Fonts.MEDIUM.draw(g, seq, x, y, 9, color, Fonts.BOLD);
		}
	}

	private static FormattedCharSequence recolor(FormattedCharSequence seq) {
		return sink -> seq.accept((i, style, cp) -> sink.accept(i, style.withColor((net.minecraft.network.chat.TextColor) null), cp));
	}

	private float contentWidth(Data d) {
		float w = textWidth(d.title);
		float sep = mc().font.width(": ");
		for (Row r : d.rows) {
			float rw = textWidth(r.name);
			if (!hideNumbers.on()) rw += sep + textWidth(r.score);
			w = Math.max(w, rw);
		}
		return w;
	}

	@Override
	public float width() {
		Data d = data(true);
		return d == null ? 60 : contentWidth(d) + PAD * 2 + 2;
	}

	@Override
	public float height() {
		Data d = data(true);
		return d == null ? 20 : (d.rows.size() + 1) * LINE + PAD * 2 + 1;
	}

	@Override
	public boolean shouldRender() {
		return data(false) != null;
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
		Data d = data(preview);
		if (d == null) return;
		float w = width(), h = height();
		float r = radius.getFloat();
		float titleH = LINE + PAD + 1;
		if (titleBackground.on()) {
			Shapes.round(g, 0, 0, w, titleH, r, titleColor.color(), true, true, false, false);
			Shapes.round(g, 0, titleH, w, h - titleH, r, background.color(), false, false, true, true);
		} else {
			Shapes.round(g, 0, 0, w, h, r, background.color());
		}
		float tw = textWidth(d.title);
		drawText(g, d.title, (w - tw) / 2f, PAD, 0xFFFFFFFF);
		float y = titleH + 1;
		for (Row row : d.rows) {
			drawText(g, row.name, PAD + 1, y, 0xFFFFFFFF);
			if (!hideNumbers.on()) {
				float sw = textWidth(row.score);
				boolean styled = row.score.getStyle().getColor() != null;
				drawText(g, row.score, w - PAD - 1 - sw, y, styled ? 0xFFFFFFFF : numberColor.color());
			}
			y += LINE;
		}
	}

}
