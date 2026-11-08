package dev.lumina.client.render;

/**
 * Frame-rate independent animated value. {@link #update()} eases the current value towards the
 * target with exponential smoothing; {@code speed} is roughly "1/time constant" in seconds.
 */
public final class Anim {
	private float value;
	private float target;
	private final float speed;
	private long last = System.nanoTime();

	public Anim(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public Anim(float speed) {
		this(0, speed);
	}

	public float update() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		float k = 1f - (float) Math.exp(-speed * dt);
		value += (target - value) * k;
		if (Math.abs(target - value) < 0.0005f) value = target;
		return value;
	}

	public float update(float newTarget) {
		target = newTarget;
		return update();
	}

	public float update(boolean on) {
		return update(on ? 1f : 0f);
	}

	public float get() {
		return value;
	}

	public void target(float t) {
		this.target = t;
	}

	public void snap(float v) {
		this.value = v;
		this.target = v;
		this.last = System.nanoTime();
	}

	public static float easeOutCubic(float t) {
		t = Math.max(0, Math.min(1, t));
		float f = 1 - t;
		return 1 - f * f * f;
	}

	public static float easeOutBack(float t) {
		t = Math.max(0, Math.min(1, t));
		float c1 = 1.70158f, c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}
}
