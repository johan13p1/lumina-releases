package dev.lumina.client.module;

import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A single-line HUD element such as "144 FPS". */
public abstract class TextHudModule extends HudModule {
	protected static final float PAD_X = 5f;
	protected static final float PAD_Y = 3.5f;

	protected final ModeSetting layout;
	protected final BoolSetting showLabel;
	protected final ModeSetting brackets;
	protected final HudStyle style;

	protected TextHudModule(String id, String name, String description, String icon, boolean defaultEnabled,
		AnchorX ax, AnchorY ay, float ox, float oy) {
		super(id, name, description, icon, defaultEnabled, ax, ay, ox, oy);
		showLabel = add(new BoolSetting("show_label", "Show Label", defaultShowLabel()).visibleWhen(() -> !label().isEmpty()));
		layout = add(new ModeSetting("layout", "Label Position", defaultLayout(), "Before", "After")
			.visibleWhen(showLabel::on));
		brackets = add(new ModeSetting("brackets", "Brackets", "None", "None", "[ ]", "( )", "< >"));
		style = style(true);
	}

	protected boolean defaultShowLabel() {
		return true;
	}

	protected String defaultLayout() {
		return "After";
	}

	/** The label part, e.g. "FPS". */
	protected abstract String label();

	/** The value part, e.g. "144". */
	protected abstract String value(boolean preview);

	/** Minimum value width so the box doesn't jitter while the number changes. */
	protected String widthTemplate() {
		return "";
	}

	private String[] parts(boolean preview) {
		String v = value(preview);
		String l = showLabel.on() ? label() : "";
		String open = "", close = "";
		switch (brackets.get()) {
			case "[ ]" -> { open = "["; close = "]"; }
			case "( )" -> { open = "("; close = ")"; }
			case "< >" -> { open = "<"; close = ">"; }
			default -> { }
		}
		if (l.isEmpty()) return new String[] {open, v, "", close};
		return layout.is("Before") ? new String[] {open, l + " ", v, close} : new String[] {open, v, " " + l, close};
	}

	private float textWidth(boolean preview) {
		String[] p = parts(preview);
		float size = HudStyle.TEXT_SIZE;
		float w = 0;
		for (String s : p) w += style.width(s, size);
		String template = widthTemplate();
		if (!template.isEmpty()) {
			String valuePart = layout.is("Before") && showLabel.on() ? p[2] : p[1];
			float vw = style.width(valuePart, size);
			float tw = style.width(template, size);
			if (tw > vw) w += tw - vw;
		}
		return w;
	}

	@Override
	public float width() {
		return textWidth(false) + PAD_X * 2;
	}

	@Override
	public float height() {
		return style.font().lineHeight(HudStyle.TEXT_SIZE) + PAD_Y * 2 - 2f;
	}

	@Override
	public void render(GuiGraphicsExtractor g, boolean preview) {
		float w = width(), h = height();
		style.drawBackground(g, 0, 0, w, h);
		String[] p = parts(preview);
		float size = HudStyle.TEXT_SIZE;
		float textW = 0;
		for (String s : p) textW += style.width(s, size);
		float x = (w - textW) / 2f;
		float y = (h - style.font().lineHeight(size)) / 2f + 0.25f;
		boolean labelFirst = layout.is("Before");
		int text = style.text(), accent = style.accent();
		int[] colors = {accent, labelFirst ? accent : text, labelFirst ? text : accent, accent};
		for (int i = 0; i < 4; i++) {
			if (p[i].isEmpty()) continue;
			style.drawText(g, p[i], x, y, size, colors[i]);
			x += style.width(p[i], size);
		}
	}
}
