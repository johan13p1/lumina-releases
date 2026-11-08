package dev.lumina.client.feature.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lumina.client.Lumina;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.w3c.dom.Node;

/**
 * A cape decoded into a GPU texture: all frames packed into one atlas. Frames use the vanilla cape
 * layout (64x32 or any 2:1 multiple of it). Elytras always keep their normal look.
 *
 * <p>Accepted files: a 2:1 PNG (static), an animated PNG (APNG), a PNG with several 2:1 frames stacked vertically (animated,
 * 100 ms per frame), an OptiFine style 46:22 PNG, a LabyMod style 22:17 PNG, or an animated GIF.
 */
public final class CapeTexture {
	private static final int MAX_ATLAS = 8192;
	private static final int MAX_FRAME_W = 512;
	private static int counter;

	public final int frameW, frameH, frames, cols, atlasW, atlasH;
	private final int[] frameEnd; // cumulative end time of each frame in ms
	public final Identifier atlas;
	private final int id;
	private final FilterMode filter;
	private final DynamicTexture atlasTexture;
	// one frame on its own, for Wavey Capes (made when first asked for)
	private @org.jspecify.annotations.Nullable Identifier frameId;
	private @org.jspecify.annotations.Nullable DynamicTexture frameTex;
	private int shownFrame = -1;

	/** Decoded pixels, made on a background thread; {@link #upload} turns them into textures. */
	public record Decoded(List<int[]> frames, int width, int height, int[] delays) {
	}

	private CapeTexture(Decoded d) {
		int fw = d.width, fh = d.height;
		List<int[]> pixels = d.frames;
		// keep the atlas within GPU limits: shrink big frames, then frames that don't fit
		while (fw > MAX_FRAME_W || atlasRows(fw, fh, pixels.size()) * fh > MAX_ATLAS) {
			pixels = halve(pixels, fw, fh);
			fw /= 2;
			fh /= 2;
		}
		this.frameW = fw;
		this.frameH = fh;
		this.frames = pixels.size();
		this.cols = Math.max(1, Math.min(frames, MAX_ATLAS / fw));
		this.atlasW = cols * fw;
		this.atlasH = atlasRows(fw, fh, frames) * fh;
		this.frameEnd = new int[frames];
		int total = 0;
		for (int i = 0; i < frames; i++) {
			total += Math.max(20, d.delays[Math.min(i, d.delays.length - 1)]);
			frameEnd[i] = total;
		}
		this.id = counter++;
		this.atlas = Lumina.id("cape/" + id + "/atlas");
		NativeImage image = new NativeImage(atlasW, atlasH, true);
		for (int f = 0; f < frames; f++) {
			int[] px = pixels.get(f);
			int ox = (f % cols) * fw, oy = (f / cols) * fh;
			for (int y = 0; y < fh; y++) {
				for (int x = 0; x < fw; x++) image.setPixel(ox + x, oy + y, px[y * fw + x]);
			}
		}
		// pixel art capes stay crisp, HD capes are filtered
		this.filter = fw > 128 ? FilterMode.LINEAR : FilterMode.NEAREST;
		this.atlasTexture = register(atlas, image, filter);
	}

	private static int atlasRows(int fw, int fh, int frames) {
		int cols = Math.max(1, Math.min(frames, MAX_ATLAS / Math.max(1, fw)));
		return (frames + cols - 1) / cols;
	}

	private static DynamicTexture register(Identifier id, NativeImage image, FilterMode filter) {
		DynamicTexture tex = new DynamicTexture(id::toString, image) {
			{
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(filter);
			}
		};
		Minecraft.getInstance().getTextureManager().register(id, tex);
		return tex;
	}

	/** Creates the textures; render thread only. */
	public static CapeTexture upload(Decoded decoded) {
		return new CapeTexture(decoded);
	}

	public void release() {
		Minecraft.getInstance().getTextureManager().release(atlas);
		CapeRenderer.forget(atlas);
		if (frameId != null) {
			Minecraft.getInstance().getTextureManager().release(frameId);
			CapeRenderer.forget(frameId);
			frameId = null;
			frameTex = null;
		}
	}

	/**
	 * A texture with only the frame to show now, in the vanilla cape layout, for mods that draw a cape
	 * from a whole cape image (Wavey Capes). A still cape is its atlas already. Render thread only.
	 */
	public Identifier frameTexture() {
		if (frames == 1) return atlas;
		if (frameTex == null) {
			frameId = Lumina.id("cape/" + id + "/frame");
			frameTex = register(frameId, new NativeImage(frameW, frameH, true), filter);
		}
		int frame = currentFrame();
		if (frame != shownFrame) {
			shownFrame = frame;
			atlasTexture.getPixels().copyRect(frameTex.getPixels(), frameX(frame), frameY(frame), 0, 0, frameW, frameH, false, false);
			frameTex.upload();
		}
		return frameId;
	}

	public boolean animated() {
		return frames > 1;
	}

	/** The frame to show right now. */
	public int currentFrame() {
		if (frames == 1) return 0;
		long t = Util.getMillis() % frameEnd[frames - 1];
		for (int i = 0; i < frames; i++) if (t < frameEnd[i]) return i;
		return 0;
	}

	public int frameX(int frame) {
		return (frame % cols) * frameW;
	}

	public int frameY(int frame) {
		return (frame / cols) * frameH;
	}

	/** Pixels per vanilla cape pixel. */
	public float scale() {
		return frameW / 64f;
	}

	// ------------------------------------------------------------------
	// decoding (any thread)
	// ------------------------------------------------------------------

