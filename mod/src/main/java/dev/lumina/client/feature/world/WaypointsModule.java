package dev.lumina.client.feature.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumina.client.LuminaKeys;
import dev.lumina.client.gui.WaypointScreen;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.font.Fonts;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.KeySetting;
import dev.lumina.client.setting.NumberSetting;
import dev.lumina.client.setting.WaypointsSetting;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public final class WaypointsModule extends Module {
	private static final int[] PALETTE = {0xFFE5484D, 0xFFF76B15, 0xFFFFC53D, 0xFF46A758, 0xFF12A594, 0xFF3E63DD, 0xFF8E4EC6, 0xFFD6409F, 0xFFF0F0F2};

	private final KeyMapping menuKey = LuminaKeys.register("waypoint", GLFW.GLFW_KEY_B);
	public final WaypointStore store = new WaypointStore();
	private final BoolSetting labels;
	private final BoolSetting distance;
	private final BoolSetting beams;
	private final NumberSetting maxDistance;
	private final NumberSetting labelScale;
	public final BoolSetting onMinimap;
	private final Random random = new Random();
	/** Last vertical FOV used for the world, captured by the FOV hook. */
	public static float lastFov = 70;

	public WaypointsModule() {
		super("waypoints", "Waypoints", "Mark places and find your way back", Category.WORLD, Icons.MAP_PIN, true);
		add(new KeySetting("add_key", "Waypoint Menu Key", menuKey));
		add(new WaypointsSetting());
		add(new HeaderSetting("Display"));
		labels = add(new BoolSetting("labels", "Show Labels", true));
		distance = add(new BoolSetting("distance", "Show Distance", true).visibleWhen(labels::on));
		labelScale = add(new NumberSetting("label_scale", "Label Scale", 1, 0.5, 2, 0.05).visibleWhen(labels::on));
		beams = add(new BoolSetting("beams", "Show Beams", true));
		maxDistance = add(new NumberSetting("max_distance", "Max Distance", 5000, 50, 20000, 50).suffix(" m"));
		onMinimap = add(new BoolSetting("on_minimap", "Show On Minimap", true));
		WorldRenderEvents.END_MAIN.register(this::renderBeams);
	}

	@Override
	public void tick() {
		while (menuKey.consumeClick()) {
			if (enabled() && mc().player != null && mc().screen == null) mc().setScreen(new WaypointScreen(this, menuKey));
		}
	}

	/** The palette color after {@code color}, for cycling a waypoint's color. */
	public static int nextColor(int color) {
		int idx = 0;
		for (int i = 0; i < PALETTE.length; i++) if ((PALETTE[i] & 0xFFFFFF) == (color & 0xFFFFFF)) idx = i;
		return PALETTE[(idx + 1) % PALETTE.length];
	}

	public Waypoint addAtPlayer() {
		var p = mc().player;
		List<Waypoint> list = store.current();
		Waypoint w = new Waypoint("Waypoint " + (list.size() + 1), p.getBlockX(), p.getBlockY(), p.getBlockZ(), WaypointStore.dimension(),
			PALETTE[random.nextInt(PALETTE.length)]);
		list.add(w);
		store.save();
		mc().gui.setOverlayMessage(Component.literal("Waypoint \"" + w.name + "\" added"), false);
		return w;
	}

	public void remove(Waypoint w) {
		store.current().remove(w);
		store.save();
	}

	private void renderBeams(WorldRenderContext context) {
		if (!enabled() || !beams.on() || mc().player == null) return;
		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		VertexConsumer vc = context.consumers().getBuffer(RenderTypes.lines());
		var pose = matrices.last();
		float max = maxDistance.getFloat();
		int minY = mc().level.getMinY(), maxY = mc().level.getMaxY();
		for (Waypoint w : store.currentDimension()) {
			if (!w.visible) continue;
			double dx = w.x + 0.5 - cam.x, dz = w.z + 0.5 - cam.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > max) continue;
			int c = ARGB.color(170, w.color);
			Vector3f up = new Vector3f(0, 1, 0);
			vc.addVertex(pose, (float) dx, (float) (minY - cam.y), (float) dz).setColor(c).setNormal(pose, up).setLineWidth(4f);
			vc.addVertex(pose, (float) dx, (float) (maxY - cam.y), (float) dz).setColor(c).setNormal(pose, up).setLineWidth(4f);
		}
	}

	/** Labels at the waypoints' projected screen positions. */
	public void renderLabels(GuiGraphics g, float partialTick) {
		if (!enabled() || !labels.on() || mc().player == null || mc().options.hideGui) return;
		Camera camera = mc().gameRenderer.getMainCamera();
		if (!camera.isInitialized()) return;
		Vec3 cam = camera.position();
		Quaternionf inv = new Quaternionf(camera.rotation()).conjugate();
		float w = g.guiWidth(), h = g.guiHeight();
		double tanHalf = Math.tan(Math.toRadians(lastFov) / 2);
		double aspect = w / h;
		float scale = labelScale.getFloat();
		for (Waypoint wp : store.currentDimension()) {
			if (!wp.visible) continue;
			double dx = wp.x + 0.5 - cam.x, dy = wp.y + 1.2 - cam.y, dz = wp.z + 0.5 - cam.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > maxDistance.get()) continue;
			Vector3f v = inv.transform(new Vector3f((float) dx, (float) dy, (float) dz));
			if (v.z >= -0.05f) continue;
			double nx = (v.x / -v.z) / (tanHalf * aspect);
			double ny = (v.y / -v.z) / tanHalf;
			if (Math.abs(nx) > 1.2 || Math.abs(ny) > 1.2) continue;
			float sx = (float) ((nx + 1) / 2 * w);
			float sy = (float) ((1 - ny) / 2 * h);
			String text = wp.name + (distance.on() ? "  " + Math.round(dist) + "m" : "");
			float size = 8;
			float tw = Fonts.MEDIUM.width(text, size);
			float bw = tw + 16, bh = 13;
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(sx, sy);
			pose.scale(scale, scale);
			Shapes.round(g, -bw / 2f, -bh / 2f, bw, bh, 4, 0xB30B0B0D);
			Shapes.circle(g, -bw / 2f + 6.5f, 0, 2.5f, ARGB.opaque(wp.color));
			Fonts.MEDIUM.draw(g, text, -bw / 2f + 11, -Fonts.MEDIUM.ascent(size) + Fonts.MEDIUM.capHeight(size) / 2f, size, 0xFFF0F0F2);
			pose.popMatrix();
		}
	}

	public static int randomColor() {
		return PALETTE[(int) (Math.random() * PALETTE.length)];
	}
}
