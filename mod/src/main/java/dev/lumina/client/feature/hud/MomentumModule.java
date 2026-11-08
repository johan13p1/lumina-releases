package dev.lumina.client.feature.hud;

import dev.lumina.client.module.TextHudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ModeSetting;
import java.util.Locale;
import net.minecraft.world.phys.Vec3;

/** Speed readout in blocks per second, like Lunar's momentum mod. */
public final class MomentumModule extends TextHudModule {
	private final BoolSetting vertical;
	private final ModeSetting unit;
	private Vec3 last;
	private final double[] samples = new double[5];
	private int sampleIndex;

	public MomentumModule() {
		super("momentum", "Momentum", "Shows how fast you are moving", Icons.WIND, false, AnchorX.CENTER, AnchorY.BOTTOM, 0, 62);
		vertical = add(new BoolSetting("vertical", "Include Vertical Speed", false));
		unit = add(new ModeSetting("unit", "Unit", "b/s", "b/s", "km/h"));
	}

	@Override
	public void tick() {
		var p = mc().player;
		if (p == null) {
			last = null;
			return;
		}
		Vec3 pos = p.position();
		if (last != null) {
			double dx = pos.x - last.x, dy = vertical.on() ? pos.y - last.y : 0, dz = pos.z - last.z;
			samples[sampleIndex++ % samples.length] = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
		}
		last = pos;
	}

	private double speed() {
		double sum = 0;
		for (double s : samples) sum += s;
		return sum / samples.length;
	}

	@Override
	protected String label() {
		return unit.get();
	}

	@Override
	protected String value(boolean preview) {
		double v = speed();
		if (unit.is("km/h")) v *= 3.6;
		return String.format(Locale.ROOT, "%.2f", v);
	}

	@Override
	protected String widthTemplate() {
		return "00.00";
	}
}
