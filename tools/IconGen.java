import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Builds every logo asset of launcher and mod from the logo package (LuminaClient-Logo-24.9):
 * app icons (PNG + ICO), the launcher's hero and titlebar marks and the mod's GUI textures.
 * Usage: java tools/IconGen.java <logoPackageDir> [projectDir]
 */
public class IconGen {
	static final Color BG = new Color(0x09090B);

	static BufferedImage read(File dir, String path) throws Exception {
		BufferedImage img = ImageIO.read(new File(dir, path));
		BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = argb.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();
		return argb;
	}

	/** High quality downscale: halve with bilinear filtering until close, then one bicubic step. */
	static BufferedImage scale(BufferedImage src, int w, int h) {
		BufferedImage cur = src;
		while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		return draw(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	}

	static BufferedImage draw(BufferedImage src, int w, int h, Object interpolation) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/** Crops to the visible pixels and centers them on a square canvas with {@code pad} relative margin. */
	static BufferedImage squareCrop(BufferedImage src, float pad) {
		int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				if ((src.getRGB(x, y) >>> 24) > 8) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		int w = maxX - minX + 1, h = maxY - minY + 1;
		int side = Math.round(Math.max(w, h) * (1 + pad * 2));
		BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, (side - w) / 2 - minX, (side - h) / 2 - minY, null);
		g.dispose();
		return out;
	}

	/** Rounded app icon: the glass icon for large sizes, the flat mark for small ones (more contrast). */
	static BufferedImage appIcon(int size, BufferedImage glass, BufferedImage flat) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		float r = size * 0.22f;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, size, size, r * 2, r * 2);
		g.setColor(BG);
		g.fill(shape);
		g.setComposite(AlphaComposite.SrcAtop);
		int s = Math.round(size * (size >= 48 ? 0.7f : 0.72f));
		g.drawImage(scale(size >= 48 ? glass : flat, s, s), (size - s) / 2, (size - s) / 2, null);
		g.setComposite(AlphaComposite.SrcOver);
		g.setColor(new Color(255, 255, 255, 20));
		g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, size - 1, size - 1, r * 2, r * 2));
		g.dispose();
		return out;
	}

	static byte[] png(BufferedImage img) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	static void write(BufferedImage img, File file) throws Exception {
		file.getParentFile().mkdirs();
		ImageIO.write(img, "png", file);
		System.out.println("  " + file.getPath() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
	}

	static void ico(List<BufferedImage> icons, File file) throws Exception {
		List<byte[]> images = new ArrayList<>();
		for (BufferedImage img : icons) images.add(png(img));
		// ICO container with embedded PNGs
		ByteBuffer header = ByteBuffer.allocate(6 + 16 * icons.size()).order(ByteOrder.LITTLE_ENDIAN);
		header.putShort((short) 0).putShort((short) 1).putShort((short) icons.size());
		int offset = 6 + 16 * icons.size();
		for (int i = 0; i < icons.size(); i++) {
			int size = icons.get(i).getWidth();
			header.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
			header.put((byte) 0).put((byte) 0);
			header.putShort((short) 1).putShort((short) 32);
			header.putInt(images.get(i).length).putInt(offset);
			offset += images.get(i).length;
		}
		try (FileOutputStream out = new FileOutputStream(file)) {
			out.write(header.array());
			for (byte[] b : images) out.write(b);
		}
		System.out.println("  " + file.getPath());
	}

	public static void main(String[] args) throws Exception {
		File logo = new File(args[0]);
		File root = new File(args.length > 1 ? args[1] : ".");
		BufferedImage glass = squareCrop(read(logo, "PNG-Transparent/01-glass-original.png"), 0.04f);
		BufferedImage flat = squareCrop(read(logo, "PNG-Vector/09-flat-white.png"), 0.02f);

		File launcher = new File(root, "launcher");
		write(scale(glass, 660, 660), new File(launcher, "assets/logo/lumina-glass.png"));
		File svg = new File(launcher, "assets/logo/lumina-mark.svg");
		Files.copy(new File(logo, "SVG/09-flat-white.svg").toPath(), svg.toPath(), StandardCopyOption.REPLACE_EXISTING);
		System.out.println("  " + svg.getPath());
		write(appIcon(256, glass, flat), new File(launcher, "assets/icon.png"));
		write(appIcon(512, glass, flat), new File(launcher, "build/icon.png"));
		write(appIcon(128, glass, flat), new File(launcher, "build/icon-128.png"));
		List<BufferedImage> icons = new ArrayList<>();
		for (int size : new int[] {16, 24, 32, 48, 64, 128, 256}) icons.add(appIcon(size, glass, flat));
		ico(icons, new File(launcher, "build/icon.ico"));

		File assets = new File(root, "mod/src/main/resources/assets/lumina");
		write(appIcon(128, glass, flat), new File(assets, "icon.png"));
		write(scale(flat, 128, 128), new File(assets, "textures/gui/logo.png"));
		write(scale(glass, 256, 256), new File(assets, "textures/gui/logo_glass.png"));
		// glyph for the logo in front of player names (tab list, name tags)
		write(scale(flat, 32, 32), new File(assets, "textures/font/logo.png"));
		// loading screen: glass mark + "Lumina" in Outfit, loaded straight from the jar (see LoadingArt)
		write(lockup(glass, new File(assets, "font/outfit-semibold.ttf"), 320), new File(assets, "textures/gui/loading.png"));
	}

	/** Glass mark with the word "Lumina" to its right; {@code h} is the mark's height in pixels. */
	static BufferedImage lockup(BufferedImage mark, File fontFile, int h) throws Exception {
		java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontFile).deriveFont(h * 0.7f);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		java.awt.font.GlyphVector gv = font.createGlyphVector(pg.getFontRenderContext(), "Lumina");
		java.awt.Rectangle bounds = gv.getPixelBounds(pg.getFontRenderContext(), 0, 0);
		pg.dispose();
		int gap = Math.round(h * 0.16f), pad = Math.round(h * 0.06f);
		int w = pad + h + gap + bounds.width + pad;
		BufferedImage out = new BufferedImage(w, h + pad * 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.drawImage(scale(mark, h, h), pad, pad, null);
		g.setColor(new Color(0xF2F2F4));
		// text vertically centered on the mark
		float baseline = pad + h / 2f - bounds.y - bounds.height / 2f;
		g.drawGlyphVector(gv, pad + h + gap - bounds.x, baseline);
		g.dispose();
		return out;
	}
}
