package dev.lumina.client.feature.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumina.client.Lumina;
import dev.lumina.client.online.LuminaOnline;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Lumina's own particle effects around your player, and around other Lumina players near you (what
 * they wear comes from Lumina's server, see LuminaOnline; nothing goes to the game server).
 * Particles are camera facing sprites from a small atlas drawn with Java2D at startup.
 *
 * <p>There are two particle systems: one in the world around you, and one for the 3D previews of
 * your player in menus (the inventory, the Cosmetics page), which runs only while a preview is shown.
 */
public final class ParticleEffects {
	public enum Sprite { GLOW, SPARKLE, HEART, HEART_SHINE, SNOWFLAKE, SNOWFLAKE_2, SNOW_SOFT, PETAL, LEAF, BUTTERFLY, BUBBLE, DOT }

	/** {@code tinted}: uses the color from the settings; {@code iconColor}: how the menu shows it otherwise. */
	public enum Effect {
		HEARTS("Hearts", false, Sprite.HEART, 0xFFFF5C8A),
		SAKURA("Sakura", false, Sprite.PETAL, 0xFFFFB7CE),
		SNOW("Snow", false, Sprite.SNOWFLAKE, 0xFFEAF4FF),
		AUTUMN("Autumn", false, Sprite.LEAF, 0xFFF08A3A),
		BUTTERFLIES("Butterflies", false, Sprite.BUTTERFLY, 0xFFC9A6FF),
		FIREFLIES("Fireflies", false, Sprite.GLOW, 0xFFE4FF7A),
		BUBBLES("Bubbles", false, Sprite.BUBBLE, 0xFFBDEBFF),
		STARFALL("Starfall", false, Sprite.SPARKLE, 0xFFFFE6A3),
		GALAXY("Galaxy", false, Sprite.SPARKLE, 0xFFB9A6FF),
		ORBIT("Orbit", true, Sprite.SPARKLE, 0xFFF4F8FF),
		SPARKS("Sparks", true, Sprite.DOT, 0xFFF4F8FF);

		public final String title;
		public final boolean tinted;
		public final Sprite icon;
		public final int iconColor;