	public static Decoded decode(InputStream in, String fileName) throws IOException {
		if (fileName.toLowerCase().endsWith(".gif")) return decodeGif(in);
		byte[] bytes = in.readAllBytes();
		if (Apng.isAnimated(bytes)) {
			Apng.Result apng = Apng.decode(bytes, MAX_FRAME_W);
			List<BufferedImage> fitted = new ArrayList<>();
			for (BufferedImage f : apng.frames()) fitted.add(f.getWidth() == f.getHeight() * 2 ? f : canvas(f, Math.max(f.getWidth(), f.getHeight() * 2)));
			return toDecoded(fitted, apng.delays());
		}
		BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
		if (img == null) throw new IOException("Not an image: " + fileName);
		int w = img.getWidth(), h = img.getHeight();
		List<BufferedImage> frames = new ArrayList<>();
		if (w * 22 == h * 46) {
			// OptiFine layout: the cape sits in the top left of a 64x32 sized canvas
			frames.add(canvas(img, w * 64 / 46));
		} else if (w * 17 == h * 22) {
			// LabyMod layout: only the part of the 64x32 canvas that holds the cape
			frames.add(canvas(img, w * 64 / 22));
		} else if (h > w / 2 && h % (w / 2) == 0) {
			int fh = w / 2;
			for (int y = 0; y + fh <= h; y += fh) frames.add(img.getSubimage(0, y, w, fh));
		} else {
			frames.add(canvas(img, w));
		}
		int[] delays = new int[frames.size()];
		java.util.Arrays.fill(delays, 100);
		return toDecoded(frames, delays);
	}

	/** Puts {@code img} into the top left of a {@code width} x {@code width/2} canvas. */
	private static BufferedImage canvas(BufferedImage img, int width) {
		BufferedImage c = new BufferedImage(width, Math.max(1, width / 2), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = c.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();
		return c;
	}

	private static Decoded decodeGif(InputStream in) throws IOException {
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		try (ImageInputStream stream = ImageIO.createImageInputStream(in)) {
			reader.setInput(stream, false);
			int count = reader.getNumImages(true);
			int width = reader.getWidth(0), height = reader.getHeight(0);
			IIOMetadata streamMeta = reader.getStreamMetadata();
			if (streamMeta != null) {
				Node screen = child(streamMeta.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
				if (screen != null) {
					width = intAttr(screen, "logicalScreenWidth", width);
					height = intAttr(screen, "logicalScreenHeight", height);
				}
			}
			BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			List<BufferedImage> frames = new ArrayList<>();
			int[] delays = new int[count];
			for (int i = 0; i < count; i++) {
				BufferedImage frame = reader.read(i);
				Node root = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
				Node desc = child(root, "ImageDescriptor"), gce = child(root, "GraphicControlExtension");
				int x = desc == null ? 0 : intAttr(desc, "imageLeftPosition", 0);
				int y = desc == null ? 0 : intAttr(desc, "imageTopPosition", 0);
				int delay = gce == null ? 10 : intAttr(gce, "delayTime", 10);
				String disposal = gce == null ? "none" : gce.getAttributes().getNamedItem("disposalMethod").getNodeValue();
				BufferedImage before = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
				Graphics2D g = canvas.createGraphics();
				g.drawImage(frame, x, y, null);
				g.dispose();
				frames.add(copy(canvas));
				delays[i] = delay <= 1 ? 100 : delay * 10;
				if (disposal.equals("restoreToBackgroundColor")) {
					Graphics2D clear = canvas.createGraphics();
					clear.setComposite(java.awt.AlphaComposite.Clear);
					clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
					clear.dispose();
				} else if (before != null) {
					canvas = before;
				}
			}
			// GIF capes may have any size; fit them into the 2:1 cape layout
			List<BufferedImage> fitted = new ArrayList<>();
			for (BufferedImage f : frames) fitted.add(width == height * 2 ? f : canvas(f, Math.max(width, height * 2)));
			return toDecoded(fitted, delays);
		} finally {
			reader.dispose();
		}
	}

	private static Decoded toDecoded(List<BufferedImage> frames, int[] delays) {
		int w = frames.get(0).getWidth(), h = frames.get(0).getHeight();
		// 512 px wide is 8x the vanilla detail; bigger frames would only cost memory
		while (w > MAX_FRAME_W) {
			w /= 2;
			h /= 2;
		}
		List<int[]> pixels = new ArrayList<>();
		for (BufferedImage f : frames) {
			BufferedImage argb = f.getWidth() == w && f.getHeight() == h ? f : scaled(f, w, h);
			pixels.add(argb.getRGB(0, 0, w, h, null, 0, w));
		}
		return new Decoded(pixels, w, h, delays);
	}

	private static List<int[]> halve(List<int[]> frames, int w, int h) {
		List<int[]> out = new ArrayList<>();
		for (int[] px : frames) {
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			img.setRGB(0, 0, w, h, px, 0, w);
			out.add(scaled(img, w / 2, h / 2).getRGB(0, 0, w / 2, h / 2, null, 0, w / 2));
		}
		return out;
	}

	private static BufferedImage scaled(BufferedImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = c.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return c;
	}

	private static Node child(Node node, String name) {
		for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeName().equals(name)) return n;
		}
		return null;
	}

	private static int intAttr(Node node, String name, int def) {
		Node a = node.getAttributes().getNamedItem(name);
		try {
			return a == null ? def : Integer.parseInt(a.getNodeValue());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
