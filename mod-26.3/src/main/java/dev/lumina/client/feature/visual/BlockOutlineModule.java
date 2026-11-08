package dev.lumina.client.feature.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BlockOutlineModule extends Module {
	private final ColorSetting color;
	private final NumberSetting width;
	private final BoolSetting fill;
	private final ColorSetting fillColor;

	public BlockOutlineModule() {
		super("block_outline", "Block Outline", "Custom color, width and fill for the block highlight", Category.VISUAL, Icons.BOX, false);
		color = add(new ColorSetting("color", "Outline Color", 0xCCFFFFFF));
		width = add(new NumberSetting("width", "Line Width", 2.5, 1, 8, 0.5));
		fill = add(new BoolSetting("fill", "Fill Block", false));
		fillColor = add(new ColorSetting("fill_color", "Fill Color", 0x26FFFFFF).visibleWhen(fill::on));
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(this::render);
	}

	private boolean render(LevelRenderContext context, BlockOutlineRenderState state) {
		if (!enabled() || state == null) return true;
		Vec3 cam = context.levelState().cameraRenderState.pos;
		BlockPos pos = state.pos();
		PoseStack matrices = context.poseStack();
		double x = pos.getX() - cam.x, y = pos.getY() - cam.y, z = pos.getZ() - cam.z;
		if (fill.on()) {
			int fc = fillColor.color();
			context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.debugFilledBox(), (pose, vc) -> {
				for (AABB box : state.shape().toAabbs()) box(pose, vc, box.inflate(0.002).move(x, y, z), fc);
			});
		}
		float lineWidth = width.getFloat() * Math.max(1f, mc().getWindow().getWidth() / 1920f);
		matrices.pushPose();
		matrices.translate(x, y, z);
		context.submitNodeCollector().submitShapeOutline(matrices, state.shape(), RenderTypes.lines(), color.color(), lineWidth, state.isTranslucent());
		matrices.popPose();
		return false;
	}

	private static void box(PoseStack.Pose pose, VertexConsumer vc, AABB b, int color) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		// six faces, counter-clockwise when seen from outside
		quad(vc, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(vc, pose, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(vc, pose, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(vc, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(vc, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(vc, pose, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, int color, float... v) {
		for (int i = 0; i < 12; i += 3) vc.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(color);
	}
}
