package dev.lumina.client.feature.camera;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.LuminaKeys;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.KeySetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.KeyMapping;

public final class ZoomModule extends Module {
	private final KeyMapping key = LuminaKeys.register("zoom", InputConstants.KEY_C);
	private final NumberSetting factor;
	private final ModeSetting mode;
	private final BoolSetting smooth;
	private final NumberSetting smoothSpeed;
	private final BoolSetting scroll;
	public final BoolSetting cinematic;
	public final BoolSetting hideHand;
	private boolean active;
	private boolean wasDown;
	private float scrollFactor = 1;
	private double current = 1;
	private long lastFrame = System.nanoTime();

	public ZoomModule() {
		super("zoom", "Zoom", "Zoom in with a key (default C)", Category.CAMERA, Icons.ZOOM, true);
		add(new KeySetting("key", "Zoom Key", key));
		mode = add(new ModeSetting("mode", "Activation", "Hold", "Hold", "Toggle"));
		factor = add(new NumberSetting("factor", "Zoom Factor", 4, 1.5, 30, 0.5).suffix("x"));
		smooth = add(new BoolSetting("smooth", "Smooth Zoom", true));
		smoothSpeed = add(new NumberSetting("smooth_speed", "Smoothness Speed", 12, 3, 30, 1).visibleWhen(smooth::on));
		scroll = add(new BoolSetting("scroll", "Scroll To Adjust", true));
		cinematic = add(new BoolSetting("cinematic", "Cinematic Camera While Zoomed", false));
		hideHand = add(new BoolSetting("hide_hand", "Hide Hand While Zoomed", true));
	}

	@Override
	public void tick() {
		boolean down = enabled() && mc().gui.screen() == null && key.isDown();
		if (mode.is("Toggle")) {
			if (down && !wasDown) active = !active;
		} else {
			active = down;
		}
		if (!enabled()) active = false;
		if (!active) scrollFactor = 1;
		wasDown = down;
	}

	public boolean active() {
		return active;
	}

	/** True while zoomed in (or still animating). */
	public boolean zoomedIn() {
		return current > 1.01;
	}

	/** Called every frame from the FOV hook; returns the divisor for the field of view. */
	public double zoomDivisor() {
		double target = active ? factor.get() * scrollFactor : 1;
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastFrame) / 1e9);
		lastFrame = now;
		if (!smooth.on()) current = target;
		else current += (target - current) * (1 - Math.exp(-smoothSpeed.get() * dt));
		if (Math.abs(current - target) < 0.001) current = target;
		return current;
	}

	/** Mouse wheel while zoomed adjusts the zoom; returns true if the scroll was consumed. */
	public boolean onScroll(double amount) {
		if (!active || !scroll.on() || amount == 0) return false;
		// proportional, so a trackpad's many small deltas zoom as smoothly as a wheel's notches
		scrollFactor *= (float) Math.pow(1.15, Math.max(-3, Math.min(3, amount)));
		scrollFactor = (float) Math.max(1 / factor.get(), Math.min(50 / factor.get(), scrollFactor));
		return true;
	}

	public double sensitivityScale() {
		return 1 / Math.max(1, current);
	}
}
