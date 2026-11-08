package dev.lumina.client.feature.hud;

import dev.lumina.client.module.HudModule;
import dev.lumina.client.module.HudStyle;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ModeSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class CoordinatesModule extends HudModule {
	private static final float PAD_X = 6, PAD_Y = 4.5f, LINE = 11;

	private final ModeSetting layout;
	private final BoolSetting direction;
	private final BoolSetting biome;
	private final BoolSetting otherDimension;
	private final BoolSetting decimals;
	private final HudStyle style;

	public CoordinatesModule() {
		super("coordinates", "Coordinates", "Shows your position and facing", Icons.COMPASS, false, AnchorX.LEFT, AnchorY.TOP, 4, 40);
		layout = add(new ModeSetting("layout", "Layout", "Vertical", "Vertical", "Horizontal"));
		direction = add(new BoolSetting("direction", "Show Direction", true));
		biome = add(new BoolSetting("biome", "Show Biome", false));
		otherDimension = add(new BoolSetting("other_dimension", "Nether / Overworld Coords", false));
		decimals = add(new BoolSetting("decimals", "Show Decimals", false));
		style = style(true);
	}

	private record Line(String label, String value) {
	}

	private String fmt(double v) {
		return decimals.on() ? String.format(Locale.ROOT, "%.1f", v) : String.valueOf((int) Math.floor(v));
	}

	private List<Line> lines(boolean preview) {
		List<Line> out = new ArrayList<>();
		LocalPlayer p = mc().player;
		double x = 128.4, y = 64, z = -311.7;
		String facing = "N";
		String axis = "-Z";
		String biomeName = "Plains";
		boolean nether = false;
		if (p != null) {
			x = p.getX();
			y = p.getY();
			z = p.getZ();
			Direction d = p.getDirection();
			facing = switch (d) {
				case NORTH -> "N";
				case SOUTH -> "S";
				case EAST -> "E";
				case WEST -> "W";
				default -> "";
			};
			axis = switch (d) {
				case NORTH -> "-Z";
				case SOUTH -> "+Z";
				case EAST -> "+X";
				case WEST -> "-X";
				default -> "";
			};
			if (mc().level != null) {
				BlockPos pos = p.blockPosition();
				biomeName = mc().level.getBiome(pos).unwrapKey()
					.map(k -> pretty(k.identifier().getPath())).orElse("Unknown");
				nether = mc().level.dimension() == Level.NETHER;
			}
		} else if (!preview) {
			return out;
		}
		if (layout.is("Horizontal")) {
			String v = fmt(x) + ", " + fmt(y) + ", " + fmt(z);
			if (direction.on()) v += "  " + facing;
			out.add(new Line("XYZ", v));
		} else {
			out.add(new Line("X", fmt(x) + (direction.on() ? "   " + facing : "")));
			out.add(new Line("Y", fmt(y)));
			out.add(new Line("Z", fmt(z) + (direction.on() ? "   " + axis : "")));
		}
		if (otherDimension.on()) {
			double f = nether ? 8 : 0.125;
			out.add(new Line(nether ? "OW" : "Nether", fmt(x * f) + ", " + fmt(z * f)));
		}
		if (biome.on()) out.add(new Line("Biome", biomeName));
		return out;
	}

	private static String pretty(String path) {
		StringBuilder sb = new StringBuilder();
		for (String part : path.split("_")) {
			if (part.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	private float labelWidth(List<Line> lines) {
		float w = 0;
		for (Line l : lines) w = Math.max(w, style.width(l.label, HudStyle.TEXT_SIZE));
		return w;
	}

	@Override
	public float width() {
		List<Line> lines = lines(true);
		float lw = labelWidth(lines);
		float vw = 0;
		for (Line l : lines) vw = Math.max(vw, style.width(l.value, HudStyle.TEXT_SIZE));
		return PAD_X * 2 + lw + 6 + Math.max(vw, style.width("-0000", HudStyle.TEXT_SIZE));
	}

	@Override
	public float height() {
		return PAD_Y * 2 + lines(true).size() * LINE - 2;
	}

	@Override
	public boolean shouldRender() {
		return mc().player != null;
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
		List<Line> lines = lines(preview);
		float w = width(), h = height();
		style.drawBackground(g, 0, 0, w, h);
		float lw = labelWidth(lines);
		float y = PAD_Y - 0.5f;
		for (Line l : lines) {
			style.drawText(g, l.label, PAD_X, y, HudStyle.TEXT_SIZE, style.accent());
			style.drawText(g, l.value, PAD_X + lw + 6, y, HudStyle.TEXT_SIZE, style.text());
			y += LINE;
		}
	}
}
