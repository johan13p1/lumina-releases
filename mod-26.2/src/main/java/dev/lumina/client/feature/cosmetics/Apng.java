package dev.lumina.client.feature.cosmetics;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * Minimal animated PNG (APNG) decoder. Java's ImageIO only sees the first frame, so every frame is
 * rebuilt as a plain PNG, decoded with ImageIO and composited by its dispose/blend rules.
 */
final class Apng {
	private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

	record Result(List<BufferedImage> frames, int[] delays) {
	}

	private record Chunk(String type, byte[] data) {
	}

	private record Frame(int w, int h, int x, int y, int delayMs, int dispose, int blend, ByteArrayOutputStream data) {
	}

	private Apng() {
	}

	static boolean isAnimated(byte[] png) {
		if (png.length < 8) return false;
		for (int i = 0; i < 8; i++) if (png[i] != SIGNATURE[i]) return false;
		for (Chunk c : chunks(png)) {
			if (c.type.equals("acTL")) return true;
			if (c.type.equals("IDAT")) return false;
		}
		return false;
	}

	private static List<Chunk> chunks(byte[] png) {
		List<Chunk> out = new ArrayList<>();
		ByteBuffer b = ByteBuffer.wrap(png);
		b.position(8);
		while (b.remaining() >= 12) {
			int len = b.getInt();
			byte[] type = new byte[4];
			b.get(type);
			if (len < 0 || len > b.remaining() - 4) break;
			byte[] data = new byte[len];
			b.get(data);
			b.getInt(); // crc
			out.add(new Chunk(new String(type, StandardCharsets.ISO_8859_1), data));
		}
		return out;
	}

	/** Decodes all frames; frames wider than {@code maxWidth} are scaled down right away to save memory. */
	static Result decode(byte[] png, int maxWidth) throws IOException {
		List<Chunk> chunks = chunks(png);
		byte[] ihdr = null;
		List<Chunk> shared = new ArrayList<>();
		List<Frame> frames = new ArrayList<>();
		Frame current = null;
		boolean seenData = false;
		for (Chunk c : chunks) {
			switch (c.type) {
				case "IHDR" -> ihdr = c.data;
				case "fcTL" -> {
					ByteBuffer d = ByteBuffer.wrap(c.data);
					d.getInt(); // sequence number
					int w = d.getInt(), h = d.getInt(), x = d.getInt(), y = d.getInt();
					int num = d.getShort() & 0xFFFF, den = d.getShort() & 0xFFFF;
					int dispose = d.get(), blend = d.get();
					int delay = Math.round(1000f * num / (den == 0 ? 100 : den));
					current = new Frame(w, h, x, y, delay <= 10 ? 100 : delay, dispose, blend, new ByteArrayOutputStream());
					frames.add(current);
				}
				case "IDAT" -> {
					seenData = true;
					// IDAT belongs to the animation only when a fcTL came before it
					if (current != null) current.data.write(c.data);
				}
				case "fdAT" -> {
					if (current != null) current.data.write(c.data, 4, c.data.length - 4);
				}
				case "acTL", "IEND" -> {
				}
				default -> {
					if (!seenData) shared.add(c);
				}
			}
		}
		if (ihdr == null || frames.isEmpty()) throw new IOException("Broken APNG");
		int width = ByteBuffer.wrap(ihdr).getInt(0), height = ByteBuffer.wrap(ihdr).getInt(4);
		int outW = width, outH = height;
		while (outW > maxWidth) {
			outW /= 2;
			outH /= 2;
		}
		BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		List<BufferedImage> out = new ArrayList<>();
		int[] delays = new int[frames.size()];
		for (int i = 0; i < frames.size(); i++) {
			Frame f = frames.get(i);
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(framePng(ihdr, shared, f)));
			if (img == null) throw new IOException("Broken APNG frame " + i);
			BufferedImage previous = f.dispose == 2 ? copy(canvas) : null;
			Graphics2D g = canvas.createGraphics();
			if (f.blend == 0) {
				// APNG_BLEND_OP_SOURCE: replace the region
				g.setComposite(AlphaComposite.Src);
			}
			g.drawImage(img, f.x, f.y, null);
			g.dispose();
			out.add(scaled(canvas, outW, outH));
			delays[i] = f.delayMs;
			if (f.dispose == 1) {
				Graphics2D clear = canvas.createGraphics();
				clear.setComposite(AlphaComposite.Clear);
				clear.fillRect(f.x, f.y, f.w, f.h);
				clear.dispose();
			} else if (previous != null) {
				canvas = previous;
			}
		}
		return new Result(out, delays);
	}

	/** One frame as a standalone PNG: the IHDR with the frame's size, shared chunks, its data. */
	private static byte[] framePng(byte[] ihdr, List<Chunk> shared, Frame f) throws IOException {
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		png.write(SIGNATURE);
		byte[] header = ihdr.clone();
		ByteBuffer.wrap(header).putInt(0, f.w).putInt(4, f.h);
		writeChunk(png, "IHDR", header);
		for (Chunk c : shared) writeChunk(png, c.type, c.data);
		writeChunk(png, "IDAT", f.data.toByteArray());
		writeChunk(png, "IEND", new byte[0]);
		return png.toByteArray();
	}

	private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
		byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
		out.write(ByteBuffer.allocate(4).putInt(data.length).array());
		out.write(t);
		out.write(data);
		CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(data);
		out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = c.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return c;
	}

	private static BufferedImage scaled(BufferedImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}
}
