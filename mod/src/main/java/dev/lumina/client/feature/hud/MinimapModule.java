package dev.lumina.client.feature.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.Waypoint;
import dev.lumina.client.feature.world.WaypointsModule;
import dev.lumina.client.module.HudModule;
import dev.lumina.client.render.Icons;
import dev.lumina.client.render.MaskedQuad;
import dev.lumina.client.render.Shapes;
import dev.lumina.client.render.Sprites;
import dev.lumina.client.render.font.Fonts;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.ModeSetting;
import dev.lumina.client.setting.NumberSetting;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix3x2fStack;

/**
 * Top-down map around the player built from block map colors. The texture is world aligned and
 * wraps around (texel = block modulo {@link #TEX}), so walking only needs to fill in the new strip.
 */
public final class MinimapModule extends HudModule {
	private static final int TEX = 256;
	private static final int EMPTY = 0xFF0E0E10;
	private static final Identifier TEXTURE = Lumina.id("minimap");

	private final NumberSetting size;
	private final ModeSetting shape;
	private final BoolSetting rotate;
	private final NumberSetting zoom;
	private final BoolSetting showCoords;
	private final BoolSetting cardinal;
	private final BoolSetting caveMode;
	private final ColorSetting borderColor;
	private final ColorSetting background;

	private NativeImage image;
	private DynamicTexture texture;
	/** World x/z each texel currently shows, to know what needs (re)drawing. */
	private final int[] texX = new int[TEX * TEX];
	private final int[] texZ = new int[TEX * TEX];
	private int refreshRow;
	private Object lastLevel;
	private boolean lastCave;

	public MinimapModule() {
		super("minimap", "Minimap", "A clean minimap with waypoints", Icons.MAP, false, AnchorX.RIGHT, AnchorY.TOP, 4, 4);
		size = add(new NumberSetting("size", "Size", 90, 50, 160, 2));
		shape = add(new ModeSetting("shape", "Shape", "Circle", "Circle", "Square"));
		rotate = add(new BoolSetting("rotate", "Rotate With Player", true));
		zoom = add(new NumberSetting("zoom", "Zoom", 1.5, 1, 4, 0.25).suffix("x"));
		caveMode = add(new BoolSetting("cave_mode", "Cave Mode In Nether", true));
		add(new HeaderSetting("Style"));
		showCoords = add(new BoolSetting("coords", "Show Coordinates", true));
		cardinal = add(new BoolSetting("cardinal", "Show N / E / S / W", true));
		borderColor = add(new ColorSetting("border", "Border Color", 0x40FFFFFF));
		background = add(new ColorSetting("background", "Background", 0xCC0B0B0D));
		Arrays.fill(texX, Integer.MIN_VALUE);
	}

	@Override
	public float width() {
		return size.getFloat();
	}

	@Override
	public float height() {
		return size.getFloat() + (showCoords.on() ? 12 : 0);
	}

	@Override
	public boolean shouldRender() {
		return mc().level != null && mc().player != null;
	}

	private void ensureTexture() {
		if (texture != null) return;
		image = new NativeImage(TEX, TEX, true);
		for (int y = 0; y < TEX; y++) for (int x = 0; x < TEX; x++) image.setPixel(x, y, EMPTY);
		texture = new DynamicTexture(() -> "lumina:minimap", image) {
			{
				this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
			}
		};
		mc().getTextureManager().register(TEXTURE, texture);
	}

