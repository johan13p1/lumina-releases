package dev.lumina.client.feature.cosmetics;

/**
 * Cloth physics for one cape. The cape is a chain of points, one per pixel row, sewn to the
 * shoulders: gravity pulls it down, the air pushes on its face while the player moves, gusts make
 * it flutter, and the back and the legs keep it out of the body. Steps run at a fixed rate in real
 * time and the shape is blended between the last two steps, so it moves the same at any frame rate.
 *
 * <p>Units are cape pixels. The sim turns with the body but stays upright (x = left, y = down,
 * z = back, origin at the top middle of the inner side), so gravity is always +y and the player's
 * real movement and turning reach the cape as inertia. No Minecraft classes here: the three game
 * versions share this file as it is.
 */
final class CapeSim {
	static final int SEGMENTS = 16;
	private static final int N = SEGMENTS + 1;
	/** One block in model pixels (players are drawn at 15/16 size). */
	static final float PX_PER_BLOCK = 16 / 0.9375f;

	private static final float STEP = 1 / 120f;
	private static final long STEP_NANOS = 1_000_000_000L / 120;
	private static final int MAX_STEPS = 12;
	/** Faster than this (blocks/s) is a teleport. */
	private static final double TELEPORT_SPEED = 100;
	private static final int ITERATIONS = 8;
	/** How far the back (where the cape hangs) is behind the body's turning axis. */
	private static final float BACK = 2;
	/** Hip joint below the top of the cape. */
	private static final float HIP = 12;

	private static final float GRAVITY = 560;
	/** Air pushing on the face of the cape: linear (1/s) and quadratic (1/px) part. */
	private static final float DRAG = 6.2f, DRAG_SQ = 0.008f;
	/** Air sliding along the cloth barely pushes it. */
	private static final float DRAG_ALONG = 0.6f;
	/** The most air the cape feels (px/s), so falls and fast flight don't tear it around. */
	private static final float MAX_AIR = 150;
	/** A fall throws the cape back as if running at this share of the fall speed, up to FALL_MAX px/s. */
	private static final float FALL_LIFT = 0.3f, FALL_MAX = 200;
	/** Share of the body's up and down movement the cape feels as inertia. */
	private static final float VERTICAL = 0.15f;
	/** Gust strength (share of gravity): standing, moving, and extra when fast. */
	private static final float GUST_STILL = 0.045f, GUST_MOVE = 0.09f, GUST_FAST = 0.05f;
	private static final float DAMPING = 0.998f;
	/** Share of a row's motion that follows its neighbours, and the body, each step. */
	private static final float INNER = 0.15f, WITH_BODY = 0.05f;
	/** Per step: how quickly the felt body velocity and turning catch up with the real ones. */
	private static final double SOFTEN = 1 - Math.exp(-STEP / 0.09);
	private static final float SOFTEN_TURN = (float) (1 - Math.exp(-STEP / 0.05));
	private static final float BEND = 0.8f;
	/** The top rows are sewn on and lie on the back: how hard they are pulled to their rest place, per step. */
	private static final float[] SEWN = {0, 0.30f, 0.12f, 0.045f, 0.015f};
	/** Sideways the cape is held by the full width of the shoulders: pull back to the middle, per step. */
	private static final float SIDE = 0.10f;
	private static final float REST_ANGLE = (float) Math.toRadians(4);
	/** Highest the cape can fly: this far above the horizontal behind the shoulders (tan). */
	private static final float MAX_RISE = 0.27f;

	// current, previous (for Verlet) and before the last step (for blending)
	private final float[] x = new float[N], y = new float[N], z = new float[N];
	private final float[] px = new float[N], py = new float[N], pz = new float[N];
	private final float[] sx = new float[N], sy = new float[N], sz = new float[N];
	// per step: accelerations and motions of the rows
	private final float[] ix = new float[N], iy = new float[N], iz = new float[N];
	private final float[] dvx = new float[N], dvy = new float[N], dvz = new float[N];
	private final float[] rx = new float[N], ry = new float[N], rz = new float[N];
	/** The shape to draw, in the body's frame (pitched with it), blended to the current moment. */
	final float[] bx = new float[N], by = new float[N], bz = new float[N];

	private boolean started;
	/** Time the sim has reached, and of the last input. */
	private long simNanos, lastNanos;
	private double time;
	/** The body position and yaw the sim has caught up with, and the last input (blocks, degrees). */
	private double ex, ey, ez, lastX, lastY, lastZ;
	private float yaw, lastYaw;
	/** Softened body velocity (blocks/s) and turn rate (degrees/s). */
	private double svx, svy, svz;
	private float sturn;
	/** Smoothed speed of the player through the air (px/s), for the flutter. */
	private float airSpeed;
	private final float phase;
	long lastUsed;

