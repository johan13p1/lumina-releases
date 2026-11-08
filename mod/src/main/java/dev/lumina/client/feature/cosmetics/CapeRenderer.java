package dev.lumina.client.feature.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import dev.lumina.client.render.LuminaPipelines;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a cape as a strip of rows, shaped by {@link CapeSim} (cloth physics) or as a stiff board when
 * Waving Cape is off, using the current animation frame of a {@link CapeTexture}. Same size and
 * anchor as the vanilla cape. The cape is lit by the world but not shaded per side, so it shows the
 * colors of its image.
 */
public final class CapeRenderer {
	private static final int SEGMENTS = CapeSim.SEGMENTS; // one per cape pixel row
	private static final int POINTS = SEGMENTS + 1;
	private static final float WIDTH = 10, DEPTH = 1;
	private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
	private static Object irisApi;
	private static Method irisShaderPackInUse;
	private static boolean irisLooked;
	private static final Map<Integer, CapeSim> SIMS = new HashMap<>();
	private static Object simLevel;
	private static long lastSweep;

	private CapeRenderer() {
	}

	static RenderType renderType(Identifier texture) {
		// shader packs bring their own lighting and only know the vanilla render types
		if (shaderPackInUse()) return RenderTypes.entityCutoutNoCull(texture);
		return TYPES.computeIfAbsent(texture, t -> RenderType.create("lumina_cape", RenderSetup.builder(LuminaPipelines.CAPE)
			.withTexture("Sampler0", t)
			.useLightmap()
			.useOverlay()
			.affectsCrumbling()
			.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
			.createRenderSetup()));
	}

	/** Called when a cape texture is freed. */
	static void forget(Identifier texture) {
		TYPES.remove(texture);
	}

	private static boolean shaderPackInUse() {
		if (!irisLooked) {
			irisLooked = true;
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				irisApi = api.getMethod("getInstance").invoke(null);
				irisShaderPackInUse = api.getMethod("isShaderPackInUse");
			} catch (ReflectiveOperationException | LinkageError e) {
				irisApi = null;
			}
		}
		try {
			return irisApi != null && (boolean) irisShaderPackInUse.invoke(irisApi);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel parent,
		CapeTexture tex, boolean physics, boolean chestArmor) {
		poseStack.pushPose();
		parent.body.translateAndRotate(poseStack);
		if (chestArmor) poseStack.translate(0.0F, -0.053125F, 0.06875F);
		poseStack.translate(0, 0, 2 / 16f);
		if (!physics) {
			// sideways lean, like the vanilla cape
			poseStack.mulPose(Axis.ZP.rotationDegrees(state.capeLean2 / 2f));
			poseStack.mulPose(Axis.YP.rotationDegrees(-state.capeLean2 / 2f));
		}
		poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);

		// the middle line of the cape's inner side, one point per pixel row (x = left, y = down, z = back)
		float[] cx = new float[POINTS], cy = new float[POINTS], cz = new float[POINTS];
		if (physics) {
			CapeSim sim = sim(state, parent);
			System.arraycopy(sim.bx, 0, cx, 0, POINTS);
			System.arraycopy(sim.by, 0, cy, 0, POINTS);
			System.arraycopy(sim.bz, 0, cz, 0, POINTS);
		} else {
			// a stiff board at the vanilla angle
			float a = (float) Math.toRadians(Math.max(-10, 6 + state.capeLean / 2f + state.capeFlap));
			for (int i = 0; i < POINTS; i++) {
				cy[i] = i * (float) Math.cos(a);
				cz[i] = i * (float) Math.sin(a);
			}
		}
		float[] mesh = mesh(cx, cy, cz);

		int frame = tex.currentFrame();
		float fx = tex.frameX(frame), fy = tex.frameY(frame), s = tex.scale();
		float aw = tex.atlasW, ah = tex.atlasH;
		// texture coordinates in vanilla cape pixels -> atlas UV
		float[] u = new float[23], v = new float[SEGMENTS + 2];
		for (int i = 0; i < u.length; i++) u[i] = (fx + i * s) / aw;
		for (int i = 0; i < v.length; i++) v[i] = (fy + i * s) / ah;

