package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private String suffix = "";

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
		super(id, name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public NumberSetting suffix(String suffix) {
		this.suffix = suffix;
		return this;
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public float getFloat() {
		return value.floatValue();
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	public double progress() {
		return (value - min) / (max - min);
	}

	public void setProgress(double p) {
		set(min + Math.max(0, Math.min(1, p)) * (max - min));
	}

	@Override
	protected Double sanitize(Double v) {
		double clamped = Math.max(min, Math.min(max, v));
		if (step > 0) clamped = Math.round((clamped - min) / step) * step + min;
		// avoid 0.30000000000000004
		return Math.round(clamped * 10000.0) / 10000.0;
	}

	public String display() {
		String number;
		if (step >= 1) number = String.valueOf((long) Math.round(value));
		else if (step >= 0.1) number = String.format(Locale.ROOT, "%.1f", value);
		else number = String.format(Locale.ROOT, "%.2f", value);
		return number + suffix;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) value = sanitize(element.getAsDouble());
	}
}