	// inputs held between updates
	private float pitch, legSwing, scale = 1;
	private boolean legs;

	CapeSim(int seed) {
		phase = (seed * 0.6180339887f) % 1f * 6.2831855f;
	}

	/**
	 * Moves the sim to {@code nanos}. {@code ex/ey/ez} is where the player is now (blocks), {@code yaw}
	 * the body yaw (degrees), {@code pitch} the forward lean of the body (radians, as the model's
	 * x rotation), {@code legSwing} how far the leg furthest back swings back (radians), {@code legs}
	 * false when the legs are not under the cape (crouching, swimming), {@code scale} the player's size.
	 */
	void update(long nanos, double ex, double ey, double ez, float yaw, float pitch, float legSwing, boolean legs, float scale) {
		lastUsed = nanos;
		this.pitch = pitch;
		this.legSwing = Math.max(0, legSwing);
		this.legs = legs;
		this.scale = scale <= 0 ? 1 : scale;
		long span = nanos - lastNanos;
		double dx = ex - lastX, dy = ey - lastY, dz = ez - lastZ;
		double moved = dx * dx + dy * dy + dz * dz;
		double limit = TELEPORT_SPEED * Math.max(span, 16_666_667L) / 1e9;
		// first use, a long pause or a teleport: start hanging still
		if (!started || span < 0 || span > 300_000_000L || moved > limit * limit) {
			reset(ex, ey, ez, yaw, nanos);
			return;
		}
		if (span == 0) return;
		if (nanos - simNanos > MAX_STEPS * STEP_NANOS) simNanos = nanos - MAX_STEPS * STEP_NANOS;
		// each step moves the body to where it was at that step's moment, between the last input and this one
		float dyaw = wrap(yaw - lastYaw);
		while (simNanos + STEP_NANOS <= nanos) {
			simNanos += STEP_NANOS;
			double f = Math.max(0, Math.min(1, (simNanos - lastNanos) / (double) span));
			double tx = lastX + dx * f, ty = lastY + dy * f, tz = lastZ + dz * f;
			float tyaw = lastYaw + dyaw * (float) f;
			// Minecraft changes speed in jolts once per tick; the cape feels it a little softened
			svx += ((tx - this.ex) / STEP - svx) * SOFTEN;
			svy += ((ty - this.ey) / STEP - svy) * SOFTEN;
			svz += ((tz - this.ez) / STEP - svz) * SOFTEN;
			sturn += (wrap(tyaw - this.yaw) / STEP - sturn) * SOFTEN_TURN;
			step((float) (svx * STEP), (float) (svy * STEP), (float) (svz * STEP), sturn * STEP);
			this.ex = tx;
			this.ey = ty;
			this.ez = tz;
			this.yaw = tyaw;
		}
		lastNanos = nanos;
		lastX = ex;
		lastY = ey;
		lastZ = ez;
		lastYaw = yaw;
		blend((nanos - simNanos) / (float) STEP_NANOS);
	}

	private void reset(double ex, double ey, double ez, float yaw, long nanos) {
		started = true;
		lastNanos = simNanos = nanos;
		this.ex = lastX = ex;
		this.ey = lastY = ey;
		this.ez = lastZ = ez;
		this.yaw = lastYaw = yaw;
		airSpeed = 0;
		svx = svy = svz = 0;
		sturn = 0;
		float c = (float) Math.cos(pitch), s = (float) Math.sin(pitch);
		for (int i = 0; i < N; i++) {
			float yb = i * (float) Math.cos(REST_ANGLE), zb = i * (float) Math.sin(REST_ANGLE);
			x[i] = 0;
			y[i] = yb * c - zb * s;
			z[i] = yb * s + zb * c;
		}
		System.arraycopy(x, 0, px, 0, N);
		System.arraycopy(y, 0, py, 0, N);
		System.arraycopy(z, 0, pz, 0, N);
		System.arraycopy(x, 0, sx, 0, N);
		System.arraycopy(y, 0, sy, 0, N);
		System.arraycopy(z, 0, sz, 0, N);
		blend(0);
	}