		collector.submitCustomGeometry(poseStack, renderType(tex.atlas), (pose, vc) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				int a = i * STRIDE, b = (i + 1) * STRIDE;
				float v0 = v[1 + i], v1 = v[2 + i];
				// outer face (what others see from behind): cape pixels 1..11
				vertex(vc, pose, light, mesh, a + OR, u[1], v0, a + NRM, 1);
				vertex(vc, pose, light, mesh, a + OL, u[11], v0, a + NRM, 1);
				vertex(vc, pose, light, mesh, b + OL, u[11], v1, b + NRM, 1);
				vertex(vc, pose, light, mesh, b + OR, u[1], v1, b + NRM, 1);
				// inner face (towards your back): cape pixels 12..22
				vertex(vc, pose, light, mesh, a + IL, u[12], v0, a + NRM, -1);
				vertex(vc, pose, light, mesh, a + IR, u[22], v0, a + NRM, -1);
				vertex(vc, pose, light, mesh, b + IR, u[22], v1, b + NRM, -1);
				vertex(vc, pose, light, mesh, b + IL, u[12], v1, b + NRM, -1);
				// edges
				vertex(vc, pose, light, mesh, a + OL, u[0], v0, a + WID, -1);
				vertex(vc, pose, light, mesh, a + IL, u[1], v0, a + WID, -1);
				vertex(vc, pose, light, mesh, b + IL, u[1], v1, b + WID, -1);
				vertex(vc, pose, light, mesh, b + OL, u[0], v1, b + WID, -1);
				vertex(vc, pose, light, mesh, a + IR, u[11], v0, a + WID, 1);
				vertex(vc, pose, light, mesh, a + OR, u[12], v0, a + WID, 1);
				vertex(vc, pose, light, mesh, b + OR, u[12], v1, b + WID, 1);
				vertex(vc, pose, light, mesh, b + IR, u[11], v1, b + WID, 1);
			}
			// top and bottom
			int e = SEGMENTS * STRIDE;
			vertex(vc, pose, light, mesh, IL, u[1], v[0], TAN, -1);
			vertex(vc, pose, light, mesh, IR, u[11], v[0], TAN, -1);
			vertex(vc, pose, light, mesh, OR, u[11], v[1], TAN, -1);
			vertex(vc, pose, light, mesh, OL, u[1], v[1], TAN, -1);
			vertex(vc, pose, light, mesh, e + OL, u[11], v[0], e + TAN, 1);
			vertex(vc, pose, light, mesh, e + OR, u[21], v[0], e + TAN, 1);
			vertex(vc, pose, light, mesh, e + IR, u[21], v[1], e + TAN, 1);
			vertex(vc, pose, light, mesh, e + IL, u[11], v[1], e + TAN, 1);
		});
		poseStack.popPose();
	}

	// one row of the mesh: corners inner left/right, outer left/right, then the outward normal, the
	// width direction and the direction down the cape
	private static final int IL = 0, IR = 3, OL = 6, OR = 9, NRM = 12, WID = 15, TAN = 18, STRIDE = 21;

	/** Corners and directions of every row, from the middle line. The cloth stays level across its width. */
	private static float[] mesh(float[] cx, float[] cy, float[] cz) {
		float[] m = new float[POINTS * STRIDE];
		for (int i = 0; i < POINTS; i++) {
			int a = Math.max(0, i - 1), b = Math.min(POINTS - 1, i + 1);
			float tx = cx[b] - cx[a], ty = cy[b] - cy[a], tz = cz[b] - cz[a];
			float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1e-4f) {
				tx = 0;
				ty = 1;
				tz = 0;
			} else {
				tx /= tl;
				ty /= tl;
				tz /= tl;
			}
			// width: the body's left, made square to the cape
			float wx = 1 - tx * tx, wy = -tx * ty, wz = -tx * tz;
			float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
			if (wl < 1e-4f) {
				wx = 1;
				wy = wz = 0;
			} else {
				wx /= wl;
				wy /= wl;
				wz /= wl;
			}
			float nx = wy * tz - wz * ty, ny = wz * tx - wx * tz, nz = wx * ty - wy * tx;
			int o = i * STRIDE;
			float hw = WIDTH / 2;
			put(m, o + IL, cx[i] - wx * hw, cy[i] - wy * hw, cz[i] - wz * hw);
			put(m, o + IR, cx[i] + wx * hw, cy[i] + wy * hw, cz[i] + wz * hw);
			put(m, o + OL, m[o + IL] + nx * DEPTH, m[o + IL + 1] + ny * DEPTH, m[o + IL + 2] + nz * DEPTH);
			put(m, o + OR, m[o + IR] + nx * DEPTH, m[o + IR + 1] + ny * DEPTH, m[o + IR + 2] + nz * DEPTH);
			put(m, o + NRM, nx, ny, nz);
			put(m, o + WID, wx, wy, wz);
			put(m, o + TAN, tx, ty, tz);
		}
		return m;
	}

	private static void put(float[] m, int o, float x, float y, float z) {
		m[o] = x;
		m[o + 1] = y;
		m[o + 2] = z;
	}

	/** One player's cape physics, fed from the player itself so menus showing the same player share it. */
	private static CapeSim sim(AvatarRenderState state, PlayerModel parent) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		if (mc.level != simLevel) {
			SIMS.clear();
			simLevel = mc.level;
		}
		if (now - lastSweep > 5_000_000_000L) {
			lastSweep = now;
			SIMS.values().removeIf(sim -> now - sim.lastUsed > 5_000_000_000L);
		}
		CapeSim sim = SIMS.computeIfAbsent(state.id, CapeSim::new);
		float pitch = parent.body.xRot + modelPitch(state);
		float leg = Math.max(parent.leftLeg.xRot, parent.rightLeg.xRot);
		boolean legs = !state.isCrouching && state.swimAmount < 0.5f && !state.isFallFlying;
		Entity entity = mc.level == null ? null : mc.level.getEntity(state.id);
		if (entity instanceof LivingEntity living) {
			float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			sim.update(now, Mth.lerp(pt, living.xo, living.getX()), Mth.lerp(pt, living.yo, living.getY()), Mth.lerp(pt, living.zo, living.getZ()),
				Mth.rotLerp(pt, living.yBodyRotO, living.yBodyRot), pitch, leg, legs, living.getScale());
		} else {
			sim.update(now, 0, 0, 0, 0, pitch, leg, legs, 1);
		}
		return sim;
	}

	/** How far swimming or gliding tips the whole model forward (radians, like a body x rotation). */
	private static float modelPitch(AvatarRenderState state) {
		float degrees = 0;
		if (state.isFallFlying) {
			if (!state.isAutoSpinAttack) degrees = state.fallFlyingScale() * (90 + state.xRot);
		} else if (state.swimAmount > 0) {
			degrees = Mth.lerp(state.swimAmount, 0, state.isInWater ? 90 + state.xRot : 90);
		}
		return (float) Math.toRadians(degrees);
	}

	/** A corner of the mesh; {@code dir} picks which way the stored direction faces. */
	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, float[] m, int p, float u, float v, int n, float dir) {
		vc.addVertex(pose, m[p], m[p + 1], m[p + 2]).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
			.setNormal(pose, m[n] * dir, m[n + 1] * dir, m[n + 2] * dir);
	}
}
