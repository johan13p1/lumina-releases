package dev.lumina.client.feature.camera;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumina.client.LuminaKeys;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.KeySetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Two ways to look around. The spectator camera flies through the world, on servers too, while your
 * player stays where it is; only what you see moves, the server never hears of it. Look around turns
 * the camera around your player while you keep walking.
 */
public final class FreeLookModule extends Module {
	private static final double SPRINT_FACTOR = 3;
	/** How fast the camera reaches its speed; higher stops sooner. */
	private static final double FLY_RESPONSE = 12;

	private final KeyMapping flyKey = LuminaKeys.register("freelook_fly", InputConstants.KEY_V);
	private final KeyMapping key = LuminaKeys.register("freelook", InputConstants.KEY_LALT);
	private final NumberSetting flySpeed;
	private final BoolSetting flyWhereYouLook;
	private final ModeSetting mode;
	private final ModeSetting perspective;
	private final BoolSetting invert;
	private boolean looking;
	private boolean wasDown;
	private boolean flyWasDown;
	private int lastHurtTime;
	private float yaw;
	private float pitch;
	private @Nullable CameraType previous;

	private @Nullable LocalPlayer flyingPlayer;
	private Vec3 flyPos = Vec3.ZERO;
	private Vec3 flyVelocity = Vec3.ZERO;
	private boolean flyPosPending;
	private double speedScale = 1;
	private long lastFrame;

	public FreeLookModule() {
		super("freelook", "Free Look", "Fly like a spectator or look around while walking", Category.CAMERA, Icons.EYE, true);
		add(new HeaderSetting("Spectator Camera"));
		add(new KeySetting("fly_key", "Spectator Key", flyKey));
		flySpeed = add(new NumberSetting("fly_speed", "Fly Speed", 10, 1, 50, 1).suffix(" blocks/s"));
		flyWhereYouLook = add(new BoolSetting("fly_where_you_look", "Fly Where You Look", false));
		add(new HeaderSetting("Look Around"));
		add(new KeySetting("key", "Look Around Key", key));
		mode = add(new ModeSetting("mode", "Activation", "Hold", "Hold", "Toggle"));
		perspective = add(new ModeSetting("perspective", "Perspective", "Third Person", "Third Person", "Front", "First Person"));
		invert = add(new BoolSetting("invert", "Invert Vertical", false));
	}

	@Override
	public void tick() {
		tickSpectator();
		boolean down = enabled() && mc().gui.screen() == null && mc().player != null && key.isDown();
		boolean want = mode.is("Toggle") ? (down && !wasDown ? !looking : looking) : down;
		if (!enabled() || mc().player == null || flying()) want = false;
		if (want != looking) {
			if (want) startLooking();
			else stopLooking();
		}
		wasDown = down;
	}

	private void startLooking() {
		var p = mc().player;
		yaw = p.getYRot();
		pitch = p.getXRot();
		previous = mc().options.getCameraType();
		CameraType target = switch (perspective.get()) {
			case "Front" -> CameraType.THIRD_PERSON_FRONT;
			case "First Person" -> CameraType.FIRST_PERSON;
			default -> CameraType.THIRD_PERSON_BACK;
		};
		mc().options.setCameraType(target);
		looking = true;
	}

	private void stopLooking() {
		looking = false;
		restoreCameraType();
	}

	private void restoreCameraType() {
		if (previous != null) mc().options.setCameraType(previous);
		previous = null;
	}

	@Override
	protected void onDisable() {
		if (looking) stopLooking();
		if (flying()) stopFlying();
	}

	// ------------------------------------------------------------------
	// Spectator camera
	// ------------------------------------------------------------------

	private void tickSpectator() {
		LocalPlayer player = mc().player;
		boolean down = enabled() && mc().gui.screen() == null && player != null && flyKey.isDown();
		if (down && !flyWasDown) {
			if (flying()) stopFlying();
			else startFlying(player);
		}
		flyWasDown = down;
		// a new hit, not the red flash that may still be fading when the flight starts
		boolean hit = player != null && player.hurtTime > lastHurtTime;
		lastHurtTime = player == null ? 0 : player.hurtTime;
		if (!flying()) return;
		// a new player object means a respawn or another dimension: this camera has nothing to show there
		if (player != flyingPlayer || !player.isAlive()) {
			stopFlying();
		} else if (hit) {
			stopFlying();
			notice("Free Look ended: you took damage");
		}
	}

