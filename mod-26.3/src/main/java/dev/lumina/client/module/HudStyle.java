package dev.lumina.client.module;

import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.font.Fonts;
import dev.lumina.client.render.font.TtfFont;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import dev.lumina.client.setting.Setting;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The shared look settings of HUD elements: background, colors, shadow, corner radius, font. */
public final class HudStyle {
	public static final float TEXT_SIZE = 9f;

	public final BoolSetting background;
	public final ColorSetting backgroundColor;
	public final NumberSetting radius;
	public final BoolSetting border;
	public final ColorSetting borderColor;
	public final ColorSetting textColor;
	public final ColorSetting accentColor;
	public final BoolSetting shadow;
	public final ModeSetting font;

	HudStyle(Function<Setting<?>, Setting<?>> add, boolean withAccent) {
		add.apply(new HeaderSetting("Style"));
		background = (BoolSetting) add.apply(new BoolSetting("background", "Background", true));
		backgroundColor = (ColorSetting) add.apply(new ColorSetting("background_color", "Background Color", 0x990B0B0D)
			.visibleWhen(this::hasBackground));
		radius = (NumberSetting) add.apply(new NumberSetting("radius", "Corner Radius", 3, 0, 8, 0.5).visibleWhen(this::hasBackground));
		border = (BoolSetting) add.apply(new BoolSetting("border", "Border", false).visibleWhen(this::hasBackground));
		borderColor = (ColorSetting) add.apply(new ColorSetting("border_color", "Border Color", 0x22FFFFFF).visibleWhen(() -> hasBackground() && border.on()));
		textColor = (ColorSetting) add.apply(new ColorSetting("text_color", "Text Color", 0xFFF0F0F2));
		accentColor = withAccent ? (ColorSetting) add.apply(new ColorSetting("accent_color", "Label Color", 0xFF8E8E96)) : null;
		shadow = (BoolSetting) add.apply(new BoolSetting("text_shadow", "Text Shadow", false));
		font = (ModeSetting) add.apply(new ModeSetting("font", "Font Weight", "Medium", "Regular", "Medium", "Semibold", "Bold"));
	}

	private boolean hasBackground() {
		return background.on();
	}

	public TtfFont font() {
		return switch (font.get()) {
			case "Regular" -> Fonts.REGULAR;
			case "Semibold" -> Fonts.SEMIBOLD;
			case "Bold" -> Fonts.BOLD;
			default -> Fonts.MEDIUM;
		};
	}

	public int text() {
		return textColor.color();
	}

	public int accent() {
		return accentColor != null ? accentColor.color() : textColor.color();
	}

	public void drawBackground(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		if (!background.on()) return;
		float r = radius.getFloat();
		Shapes.round(g, x, y, w, h, r, backgroundColor.color());
		if (border.on()) Shapes.roundOutline(g, x, y, w, h, r, Shapes.hairline(), borderColor.color());
	}

	public void drawText(GuiGraphicsExtractor g, String text, float x, float y, float size, int color) {
		TtfFont f = font();
		if (shadow.on()) {
			int a = (color >>> 24) & 0xFF;
			f.draw(g, text, x + 0.6f, y + 0.6f, size, (Math.round(a * 0.55f) << 24));
		}
		f.draw(g, text, x, y, size, color);
	}

	public float width(String text, float size) {
		return font().width(text, size);
	}
}