	/** One fixed step: the body moved by (dx, dy, dz) blocks and turned by dyaw degrees. */
	private void step(float dx, float dy, float dz, float dyaw) {
		System.arraycopy(x, 0, sx, 0, N);
		System.arraycopy(y, 0, sy, 0, N);
		System.arraycopy(z, 0, sz, 0, N);
		float ppb = PX_PER_BLOCK / scale;

		// 1. the body moved and turned: express the points (which stay where they are in the world) in
		// its new frame, and note how each would have moved if it were stuck to the body
		float newYaw = (float) Math.toRadians(yaw + dyaw);
		yaw += dyaw;
		float cy = (float) Math.cos(newYaw), sy0 = (float) Math.sin(newYaw);
		// up and down the cape mostly just goes along: full inertia there makes every jump a whip
		float mx = -(dx * cy + dz * sy0) * ppb, my = dy * ppb * VERTICAL, mz = -(dx * sy0 - dz * cy) * ppb;
		float turn = (float) Math.toRadians(dyaw), ct = (float) Math.cos(turn), st = (float) Math.sin(turn);
		rx[0] = st * BACK - mx;
		ry[0] = -my;
		rz[0] = -(ct * BACK - BACK + mz);
		for (int i = 1; i < N; i++) {
			float ax = x[i], az = z[i] + BACK;
			x[i] = ct * ax - st * az + mx;
			z[i] = st * ax + ct * az - BACK + mz;
			y[i] += my;
			rx[i] = sx[i] - x[i];
			ry[i] = sy[i] - y[i];
			rz[i] = sz[i] - z[i];
			ax = px[i];
			az = pz[i] + BACK;
			px[i] = ct * ax - st * az + mx;
			pz[i] = st * ax + ct * az - BACK + mz;
			py[i] += my;
		}
		// how fast the player goes through the air (px/s), smoothed, for the gusts
		float bvx = rx[0] / STEP, bvy = ry[0] / STEP, bvz = rz[0] / STEP;
		float speed = (float) Math.sqrt(bvx * bvx + bvz * bvz) + 0.5f * Math.abs(dy * ppb / STEP);
		airSpeed += (Math.min(speed, 260) - airSpeed) * (1 - (float) Math.exp(-STEP / 0.35f));
		// a fall throws the cape up behind the back, like running does
		float lift = FALL_LIFT * Math.min(FALL_MAX, Math.max(0, -dy * ppb / STEP));

		// 2. forces: gravity, air on the face and along the cloth, gusts
		float pc = (float) Math.cos(pitch), ps = (float) Math.sin(pitch);
		float move = Math.min(1, airSpeed / 70f);
		float gust = GRAVITY * (GUST_STILL + GUST_MOVE * move + GUST_FAST * Math.min(1, Math.max(0, airSpeed - 70) / 120f));
		float t = (float) time;
		for (int i = 1; i < N; i++) {
			// normal of the cloth here: from the tangent, turned towards the back
			int a = i - 1, b = Math.min(N - 1, i + 1);
			float tx = x[b] - x[a], ty = y[b] - y[a], tz = z[b] - z[a];
			float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1e-4f) tl = 1e-4f;
			ty /= tl;
			tz /= tl;
			float ny = -tz, nz = ty;
			float nl = (float) Math.sqrt(ny * ny + nz * nz);
			if (nl < 1e-4f) {
				ny = -ps;
				nz = pc;
			} else {
				ny /= nl;
				nz /= nl;
			}
			// air relative to this row: still air sideways, moving up and down with the body, plus the lift of a fall
			float vx = (x[i] - px[i]) / STEP, vy = (y[i] - py[i]) / STEP - bvy, vz = (z[i] - pz[i]) / STEP - lift;
			float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
			if (vl > MAX_AIR) {
				float f = MAX_AIR / vl;
				vx *= f;
				vy *= f;
				vz *= f;
			}
			float vn = vy * ny + vz * nz;
			float fn = -(DRAG + DRAG_SQ * Math.abs(vn)) * vn;
			float ax = -DRAG_ALONG * vx, ay = GRAVITY - DRAG_ALONG * (vy - vn * ny) + fn * ny, az = -DRAG_ALONG * (vz - vn * nz) + fn * nz;

			// gusts: waves that run down the cape, slow and soft when standing, quicker when moving
			float k = i / (float) SEGMENTS;
			float still = (float) (Math.sin(2.1 * t - 0.22 * i + phase) + 0.6 * Math.sin(3.7 * t - 0.37 * i + phase * 1.7));
			float moving = (float) (Math.sin(13.0 * t - 0.36 * i + phase) + 0.35 * Math.sin(21.0 * t - 0.52 * i + phase * 2.3)
				+ 0.5 * Math.sin(7.3 * t - 0.2 * i + phase * 0.6));
			float flutter = gust * k * (still * (1 - move) + moving * move);
			ay += flutter * ny;
			az += flutter * nz;
			ax += gust * 0.35f * k * (float) Math.sin(4.7 * t - 0.25 * i + phase * 1.3);

			ix[i] = ax;
			iy[i] = ay;
			iz[i] = az;
		}
		// 3. move. Cloth damps itself: each row's motion leans a little towards its neighbours' and
		// towards the body's, which settles swings without changing where the cape wants to be
		dvx[0] = rx[0];
		dvy[0] = ry[0];
		dvz[0] = rz[0];
		for (int i = 1; i < N; i++) {
			dvx[i] = x[i] - px[i];
			dvy[i] = y[i] - py[i];
			dvz[i] = z[i] - pz[i];
		}
		for (int i = 1; i < N; i++) {
			int b = Math.min(N - 1, i + 1);
			float mxv = (dvx[i - 1] + dvx[b]) * 0.5f, myv = (dvy[i - 1] + dvy[b]) * 0.5f, mzv = (dvz[i - 1] + dvz[b]) * 0.5f;
			float vx = dvx[i] + (mxv - dvx[i]) * INNER + (rx[i] - dvx[i]) * WITH_BODY;
			float vy = dvy[i] + (myv - dvy[i]) * INNER + (ry[i] - dvy[i]) * WITH_BODY;
			float vz = dvz[i] + (mzv - dvz[i]) * INNER + (rz[i] - dvz[i]) * WITH_BODY;
			px[i] = x[i];
			py[i] = y[i];
			pz[i] = z[i];
			x[i] += vx * DAMPING + ix[i] * STEP * STEP;
			y[i] += vy * DAMPING + iy[i] * STEP * STEP;
			z[i] += vz * DAMPING + iz[i] * STEP * STEP;
		}
		time += STEP;