		Effect(String title, boolean tinted, Sprite icon, int iconColor) {
			this.title = title;
			this.tinted = tinted;
			this.icon = icon;
			this.iconColor = iconColor;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	public static final int CELL = 128;
	public static final Identifier ATLAS = Lumina.id("cosmetics/particles");
	private static final int MAX_PARTICLES = 600;
	private static final float PREVIEW_HEIGHT = 1.8f;
	private static final int DEFAULT_COLOR = 0xFFB9C6FF;
	private static final double OTHERS_RANGE = 32;
	private static final int OTHERS_MAX = 8;
	private static final int OTHERS_PARTICLES = 300;

	private final CosmeticsModule module;
	private final Emitter world = new Emitter(MAX_PARTICLES);
	private final Emitter preview = new Emitter(MAX_PARTICLES);
	private long previewUntil;
	private boolean atlasReady;
	private Vec3 lastPos;
	private final Map<UUID, Wearer> others = new HashMap<>();

	ParticleEffects(CosmeticsModule module) {
		this.module = module;
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	public Effect effect() {
		return byId(module.effect.get());
	}

	private static @Nullable Effect byId(String id) {
		for (Effect e : Effect.values()) if (e.id().equals(id)) return e;
		return null;
	}

	private int color() {
		return module.effectColor.color() | 0xFF000000;
	}

	// ------------------------------------------------------------------
	// simulation (client ticks)
	// ------------------------------------------------------------------

	void tick() {
		LocalPlayer p = Minecraft.getInstance().player;
		Effect e = effect();
		boolean on = e != null && module.enabled();
		world.density = preview.density = module.density.getFloat();
		world.color = preview.color = color();

		if (p == null) {
			world.particles.clear();
			lastPos = null;
		} else {
			world.update(p.getX(), p.getY(), p.getZ());
			Vec3 pos = p.position();
			double speed = lastPos == null ? 0 : pos.distanceTo(lastPos);
			lastPos = pos;
			if (on && !p.isSpectator() && !p.isInvisible()) world.spawn(e, p.getX(), p.getY(), p.getZ(), p.getBbHeight(), speed);
		}
		tickOthers(p);

		// the preview system only runs while a menu shows your player
		if (on && Util.getMillis() < previewUntil) {
			preview.update(0, 0, 0);
			preview.spawn(e, 0, 0, 0, PREVIEW_HEIGHT, 0);
		} else {
			preview.particles.clear();
		}
	}

	/** The effects of other Lumina players: within 32 blocks, the closest 8, as their owners set them. */
	private void tickOthers(@Nullable LocalPlayer p) {
		ClientLevel level = Minecraft.getInstance().level;
		if (p == null || level == null) {
			others.clear();
			return;
		}
		List<AbstractClientPlayer> near = new ArrayList<>();
		for (AbstractClientPlayer o : level.players()) {
			if (o == p || o.isSpectator() || o.isInvisible() || o.distanceToSqr(p) > OTHERS_RANGE * OTHERS_RANGE) continue;
			LuminaOnline.Remote r = LuminaOnline.remote(o.getUUID());
			if (r != null && r.effect() != null && byId(r.effect().split(":")[0]) != null) near.add(o);
		}
		near.sort(Comparator.comparingDouble(o -> o.distanceToSqr(p)));
		Set<UUID> active = new HashSet<>();
		for (AbstractClientPlayer o : near.subList(0, Math.min(OTHERS_MAX, near.size()))) {
			String[] spec = LuminaOnline.remote(o.getUUID()).effect().split(":");
			Wearer w = others.computeIfAbsent(o.getUUID(), k -> new Wearer());
			w.effect = byId(spec[0]);
			w.emitter.color = spec.length > 1 ? parseColor(spec[1]) : DEFAULT_COLOR;
			w.entityId = o.getId();
			active.add(o.getUUID());
			w.emitter.update(o.getX(), o.getY(), o.getZ());
			Vec3 pos = o.position();
			double speed = w.lastPos == null ? 0 : pos.distanceTo(w.lastPos);
			w.lastPos = pos;
			w.emitter.spawn(w.effect, o.getX(), o.getY(), o.getZ(), o.getBbHeight(), speed);
		}
		// out of range or effect taken off: let the particles fade out, then forget them
		others.entrySet().removeIf(en -> {
			if (active.contains(en.getKey())) return false;
			Wearer w = en.getValue();
			w.effect = null;
			if (w.lastPos != null) w.emitter.update(w.lastPos.x, w.lastPos.y, w.lastPos.z);
			else w.emitter.particles.clear();
			return w.emitter.particles.isEmpty();
		});
	}

	private static int parseColor(String hex) {
		try {
			return Integer.parseInt(hex, 16) | 0xFF000000;
		} catch (NumberFormatException e) {
			return DEFAULT_COLOR;
		}
	}

	/** Another player's effect: its own particles, plus what to draw around them every frame. */
	private final class Wearer {
		final Emitter emitter = new Emitter(OTHERS_PARTICLES);
		/** Null while the particles fade out. */
		@Nullable Effect effect;
		int entityId;
		@Nullable Vec3 lastPos;
	}

	private static final class Particle {
		double x, y, z, px, py, pz, vx, vy, vz;
		float size, rot, spin, gravity, drag = 0.96f;
		int age, life, color;
		Sprite sprite;
		/** 3D tumbling: the sprite's width follows |cos(flip)|. */
		float flip, flipSpeed;
		/** Gentle swaying drift around the path. */
		float swayAmp, swayFreq, swayPhase;
		/** Scales in with a little overshoot. */
		boolean pop;
		/** Brightness pulsing speed, 0 = steady. */
		float blink;
		int kind;
	}

	/** Particle kinds with their own behavior. */
	private static final int PLAIN = 0, HEART = 1, FIREFLY = 2, BUTTERFLY = 3, STAR = 4, BUBBLE = 5, CRYSTAL = 6;

	/** One particle system; positions are in world space, or around (0, 0, 0) for previews. */
	private final class Emitter {
		final List<Particle> particles = new ArrayList<>();
		final Random random = new Random();
		final int max;
		int ticks;
		/** Amount and the color of tinted effects, set before each spawn. */
		float density = 1;
		int color = DEFAULT_COLOR;

		Emitter(int max) {
			this.max = max;
		}

		void update(double ox, double oy, double oz) {
			ticks++;
			List<Particle> born = null;
			for (int i = particles.size() - 1; i >= 0; i--) {
				Particle q = particles.get(i);
				q.px = q.x;
				q.py = q.y;
				q.pz = q.z;
				if (q.kind == BUTTERFLY) steerButterfly(q, ox, oy, oz);
				if (q.kind == FIREFLY) {
					q.vx += around(0.0025);
					q.vy += around(0.0018);
					q.vz += around(0.0025);
				}
				q.x += q.vx;
				q.y += q.vy;
				q.z += q.vz;
				if (q.swayAmp > 0) {
					// moves along a circle, so the sway never drifts away
					float a0 = q.swayPhase + q.age * q.swayFreq, a1 = a0 + q.swayFreq;
					q.x += q.swayAmp * (Mth.sin(a1) - Mth.sin(a0));
					q.z += q.swayAmp * (Mth.cos(a1) - Mth.cos(a0));
				}
				q.vx *= q.drag;
				q.vy = q.vy * q.drag - q.gravity;
				q.vz *= q.drag;
				q.rot += q.spin;
				q.flip += q.flipSpeed;
				q.age++;
				if (q.kind == STAR && q.age < q.life) {
					if (born == null) born = new ArrayList<>();
					born.add(trail(q));
				}
				if (q.age >= q.life) {
					particles.remove(i);
					if (q.kind == STAR || q.kind == BUBBLE) {
						if (born == null) born = new ArrayList<>();
						burst(born, q);
					}
				}
			}
			if (born != null) for (Particle q : born) push(q);
		}

		private void steerButterfly(Particle q, double ox, double oy, double oz) {
			// flutter on a wandering heading and turn back when too far from you
			double dx = q.x - ox, dz = q.z - oz;
			double dist = Math.sqrt(dx * dx + dz * dz);
			q.rot += around(0.35);
			float heading = q.rot;
			if (dist > 1.25) heading = (float) Math.atan2(-dz, -dx);
			else if (dist < 0.45) heading = (float) Math.atan2(dz, dx);
			q.vx = q.vx * 0.8 + Math.cos(heading) * 0.03 * 0.2;
			q.vz = q.vz * 0.8 + Math.sin(heading) * 0.03 * 0.2;
			double targetY = oy + 0.9 + 0.5 * Math.sin(q.age * 0.03 + q.swayPhase);
			q.vy = q.vy * 0.85 + (targetY - q.y) * 0.01 + Math.sin(q.age * 0.6 + q.swayPhase) * 0.006;
		}

		private Particle trail(Particle star) {
			Particle t = make(Sprite.GLOW, star.x, star.y, star.z, 9, star.size * 0.85f, 0xFFFFC966);
			t.rot = 0;
			t.drag = 0.8f;
			return t;
		}

		private void burst(List<Particle> out, Particle from) {
			int n = from.kind == STAR ? 5 : 4;
			for (int i = 0; i < n; i++) {
				Particle s = make(Sprite.DOT, from.x, from.y, from.z, 5 + random.nextInt(4), 0.025f + random.nextFloat() * 0.015f,
					from.kind == STAR ? 0xFFFFF1C4 : 0xFFF2FBFF);
				double a = random.nextDouble() * Math.PI * 2;
				s.vx = Math.cos(a) * 0.035;
				s.vz = Math.sin(a) * 0.035;
				s.vy = around(0.03) + (from.kind == STAR ? 0.02 : 0);
				s.drag = 0.8f;
				out.add(s);
			}
		}

		private boolean chance(float perTick) {
			return random.nextFloat() < perTick * density;
		}

		private int count(float perTick) {
			float n = perTick * density;
			int whole = (int) n;
			return whole + (random.nextFloat() < n - whole ? 1 : 0);
		}

		private double around(double r) {
			return (random.nextDouble() * 2 - 1) * r;
		}

		private float range(float min, float max) {
			return min + random.nextFloat() * (max - min);
		}

		private int pick(int... colors) {
			return colors[random.nextInt(colors.length)];
		}

		private Particle make(Sprite sprite, double x, double y, double z, int life, float size, int color) {
			Particle q = new Particle();
			q.x = q.px = x;
			q.y = q.py = y;
			q.z = q.pz = z;
			q.sprite = sprite;
			q.life = life;
			q.size = size;
			q.color = color;
			q.rot = random.nextFloat() * Mth.TWO_PI;
			q.flip = random.nextFloat() * Mth.TWO_PI;
			q.swayPhase = random.nextFloat() * Mth.TWO_PI;
			return q;
		}

		private void push(Particle q) {
			if (particles.size() >= max) particles.remove(0);
			particles.add(q);
		}

		private Particle add(Sprite sprite, double x, double y, double z, int life, float size, int color) {
			Particle q = make(sprite, x, y, z, life, size, color);
			push(q);
			return q;
		}

		private int countKind(int kind) {
			int n = 0;
			for (Particle q : particles) if (q.kind == kind) n++;
			return n;
		}

		void spawn(Effect e, double x, double y, double z, float h, double speed) {
			switch (e) {
				case HEARTS -> {
					if (chance(0.1f)) {
						double a = random.nextDouble() * Math.PI * 2, r = range(0.35f, 0.6f);
						Particle q = add(Sprite.HEART, x + Math.cos(a) * r, y + h * range(0.55f, 1.0f), z + Math.sin(a) * r, 48 + random.nextInt(16),
							range(0.13f, 0.2f), pick(0xFFFF4D7A, 0xFFFF6FA3, 0xFFE8304F, 0xFFFF8FB8));
						q.kind = HEART;
						q.vy = 0.016 + random.nextDouble() * 0.01;
						q.drag = 0.985f;
						q.swayAmp = 0.06f;
						q.swayFreq = range(0.08f, 0.13f);
						q.rot = range(-0.25f, 0.25f);
						q.spin = range(-0.004f, 0.004f);
						q.pop = true;
					}
					if (chance(0.04f)) {
						Particle s = add(Sprite.SPARKLE, x + around(0.55), y + h * range(0.5f, 1.05f), z + around(0.55), 14, range(0.04f, 0.06f), 0xFFFFE3EE);
						s.vy = 0.01;
						s.spin = 0.08f;
					}
				}
				case SAKURA -> {
					for (int i = count(0.38f); i > 0; i--) {
						double a = random.nextDouble() * Math.PI * 2, r = range(0.2f, 0.95f);
						Particle q = add(Sprite.PETAL, x + Math.cos(a) * r, y + h + range(0.25f, 0.7f), z + Math.sin(a) * r, 80 + random.nextInt(25),
							range(0.07f, 0.11f), pick(0xFFFFB7CE, 0xFFFFC9D9, 0xFFF79AB8, 0xFFFFDDE7, 0xFFFFF0F4));
						// a slow swirl around you while they fall
						q.vx = -Math.sin(a) * 0.008;
						q.vz = Math.cos(a) * 0.008;
						q.vy = -0.008;
						q.gravity = 0.00035f;
						q.drag = 0.982f;
						q.swayAmp = range(0.18f, 0.32f);
						q.swayFreq = range(0.06f, 0.11f);
						q.flipSpeed = range(0.07f, 0.17f) * (random.nextBoolean() ? 1 : -1);
						q.spin = range(-0.07f, 0.07f);
					}
				}
				case SNOW -> {
					for (int i = count(0.95f); i > 0; i--) {
						double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 1.15;
						boolean crystal = random.nextFloat() < 0.45f;
						Particle q = add(crystal ? (random.nextBoolean() ? Sprite.SNOWFLAKE : Sprite.SNOWFLAKE_2) : Sprite.SNOW_SOFT,
							x + Math.cos(a) * r, y + h + range(0.55f, 0.95f), z + Math.sin(a) * r, 75 + random.nextInt(20),
							crystal ? range(0.065f, 0.11f) : range(0.035f, 0.06f), crystal ? 0xFFEAF4FF : 0xE6FFFFFF);
						q.vy = -range(0.01f, 0.018f);
						q.gravity = 0.0004f;
						q.drag = 0.985f;
						q.swayAmp = range(0.08f, 0.16f);
						q.swayFreq = range(0.06f, 0.12f);
						q.spin = range(-0.04f, 0.04f);
						if (crystal) {
							q.kind = CRYSTAL;
							q.flipSpeed = range(0.03f, 0.09f);
							q.blink = range(0.25f, 0.45f);
						}
					}
				}
				case AUTUMN -> {
					for (int i = count(0.3f); i > 0; i--) {
						double a = random.nextDouble() * Math.PI * 2, r = range(0.2f, 1.0f);
						Particle q = add(Sprite.LEAF, x + Math.cos(a) * r, y + h + range(0.3f, 0.7f), z + Math.sin(a) * r, 80 + random.nextInt(25),
							range(0.09f, 0.14f), pick(0xFFE8762B, 0xFFD94A1E, 0xFFF2B33D, 0xFFC5702A, 0xFFC93C20, 0xFFE89A2F));
						q.vy = -0.01;
						q.gravity = 0.00045f;
						q.drag = 0.98f;
						q.swayAmp = range(0.22f, 0.38f);
						q.swayFreq = range(0.05f, 0.09f);
						q.flipSpeed = range(0.06f, 0.14f) * (random.nextBoolean() ? 1 : -1);
						q.spin = range(-0.09f, 0.09f);
					}
				}
				case BUTTERFLIES -> {
					if (countKind(BUTTERFLY) < Math.round(3 * density) && chance(0.04f)) {
						double a = random.nextDouble() * Math.PI * 2;
						Particle q = add(Sprite.BUTTERFLY, x + Math.cos(a) * 0.9, y + h * range(0.4f, 0.9f), z + Math.sin(a) * 0.9, 220 + random.nextInt(120),
							range(0.16f, 0.2f), pick(0xFF9FD8FF, 0xFFFFB86B, 0xFFE3A6FF, 0xFFFFE680, 0xFFFF9CC2));
						q.kind = BUTTERFLY;
						q.rot = (float) (a + Math.PI);
						q.drag = 1f;
					}
				}
				case FIREFLIES -> {
					if (countKind(FIREFLY) < Math.round(14 * density) && chance(0.2f)) {
						double a = random.nextDouble() * Math.PI * 2, r = range(0.45f, 1.1f);
						Particle q = add(Sprite.GLOW, x + Math.cos(a) * r, y + range(0.15f, h + 0.3f), z + Math.sin(a) * r, 90 + random.nextInt(60),
							range(0.13f, 0.18f), pick(0xFFE4FF7A, 0xFFD2FF5E, 0xFFFFF27A));
						q.kind = FIREFLY;
						q.drag = 0.92f;
						q.blink = range(0.06f, 0.12f);
						q.rot = 0;
					}
				}
				case BUBBLES -> {
					for (int i = count(0.16f); i > 0; i--) {
						double a = random.nextDouble() * Math.PI * 2, r = range(0.35f, 0.75f);
						Particle q = add(Sprite.BUBBLE, x + Math.cos(a) * r, y + range(0.1f, h * 0.7f), z + Math.sin(a) * r, 45 + random.nextInt(35),
							range(0.06f, 0.13f), pick(0xFFBDEBFF, 0xFFD9C8FF, 0xFFFFD6F0, 0xFFC8FFF0, 0xFFFFF4C8));
						q.kind = BUBBLE;
						q.vy = range(0.012f, 0.02f);
						q.drag = 0.99f;
						q.swayAmp = range(0.05f, 0.1f);
						q.swayFreq = range(0.1f, 0.18f);
						q.rot = range(-0.3f, 0.3f);
						q.pop = true;
					}
				}
				case STARFALL -> {
					if (chance(0.09f)) {
						double a = random.nextDouble() * Math.PI * 2, r = range(0.5f, 1.2f);
						double dir = random.nextDouble() * Math.PI * 2;
						Particle q = add(Sprite.SPARKLE, x + Math.cos(a) * r, y + h + range(1.0f, 1.6f), z + Math.sin(a) * r, 16 + random.nextInt(6),
							range(0.12f, 0.15f), pick(0xFFFFF6D5, 0xFFFFFFFF, 0xFFFFE9A8));
						q.kind = STAR;
						q.vx = Math.cos(dir) * 0.045;
						q.vz = Math.sin(dir) * 0.045;
						q.vy = -0.1;
						q.drag = 1f;
						q.spin = 0.2f;
					}
				}
				case SPARKS -> {
					for (int i = count(0.9f); i > 0; i--) {
						double a = random.nextDouble() * Math.PI * 2;
						Particle q = add(Sprite.DOT, x + Math.cos(a) * 0.45, y + random.nextDouble() * h, z + Math.sin(a) * 0.45, 6 + random.nextInt(6),
							0.05f + random.nextFloat() * 0.04f, color);
						q.vx = around(0.06);
						q.vy = around(0.06);
						q.vz = around(0.06);
						q.drag = 0.8f;
					}
				}
				case GALAXY -> {
					// stars drifting off the spiral (the spiral itself is drawn every frame)
					if (chance(0.5f)) {
						double a = random.nextDouble() * Math.PI * 2;
						Particle q = add(Sprite.SPARKLE, x + Math.cos(a) * 0.6, y + random.nextDouble() * h, z + Math.sin(a) * 0.6, 30,
							0.06f + random.nextFloat() * 0.05f, pick(0xFFB9A6FF, 0xFF7FB6FF, 0xFFFF9BE8, 0xFFFFFFFF));
						q.vx = Math.cos(a) * 0.01;
						q.vz = Math.sin(a) * 0.01;
						q.vy = 0.004;
					}
				}
				default -> {
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// rendering
	// ------------------------------------------------------------------

	/** Where sprites go: a vertex consumer plus the camera facing axes. */
	private record Sink(VertexConsumer vc, PoseStack.Pose pose, Vector3f right, Vector3f up) {
	}

	/** An every-frame effect around a player, relative to the camera. */
	private record Shape(Effect effect, double x, double y, double z, float height, int color) {
	}

	private void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) return;
		Effect e = effect();
		// yours as set on the Cosmetics page (in first person only if asked for); others always
		boolean own = module.enabled() && (!mc.options.getCameraType().isFirstPerson() || module.firstPerson.on())
			&& (!world.particles.isEmpty() || drawsEveryFrame(e));
		if (!own && others.isEmpty()) return;
		ensureAtlas();
		Vec3 cam = context.levelState().cameraRenderState.pos;
		Quaternionf rot = mc.gameRenderer.mainCamera().rotation();
		Vector3f right = rot.transform(new Vector3f(1, 0, 0)), up = rot.transform(new Vector3f(0, 1, 0));
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		List<Emitter> emitters = new ArrayList<>();
		List<Shape> shapes = new ArrayList<>();
		if (own) {
			emitters.add(world);
			if (e != null && !p.isInvisible() && !p.isSpectator()) shapes.add(shape(e, p, partial, cam, color()));
		}
		for (Wearer w : others.values()) {
			emitters.add(w.emitter);
			if (w.effect != null && drawsEveryFrame(w.effect) && mc.level.getEntity(w.entityId) instanceof AbstractClientPlayer o && !o.isInvisible()) {
				shapes.add(shape(w.effect, o, partial, cam, w.emitter.color));
			}
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.eyes(ATLAS),
			(pose, vc) -> draw(new Sink(vc, pose, right, up), emitters, shapes, partial, cam));
	}

	private static Shape shape(Effect e, Entity player, float partial, Vec3 cam, int color) {
		Vec3 pos = player.getPosition(partial);
		return new Shape(e, pos.x - cam.x, pos.y - cam.y, pos.z - cam.z, player.getBbHeight(), color);
	}

	private void draw(Sink sink, List<Emitter> emitters, List<Shape> shapes, float partial, Vec3 cam) {
		for (Emitter em : emitters) drawParticles(sink, em, partial, cam.x, cam.y, cam.z);
		for (Shape s : shapes) drawShapes(sink, s.effect, s.x, s.y, s.z, s.height, s.color);
	}

	private static boolean drawsEveryFrame(Effect e) {
		return e == Effect.ORBIT || e == Effect.GALAXY;
	}

	/**
	 * Draws the effect around a player shown in a menu (inventory, Cosmetics page). Called while
	 * the preview's entity is rendered, with the same pose, so the particles sit around the model
	 * and are hidden behind it like in the world.
	 */
	public void renderPreview(PoseStack poseStack, SubmitNodeCollector collector) {
		Effect e = effect();
		if (e == null || !module.enabled()) return;
		previewUntil = Util.getMillis() + 750;
		ensureAtlas();
		// the menu camera looks straight at the model; undo the pose to face it
		Matrix3f inverse = new Matrix3f(poseStack.last().pose()).invert();
		Vector3f right = inverse.transform(new Vector3f(1, 0, 0)).normalize();
		Vector3f up = inverse.transform(new Vector3f(0, -1, 0)).normalize();
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		collector.submitCustomGeometry(poseStack, RenderTypes.eyes(ATLAS), (pose, vc) -> {
			Sink sink = new Sink(vc, pose, right, up);
			drawParticles(sink, preview, partial, 0, 0, 0);
			drawShapes(sink, e, 0, 0, 0, PREVIEW_HEIGHT, color());
		});
	}

	private void drawParticles(Sink sink, Emitter emitter, float partial, double cx, double cy, double cz) {
		for (Particle q : emitter.particles) {
			float age = q.age + partial;
			float t = age / q.life;
			float fade = t < 0.12f ? t / 0.12f : 1 - Math.max(0, (t - 0.65f) / 0.35f);
			// bubbles and shooting stars stay bright until they pop
			if (q.kind == BUBBLE || q.kind == STAR) fade = Math.min(1, t / 0.12f);
			float x = (float) (Mth.lerp(partial, q.px, q.x) - cx), y = (float) (Mth.lerp(partial, q.py, q.y) - cy), z = (float) (Mth.lerp(partial, q.pz, q.z) - cz);
			float size = q.size;
			if (q.pop) size *= popScale(Math.min(1, age / 7f));
			if (q.blink > 0) {
				float b = Mth.sin(age * q.blink + q.swayPhase);
				fade *= q.kind == FIREFLY ? 0.12f + 0.88f * b * b : 0.7f + 0.3f * b;
			}
			float rot = q.rot + q.spin * partial;
			float width = q.flipSpeed != 0 ? 0.2f + 0.8f * Math.abs(Mth.cos(q.flip + q.flipSpeed * partial)) : 1;
			switch (q.kind) {
				case HEART -> {
					size *= 1 + 0.07f * Mth.sin(age * 0.45f); // a soft heartbeat
					sprite(sink, x, y, z, size * 2.1f, 1, rot, Sprite.GLOW, q.color, fade * 0.28f);
					sprite(sink, x, y, z, size, 1, rot, Sprite.HEART, q.color, fade);
					sprite(sink, x, y, z, size, 1, rot, Sprite.HEART_SHINE, 0xFFFFFFFF, fade * 0.8f);
				}
				case FIREFLY -> {
					sprite(sink, x, y, z, size, 1, 0, Sprite.GLOW, q.color, fade * 0.8f);
					sprite(sink, x, y, z, size * 0.28f, 1, 0, Sprite.DOT, 0xFFFFFFE6, fade);
				}
				case BUTTERFLY -> {
					// wings open and close; the sprite stays upright
					float flap = 0.18f + 0.82f * Math.abs(Mth.sin(age * 0.85f + q.swayPhase));
					float tilt = 0.25f * Mth.sin(age * 0.05f + q.swayPhase);
					sprite(sink, x, y, z, size, flap, tilt, Sprite.BUTTERFLY, q.color, fade);
				}
				case STAR -> {
					sprite(sink, x, y, z, size * 2.4f, 1, 0, Sprite.GLOW, 0xFFFFE2A0, fade * 0.45f);
					sprite(sink, x, y, z, size, 1, rot, Sprite.SPARKLE, q.color, fade);
				}
				case BUBBLE -> {
					float grow = 1 + 0.25f * t;
					sprite(sink, x, y, z, size * grow, 1, rot, Sprite.BUBBLE, q.color, fade);
				}
				default -> sprite(sink, x, y, z, size, width, rot, q.sprite, q.color, fade);
			}
		}
	}

	private static float popScale(float t) {
		// ease out with a small overshoot
		float c = 1.9f;
		float k = t - 1;
		return 1 + (c + 1) * k * k * k + c * k * k;
	}

	/** Effects drawn as shapes every frame (not simulated), around a player at (x, y, z). */
	private void drawShapes(Sink sink, Effect e, double x, double y, double z, float h, int color) {
		double time = (System.currentTimeMillis() % 100_000L) / 1000.0;
		switch (e) {
			case ORBIT -> {
				for (int i = 0; i < 3; i++) {
					for (int trail = 0; trail < 6; trail++) {
						double a = time * 2.4 - trail * 0.12 + i * Math.PI * 2 / 3;
						double yy = y + h * 0.55 + Math.sin(time * 1.6 + i * 2) * 0.25;
						float size = 0.11f * (1 - trail / 7f);
						sprite(sink, (float) (x + Math.cos(a) * 0.62), (float) yy, (float) (z + Math.sin(a) * 0.62), size, 1, (float) time * 2,
							trail == 0 ? Sprite.SPARKLE : Sprite.GLOW, color, 1 - trail / 6f);
					}
				}
			}
			case GALAXY -> {
				int[] colors = {0xFFB9A6FF, 0xFF7FB6FF, 0xFFFF9BE8};
				int n = 26;
				for (int arm = 0; arm < 2; arm++) {
					for (int i = 0; i < n; i++) {
						float k = i / (float) n;
						double a = time * 1.5 + k * Math.PI * 3 + arm * Math.PI;
						double r = 0.45 + 0.15 * Math.sin(time + k * 6);
						sprite(sink, (float) (x + Math.cos(a) * r), (float) (y + k * h * 1.05), (float) (z + Math.sin(a) * r),
							0.05f + 0.03f * (float) Math.sin(time * 3 + i), 1, (float) time, Sprite.SPARKLE, colors[(i + arm) % 3], 0.9f);
					}
				}
			}
			default -> {
			}
		}
	}

	/** A camera facing quad; {@code width} squeezes it sideways (tumbling petals, flapping wings). */
	private static void sprite(Sink sink, float x, float y, float z, float size, float width, float rot, Sprite sprite, int color, float alpha) {
		if (alpha <= 0.01f || size <= 0) return;
		Vector3f right = sink.right, up = sink.up;
		float c = Mth.cos(rot) * size, s = Mth.sin(rot) * size;
		// rotated billboard axes
		float rx = (right.x * c + up.x * s) * width, ry = (right.y * c + up.y * s) * width, rz = (right.z * c + up.z * s) * width;
		float ux = up.x * c - right.x * s, uy = up.y * c - right.y * s, uz = up.z * c - right.z * s;
		float u0 = sprite.ordinal() / (float) Sprite.values().length, u1 = (sprite.ordinal() + 1) / (float) Sprite.values().length;
		int argb = ARGB.color(Math.round(ARGB.alpha(color) * Math.min(1, alpha)), color);
		VertexConsumer vc = sink.vc;
		PoseStack.Pose pose = sink.pose;
		vertex(vc, pose, x - rx + ux, y - ry + uy, z - rz + uz, u0, 0, argb);
		vertex(vc, pose, x - rx - ux, y - ry - uy, z - rz - uz, u0, 1, argb);
		vertex(vc, pose, x + rx - ux, y + ry - uy, z + rz - uz, u1, 1, argb);
		vertex(vc, pose, x + rx + ux, y + ry + uy, z + rz + uz, u1, 0, argb);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
		vc.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
	}

	// ------------------------------------------------------------------
	// sprite atlas
	// ------------------------------------------------------------------

	private void ensureAtlas() {
		if (atlasReady) return;
		atlasReady = true;
		Minecraft.getInstance().getTextureManager().register(ATLAS, atlasTexture());
	}

	private static DynamicTexture atlasTexture() {
		int n = Sprite.values().length, c = CELL;
		BufferedImage img = new BufferedImage(c * n, c, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		for (Sprite s : Sprite.values()) {
			Graphics2D cell = (Graphics2D) g.create(s.ordinal() * c, 0, c, c);
			paint(cell, s, c);
			cell.dispose();
		}
		g.dispose();
		NativeImage image = new NativeImage(img.getWidth(), img.getHeight(), true);
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) image.setPixel(x, y, img.getRGB(x, y));
		}
		return new DynamicTexture(ATLAS::toString, image) {
			{
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		};
	}

	private static Color white(int alpha) {
		return new Color(255, 255, 255, alpha);
	}

	private static Color gray(int v, int alpha) {
		return new Color(v, v, v, alpha);
	}

	/**
	 * White or gray sprites (tinted by the vertex color), each in a {@code c} x {@code c} cell. Gray
	 * parts come out as darker shades of the particle's color.
	 */
	private static void paint(Graphics2D g, Sprite s, int c) {
		float m = c / 2f;
		Color clear = white(0);
		switch (s) {
			case GLOW -> {
				g.setPaint(new RadialGradientPaint(m, m, m, new float[] {0f, 0.22f, 1f}, new Color[] {white(255), white(150), clear}));
				g.fillRect(0, 0, c, c);
			}
			case SPARKLE -> {
				g.setPaint(new RadialGradientPaint(m, m, m * 0.6f, new float[] {0f, 1f}, new Color[] {white(150), clear}));
				g.fillRect(0, 0, c, c);
				Path2D star = new Path2D.Float();
				float r = m * 0.95f, w = m * 0.13f;
				star.moveTo(m, m - r);
				star.quadTo(m + w, m - w, m + r, m);
				star.quadTo(m + w, m + w, m, m + r);
				star.quadTo(m - w, m + w, m - r, m);
				star.quadTo(m - w, m - w, m, m - r);
				g.setColor(white(255));
				g.fill(star);
			}
			case HEART, HEART_SHINE -> {
				Path2D heart = heart(c);
				if (s == Sprite.HEART) {
					// lighter at the top, a touch deeper towards the tip
					g.setPaint(new GradientPaint(0, c * 0.2f, white(255), 0, c * 0.9f, gray(200, 255)));
					g.fill(heart);
					g.setClip(heart);
					g.setPaint(new RadialGradientPaint(m, c * 0.95f, c * 0.55f, new float[] {0f, 1f}, new Color[] {gray(150, 120), gray(150, 0)}));
					g.fillRect(0, 0, c, c);
				} else {
					g.setClip(heart);
					g.setPaint(new RadialGradientPaint(c * 0.32f, c * 0.33f, c * 0.2f, new float[] {0f, 0.5f, 1f}, new Color[] {white(235), white(120), clear}));
					g.fill(new Ellipse2D.Float(c * 0.14f, c * 0.18f, c * 0.36f, c * 0.3f));
					g.setPaint(new RadialGradientPaint(c * 0.66f, c * 0.3f, c * 0.06f, new float[] {0f, 1f}, new Color[] {white(200), clear}));
					g.fill(new Ellipse2D.Float(c * 0.58f, c * 0.22f, c * 0.16f, c * 0.16f));
				}
			}
			case SNOWFLAKE -> {
				g.setColor(white(255));
				g.setStroke(new BasicStroke(c * 0.055f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3 - Math.PI / 2;
					float ex = m + (float) Math.cos(a) * m * 0.88f, ey = m + (float) Math.sin(a) * m * 0.88f;
					g.draw(new Line2D.Float(m, m, ex, ey));
					for (float at : new float[] {0.42f, 0.66f}) {
						float bx = m + (float) Math.cos(a) * m * at, by = m + (float) Math.sin(a) * m * at;
						float len = m * (at < 0.5f ? 0.26f : 0.18f);
						for (int side = -1; side <= 1; side += 2) {
							double b = a + side * 0.75;
							g.draw(new Line2D.Float(bx, by, bx + (float) Math.cos(b) * len, by + (float) Math.sin(b) * len));
						}
					}
				}
				g.fill(new Ellipse2D.Float(m - c * 0.06f, m - c * 0.06f, c * 0.12f, c * 0.12f));
			}
			case SNOWFLAKE_2 -> {
				// a star plate: short arms with little diamonds at the tips
				g.setColor(white(255));
				g.setStroke(new BasicStroke(c * 0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				Path2D hex = new Path2D.Float();
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3;
					float hx = m + (float) Math.cos(a) * m * 0.3f, hy = m + (float) Math.sin(a) * m * 0.3f;
					if (i == 0) hex.moveTo(hx, hy);
					else hex.lineTo(hx, hy);
				}
				hex.closePath();
				g.draw(hex);
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3 + Math.PI / 6;
					float sx = m + (float) Math.cos(a) * m * 0.26f, sy = m + (float) Math.sin(a) * m * 0.26f;
					float ex = m + (float) Math.cos(a) * m * 0.7f, ey = m + (float) Math.sin(a) * m * 0.7f;
					g.draw(new Line2D.Float(sx, sy, ex, ey));
					Path2D tip = new Path2D.Float();
					float tx = m + (float) Math.cos(a) * m * 0.86f, ty = m + (float) Math.sin(a) * m * 0.86f;
					double p = a + Math.PI / 2;
					float w = m * 0.09f;
					tip.moveTo(ex, ey);
					tip.lineTo(m + (float) Math.cos(a) * m * 0.78f + (float) Math.cos(p) * w, m + (float) Math.sin(a) * m * 0.78f + (float) Math.sin(p) * w);
					tip.lineTo(tx, ty);
					tip.lineTo(m + (float) Math.cos(a) * m * 0.78f - (float) Math.cos(p) * w, m + (float) Math.sin(a) * m * 0.78f - (float) Math.sin(p) * w);
					tip.closePath();
					g.fill(tip);
				}
			}
			case SNOW_SOFT -> {
				g.setPaint(new RadialGradientPaint(m, m, m * 0.55f, new float[] {0f, 0.45f, 1f}, new Color[] {white(255), white(200), clear}));
				g.fillRect(0, 0, c, c);
			}
			case PETAL -> {
				// a cherry blossom petal: rounded with the little notch at the outer end
				Path2D petal = new Path2D.Float();
				petal.moveTo(m, c * 0.94f);
				petal.curveTo(c * 0.1f, c * 0.72f, c * 0.06f, c * 0.24f, c * 0.37f, c * 0.1f);
				petal.quadTo(c * 0.46f, c * 0.07f, m, c * 0.21f);
				petal.quadTo(c * 0.54f, c * 0.07f, c * 0.63f, c * 0.1f);
				petal.curveTo(c * 0.94f, c * 0.24f, c * 0.9f, c * 0.72f, m, c * 0.94f);
				g.setPaint(new GradientPaint(0, c * 0.94f, gray(205, 255), 0, c * 0.15f, white(255)));
				g.fill(petal);
				g.setClip(petal);
				g.setPaint(new RadialGradientPaint(m, c * 0.92f, c * 0.32f, new float[] {0f, 1f}, new Color[] {gray(170, 150), gray(170, 0)}));
				g.fillRect(0, 0, c, c);
				g.setColor(gray(225, 110));
				g.setStroke(new BasicStroke(c * 0.018f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Line2D.Float(m, c * 0.88f, m, c * 0.4f));
			}
			case LEAF -> {
				// a pointed leaf with a stem and veins
				Path2D leaf = new Path2D.Float();
				leaf.moveTo(m, c * 0.06f);
				leaf.curveTo(c * 0.86f, c * 0.22f, c * 0.86f, c * 0.62f, m, c * 0.84f);
				leaf.curveTo(c * 0.14f, c * 0.62f, c * 0.14f, c * 0.22f, m, c * 0.06f);
				g.setPaint(new GradientPaint(c * 0.25f, c * 0.2f, white(255), c * 0.75f, c * 0.8f, gray(190, 255)));
				g.fill(leaf);
				g.setColor(gray(150, 200));
				g.setStroke(new BasicStroke(c * 0.025f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Line2D.Float(m, c * 0.96f, m, c * 0.16f));
				g.setStroke(new BasicStroke(c * 0.016f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				for (int i = 0; i < 3; i++) {
					float yy = c * (0.66f - i * 0.15f);
					g.draw(new Line2D.Float(m, yy, m - c * 0.2f, yy - c * 0.1f));
					g.draw(new Line2D.Float(m, yy, m + c * 0.2f, yy - c * 0.1f));
				}
			}
			case BUTTERFLY -> {
				// two pairs of wings, lighter inside, with a few spots; the body is dark
				Color inner = white(255), outer = gray(170, 255);
				for (int side = -1; side <= 1; side += 2) {
					Path2D upper = new Path2D.Float();
					upper.moveTo(m, c * 0.46f);
					upper.curveTo(m + side * c * 0.12f, c * 0.1f, m + side * c * 0.5f, c * 0.02f, m + side * c * 0.47f, c * 0.3f);
					upper.curveTo(m + side * c * 0.45f, c * 0.46f, m + side * c * 0.2f, c * 0.5f, m, c * 0.52f);
					g.setPaint(new RadialGradientPaint(m + side * c * 0.22f, c * 0.32f, c * 0.3f, new float[] {0f, 1f}, new Color[] {inner, outer}));
					g.fill(upper);
					Path2D lower = new Path2D.Float();
					lower.moveTo(m, c * 0.52f);
					lower.curveTo(m + side * c * 0.3f, c * 0.5f, m + side * c * 0.4f, c * 0.72f, m + side * c * 0.26f, c * 0.82f);
					lower.curveTo(m + side * c * 0.14f, c * 0.9f, m + side * c * 0.05f, c * 0.7f, m, c * 0.6f);
					g.setPaint(new RadialGradientPaint(m + side * c * 0.18f, c * 0.64f, c * 0.24f, new float[] {0f, 1f}, new Color[] {inner, outer}));
					g.fill(lower);
					g.setColor(white(230));
					g.fill(new Ellipse2D.Float(m + side * c * 0.33f - c * 0.04f, c * 0.2f, c * 0.08f, c * 0.08f));
					g.fill(new Ellipse2D.Float(m + side * c * 0.22f - c * 0.03f, c * 0.68f, c * 0.06f, c * 0.06f));
				}
				g.setColor(gray(70, 255));
				g.fill(new Ellipse2D.Float(m - c * 0.035f, c * 0.3f, c * 0.07f, c * 0.46f));
				g.setStroke(new BasicStroke(c * 0.015f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Line2D.Float(m - c * 0.01f, c * 0.31f, m - c * 0.09f, c * 0.18f));
				g.draw(new Line2D.Float(m + c * 0.01f, c * 0.31f, m + c * 0.09f, c * 0.18f));
			}
			case BUBBLE -> {
				// see-through middle, a bright rim and a highlight
				g.setPaint(new RadialGradientPaint(m, m, m * 0.92f, new float[] {0f, 0.7f, 0.9f, 1f}, new Color[] {white(18), white(45), white(210), clear}));
				g.fillRect(0, 0, c, c);
				g.setPaint(new RadialGradientPaint(c * 0.34f, c * 0.32f, c * 0.16f, new float[] {0f, 1f}, new Color[] {white(240), clear}));
				g.fill(new Ellipse2D.Float(c * 0.16f, c * 0.16f, c * 0.36f, c * 0.32f));
				g.setPaint(new RadialGradientPaint(c * 0.68f, c * 0.7f, c * 0.08f, new float[] {0f, 1f}, new Color[] {white(150), clear}));
				g.fill(new Ellipse2D.Float(c * 0.58f, c * 0.6f, c * 0.2f, c * 0.2f));
			}
			case DOT -> {
				g.setPaint(new RadialGradientPaint(m, m, m * 0.5f, new float[] {0f, 0.6f, 1f}, new Color[] {white(255), white(255), clear}));
				g.fillRect(0, 0, c, c);
			}
		}
	}

	/** A plump heart filling most of the cell. */
	private static Path2D heart(int c) {
		Path2D p = new Path2D.Float();
		float m = c / 2f;
		p.moveTo(m, c * 0.9f);
		p.curveTo(c * 0.34f, c * 0.76f, c * 0.06f, c * 0.56f, c * 0.07f, c * 0.34f);
		p.curveTo(c * 0.08f, c * 0.16f, c * 0.24f, c * 0.08f, c * 0.33f, c * 0.09f);
		p.curveTo(c * 0.43f, c * 0.1f, m, c * 0.18f, m, c * 0.27f);
		p.curveTo(m, c * 0.18f, c * 0.57f, c * 0.1f, c * 0.67f, c * 0.09f);
		p.curveTo(c * 0.76f, c * 0.08f, c * 0.92f, c * 0.16f, c * 0.93f, c * 0.34f);
		p.curveTo(c * 0.94f, c * 0.56f, c * 0.66f, c * 0.76f, m, c * 0.9f);
		p.closePath();
		return p;
	}

	/** The atlas U range of a sprite, for drawing effect icons in the menu. */
	public static float[] uv(Sprite sprite) {
		int n = Sprite.values().length;
		return new float[] {sprite.ordinal() / (float) n, (sprite.ordinal() + 1) / (float) n};
	}

	public void ensureAtlasForGui() {
		ensureAtlas();
	}
}