	private void startFlying(LocalPlayer player) {
		if (looking) stopLooking();
		yaw = player.getYRot();
		pitch = player.getXRot();
		flyPosPending = true;
		flyVelocity = Vec3.ZERO;
		lastFrame = System.nanoTime();
		previous = mc().options.getCameraType();
		// third person shows your player and hides the hand and crosshair; the position is replaced each frame
		mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
		flyingPlayer = player;
		notice("Free Look: WASD, Space and Shift fly, scroll sets the speed, " + flyKey.getTranslatedKeyMessage().getString() + " ends it");
	}

	private void stopFlying() {
		flyingPlayer = null;
		mc().smartCull = true;
		restoreCameraType();
	}

	private void notice(String text) {
		mc().gui.hud.setOverlayMessage(Component.literal(text), false);
	}

	public boolean flying() {
		return flyingPlayer != null;
	}

	/**
	 * Moves the camera for this frame and returns its new position; called once per frame while flying.
	 * {@code vanillaPos} is where the game put the camera: the first frame starts from there, behind your
	 * player, so you see yourself and not the inside of your head.
	 */
	public Vec3 nextFlyPosition(Vec3 vanillaPos) {
		if (flyPosPending) {
			flyPos = vanillaPos;
			flyPosPending = false;
		}
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastFrame) / 1e9);
		lastFrame = now;
		Options o = mc().options;
		boolean input = mc().gui.screen() == null;
		double forward = input ? axis(o.keyUp, o.keyDown) : 0;
		double left = input ? axis(o.keyLeft, o.keyRight) : 0;
		double up = input ? axis(o.keyJump, o.keyShift) : 0;
		double sin = Math.sin(Math.toRadians(yaw)), cos = Math.cos(Math.toRadians(yaw));
		Vec3 wish;
		if (flyWhereYouLook.on()) {
			double pitchCos = Math.cos(Math.toRadians(pitch)), pitchSin = Math.sin(Math.toRadians(pitch));
			wish = new Vec3(left * cos - forward * sin * pitchCos, up - forward * pitchSin, forward * cos * pitchCos + left * sin);
		} else {
			wish = new Vec3(left * cos - forward * sin, up, forward * cos + left * sin);
		}
		double speed = flySpeed.get() * speedScale * (input && o.keySprint.isDown() ? SPRINT_FACTOR : 1);
		wish = wish.lengthSqr() > 1 ? wish.normalize().scale(speed) : wish.scale(speed);
		flyVelocity = flyVelocity.lerp(wish, 1 - Math.exp(-FLY_RESPONSE * dt));
		flyPos = flyPos.add(flyVelocity.scale(dt));
		// occlusion culling starts at the camera, so inside a solid block it would hide the whole world
		if (mc().level != null) mc().smartCull = !mc().level.getBlockState(BlockPos.containing(flyPos)).isSolidRender();
		return flyPos;
	}

	private static double axis(KeyMapping positive, KeyMapping negative) {
		return (positive.isDown() ? 1 : 0) - (negative.isDown() ? 1 : 0);
	}

	/** Mouse wheel while flying sets the speed; returns true if the scroll was consumed. */
	public boolean onScroll(double amount) {
		if (!flying() || amount == 0) return false;
		speedScale *= Math.pow(1.15, Math.max(-3, Math.min(3, amount)));
		double base = flySpeed.get();
		speedScale = Math.max(1 / base, Math.min(200 / base, speedScale));
		notice(String.format(Locale.ROOT, "Fly speed: %.1f blocks/s", base * speedScale));
		return true;
	}

	/** While flying your player targets nothing, so no block is highlighted where it happens to look. */
	public void clearTarget() {
		LocalPlayer player = mc().player;
		if (!flying() || player == null) return;
		mc().hitResult = BlockHitResult.miss(player.position(), Direction.UP, player.blockPosition());
		mc().crosshairPickEntity = null;
	}

	// ------------------------------------------------------------------
	// Shared
	// ------------------------------------------------------------------

	/** True while the mouse turns this camera instead of your player. */
	public boolean active() {
		return looking || flying();
	}

	/** Mouse movement while active; mirrors Entity#turn scaling. */
	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch += (float) (invert.on() ? -dy : dy) * 0.15f;
		pitch = Math.max(-90, Math.min(90, pitch));
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}
}