	@Override
	public void tick() {
		if (!enabled() || mc().level == null || mc().player == null || texture == null) return;
		ClientLevel level = mc().level;
		boolean cave = caveMode.on() && level.dimensionType().hasCeiling();
		if (level != lastLevel || cave != lastCave) {
			Arrays.fill(texX, Integer.MIN_VALUE);
			lastLevel = level;
			lastCave = cave;
		}
		int px = mc().player.getBlockX(), pz = mc().player.getBlockZ(), py = mc().player.getBlockY();
		int radius = (int) Math.ceil(size.getFloat() / 2f / zoom.getFloat() * 1.45f) + 2;
		radius = Math.min(radius, TEX / 2 - 1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean changed = false;
		int budget = 6000;
		// 1) fill texels that show the wrong block (newly visible area)
		for (int dz = -radius; dz <= radius && budget > 0; dz++) {
			int z = pz + dz;
			for (int dx = -radius; dx <= radius; dx++) {
				int x = px + dx;
				int idx = Math.floorMod(z, TEX) * TEX + Math.floorMod(x, TEX);
				if (texX[idx] == x && texZ[idx] == z) continue;
				int c = sample(level, pos, x, z, cave, py);
				image.setPixel(Math.floorMod(x, TEX), Math.floorMod(z, TEX), c);
				if (c != EMPTY) {
					texX[idx] = x;
					texZ[idx] = z;
				}
				changed = true;
				if (--budget <= 0) break;
			}
		}
		// 2) refresh a couple of rows each tick so block changes show up
		for (int n = 0; n < 4; n++) {
			int dz = refreshRow - radius;
			refreshRow = (refreshRow + 1) % (radius * 2 + 1);
			int z = pz + dz;
			for (int dx = -radius; dx <= radius; dx++) {
				int x = px + dx;
				image.setPixel(Math.floorMod(x, TEX), Math.floorMod(z, TEX), sample(level, pos, x, z, cave, py));
			}
			changed = true;
		}
		if (changed) texture.upload();
	}

	private int sample(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, boolean cave, int playerY) {
		if (!level.hasChunk(x >> 4, z >> 4)) return EMPTY;
		int minY = level.getMinY();
		int y;
		if (cave) {
			y = playerY + 1;
			while (y > minY && !level.getBlockState(pos.set(x, y, z)).isAir()) y--;
			while (y > minY && level.getBlockState(pos.set(x, y, z)).isAir()) y--;
			if (playerY - y > 48) return EMPTY;
		} else {
			y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		}
		pos.set(x, y, z);
		BlockState state = level.getBlockState(pos);
		MapColor color = state.getMapColor(level, pos);
		if (color == MapColor.NONE) return EMPTY;
		MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
		if (color == MapColor.WATER) {
			int depth = 0;
			int yy = y;
			while (yy > minY && depth < 10 && !level.getBlockState(pos.set(x, --yy, z)).getFluidState().isEmpty()) depth++;
			brightness = depth > 6 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
		} else if (!cave) {
			int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
			if (north > y) brightness = MapColor.Brightness.LOW;
			else if (north < y) brightness = MapColor.Brightness.HIGH;
		}
		return color.calculateARGBColor(brightness) | 0xFF000000;
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
		ensureTexture();
		float s = size.getFloat();
		boolean circle = shape.is("Circle");
		float radius = circle ? s / 2f : 6;
		float half = s / 2f;
		Shapes.shadow(g, 0, 0, s, s, radius, 6, 0x40000000);
		Shapes.round(g, 0, 0, s, s, radius, background.color());

		var player = mc().player;
		float partial = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double ppx = player == null ? 0 : Mth.lerp(partial, player.xo, player.getX());
		double ppz = player == null ? 0 : Mth.lerp(partial, player.zo, player.getZ());
		float yaw = player == null ? 180 : player.getViewYRot(partial);
		boolean rot = rotate.on();
		float pxPerBlock = zoom.getFloat();

		// UVs: rotate the sampled area around the player instead of rotating the frame
		double angle = rot ? Math.toRadians(yaw + 180) : 0;
		double cos = Math.cos(angle), sin = Math.sin(angle);
		double hb = half / pxPerBlock;
		double[][] corners = {{-hb, -hb}, {-hb, hb}, {hb, hb}, {hb, -hb}};
		float[] uv = new float[8];
		for (int i = 0; i < 4; i++) {
			double cx = corners[i][0], cz = corners[i][1];
			double wx = ppx + cx * cos - cz * sin;
			double wz = ppz + cx * sin + cz * cos;
			uv[i * 2] = (float) (wx / TEX);
			uv[i * 2 + 1] = (float) (wz / TEX);
		}
		MaskedQuad.draw(g, TEXTURE, 0, 0, s, s, uv, circle ? 1 : radius / half, 1);

		// waypoints, clamped to the edge
		WaypointsModule wps = Lumina.modules().get(WaypointsModule.class);
		if (wps != null && wps.enabled() && wps.onMinimap.on() && player != null) {
			for (Waypoint w : wps.store.currentDimension()) {
				if (!w.visible) continue;
				double dx = (w.x + 0.5 - ppx) * pxPerBlock, dz = (w.z + 0.5 - ppz) * pxPerBlock;
				// inverse rotation into map space
				double mx = dx * cos + dz * sin, mz = -dx * sin + dz * cos;
				double lim = half - 4;
				if (circle) {
					double len = Math.sqrt(mx * mx + mz * mz);
					if (len > lim) {
						mx *= lim / len;
						mz *= lim / len;
					}
				} else {
					mx = Math.max(-lim, Math.min(lim, mx));
					mz = Math.max(-lim, Math.min(lim, mz));
				}
				Shapes.circle(g, half + (float) mx, half + (float) mz, 2.8f, 0xFF0B0B0D);
				Shapes.circle(g, half + (float) mx, half + (float) mz, 2f, ARGB.opaque(w.color));
			}
		}

		// player arrow
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(half, half);
		if (!rot) pose.rotate((float) Math.toRadians(yaw + 180));
		int a = 10;
		g.blit(RenderPipelines.GUI_TEXTURED, Sprites.arrow(), -a / 2, -a / 2, 0, 0, a, a, Sprites.SIZE, Sprites.SIZE, Sprites.SIZE, Sprites.SIZE, 0xFFFFFFFF);
		pose.popMatrix();

		if (cardinal.on()) {
			String[] labels = {"N", "E", "S", "W"};
			float size = 6.5f;
			for (int i = 0; i < 4; i++) {
				// north is -z; on screen it points up when not rotating
				double base = Math.toRadians(i * 90 - 90);
				double ang = base - angle;
				float d = half - 6.5f;
				float lx = half + (float) Math.cos(ang) * d, ly = half + (float) Math.sin(ang) * d;
				if (!circle) {
					lx = Math.max(6, Math.min(s - 6, lx));
					ly = Math.max(6, Math.min(s - 6, ly));
				}
				float tw = Fonts.SEMIBOLD.width(labels[i], size);
				Fonts.SEMIBOLD.draw(g, labels[i], lx - tw / 2f, ly - Fonts.SEMIBOLD.ascent(size) + Fonts.SEMIBOLD.capHeight(size) / 2f, size, i == 0 ? 0xFFFF6B6B : 0xCCFFFFFF);
			}
		}
		Shapes.roundOutline(g, 0, 0, s, s, radius, Shapes.hairline() * 1.5f, borderColor.color());

		if (showCoords.on() && player != null) {
			String text = player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
			float tw = Fonts.MEDIUM.width(text, 7.5f);
			Fonts.MEDIUM.draw(g, text, (s - tw) / 2f, s + 2.5f, 7.5f, 0xE6F0F0F2);
		}
	}
}
