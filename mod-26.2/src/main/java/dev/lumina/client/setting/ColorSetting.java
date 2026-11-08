package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;

/** ARGB color with optional "chroma" (rainbow cycling). */
public class ColorSetting extends Setting<Integer> {
	private final boolean alpha;
	private boolean chroma;
	private float chromaSpeed = 1f;

	public ColorSetting(String id, String name, int defaultArgb) {
		this(id, name, defaultArgb, true);
	}

	public ColorSetting(String id, String name, int defaultArgb, boolean alpha) {
		super(id, name, defaultArgb);
		this.alpha = alpha;
	}

	public boolean allowsAlpha() {
		return alpha;
	}

	public boolean chroma() {
		return chroma;
	}

	public void setChroma(boolean chroma) {
		this.chroma = chroma;
		SettingChanges.markDirty();
	}

	/** The color to draw with, chroma applied. */
	public int color() {
		if (!chroma) return value;
		float hue = (System.currentTimeMillis() % (long) (4000 / chromaSpeed)) / (4000f / chromaSpeed);
		float[] hsb = Color.RGBtoHSB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, null);
		int rgb = Color.HSBtoRGB(hue, Math.max(0.6f, hsb[1]), Math.max(0.8f, hsb[2])) & 0xFFFFFF;
		return (value & 0xFF000000) | rgb;
	}

	/** Same as {@link #color()} but with the alpha multiplied. */
	public int color(float alphaMul) {
		int c = color();
		int a = Math.round(((c >>> 24) & 0xFF) * alphaMul);
		return (a << 24) | (c & 0xFFFFFF);
	}

	@Override
	protected Integer sanitize(Integer v) {
		return alpha ? v : (v | 0xFF000000);
	}

	@Override
	public void reset() {
		super.reset();
		chroma = false;
	}

	@Override
	public JsonElement save() {
		JsonObject o = new JsonObject();
		o.addProperty("argb", String.format("#%08X", value));
		if (chroma) o.addProperty("chroma", true);
		return o;
	}

	@Override
	public void load(JsonElement element) {
		if (element == null) return;
		try {
			if (element.isJsonObject()) {
				JsonObject o = element.getAsJsonObject();
				value = sanitize((int) Long.parseLong(o.get("argb").getAsString().substring(1), 16));
				chroma = o.has("chroma") && o.get("chroma").getAsBoolean();
			} else if (element.isJsonPrimitive()) {
				value = sanitize(element.getAsInt());
			}
		} catch (RuntimeException ignored) {
		}
	}
}