		// 4. sewn top and the shoulders' width
		for (int i = 1; i < N; i++) {
			if (i < SEWN.length) {
				float yb = i * (float) Math.cos(REST_ANGLE), zb = i * (float) Math.sin(REST_ANGLE);
				float ry = yb * pc - zb * ps, rz = yb * ps + zb * pc;
				y[i] += (ry - y[i]) * SEWN[i];
				z[i] += (rz - z[i]) * SEWN[i];
			}
			float side = 1 - i / (float) SEGMENTS;
			x[i] -= x[i] * SIDE * (0.15f + 0.85f * side * side);
		}

		// 5. keep it one piece, a little stiff, and out of the body
		for (int it = 0; it < ITERATIONS; it++) {
			x[0] = y[0] = z[0] = 0;
			for (int i = 0; i < SEGMENTS; i++) link(i, i + 1, 1, 1);
			for (int i = 1; i < SEGMENTS; i++) link(i - 1, i + 1, 2, BEND);
			collide(pc, ps);
		}
	}

	/** Pulls points a and b to their rest distance; the top point does not move. */
	private void link(int a, int b, float rest, float stiffness) {
		float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d < 1e-5f) return;
		float diff = (d - rest) / d * stiffness;
		if (a == 0) {
			x[b] -= dx * diff;
			y[b] -= dy * diff;
			z[b] -= dz * diff;
		} else {
			float h = diff * 0.5f;
			x[a] += dx * h;
			y[a] += dy * h;
			z[a] += dz * h;
			x[b] -= dx * h;
			y[b] -= dy * h;
			z[b] -= dz * h;
		}
	}

	private void collide(float pc, float ps) {
		float lc = (float) Math.cos(legSwing), ls = (float) Math.sin(legSwing);
		for (int i = 1; i < N; i++) {
			// into the body's frame
			float yb = y[i] * pc + z[i] * ps, zb = -y[i] * ps + z[i] * pc;
			float min = 0;
			if (legs && yb > HIP) min = Math.max(0, 2 * lc + (yb - HIP) * ls - 2 + 0.4f);
			boolean hit = false;
			if (zb < min) {
				zb = min;
				hit = true;
			}
			if (yb < -MAX_RISE * zb) {
				yb = -MAX_RISE * zb;
				hit = true;
			}
			if (hit) {
				y[i] = yb * pc - zb * ps;
				z[i] = yb * ps + zb * pc;
				// cloth on cloth: a little friction
				px[i] += (x[i] - px[i]) * 0.15f;
				py[i] += (y[i] - py[i]) * 0.15f;
				pz[i] += (z[i] - pz[i]) * 0.15f;
			}
		}
	}

	/** Shape between the last two steps, turned into the body's frame. */
	private void blend(float alpha) {
		float pc = (float) Math.cos(pitch), ps = (float) Math.sin(pitch);
		for (int i = 0; i < N; i++) {
			float bxx = sx[i] + (x[i] - sx[i]) * alpha;
			float byy = sy[i] + (y[i] - sy[i]) * alpha;
			float bzz = sz[i] + (z[i] - sz[i]) * alpha;
			bx[i] = bxx;
			by[i] = byy * pc + bzz * ps;
			bz[i] = -byy * ps + bzz * pc;
		}
	}

	private static float wrap(float degrees) {
		float d = degrees % 360;
		if (d >= 180) d -= 360;
		if (d < -180) d += 360;
		return d;
	}
}
