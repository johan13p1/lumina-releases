import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Renders the README header: Lumina logo next to the word "Lumina", vertically centered on the
 * capital height, in the launcher's title font (Outfit Medium, -0.03em tracking). Two versions:
 * dark theme (glass logo, off-white text) and light theme (flat mark, graphite text), both on a
 * transparent background at 2x so GitHub shows them sharp.
 *
 * java tools/ReadmeHeader.java launcher/assets/logo/lumina-glass.png launcher/assets/logo/lumina-mark.svg launcher/assets/fonts/outfit-medium.ttf .github/assets/header-dark.png .github/assets/header-light.png
 */
public class ReadmeHeader {
	static final int LOGO = 128;          // logo height in pixels (2x)
	static final double CAP = 0.66;       // capital height of the text, relative to the logo
	static final double GAP = 0.30;       // space between logo and text, relative to the logo
	static final int PAD = 6;

	public static void main(String[] a) throws Exception {
		BufferedImage glass = crop(ImageIO.read(new File(a[0])));
		Shape mark = svgPath(new String(Files.readAllBytes(new File(a[1]).toPath())));
		Font base = Font.createFont(Font.TRUETYPE_FONT, new File(a[2]));
		render(glass, null, base, new Color(0xF2, 0xF2, 0xF4), new File(a[3]));
		render(null, mark, base, new Color(0x1C, 0x1C, 0x1F), new File(a[4]));
	}

	static void render(BufferedImage logoImg, Shape logoShape, Font base, Color text, File out) throws Exception {
		FontRenderContext frc = new FontRenderContext(null, true, true);
		// font size from the wanted capital height, measured on the outline of "L"
		Font probe = base.deriveFont(100f);
		double capAt100 = probe.createGlyphVector(frc, "L").getOutline().getBounds2D().getHeight();
		float size = (float) (LOGO * CAP * 100 / capAt100);
		Font font = base.deriveFont(Map.of(TextAttribute.SIZE, size, TextAttribute.TRACKING, -0.03f));
		Shape word = font.createGlyphVector(frc, "Lumina").getOutline();
		Rectangle2D wb = word.getBounds2D();
		double cap = font.createGlyphVector(frc, "L").getOutline().getBounds2D().getHeight();

		double logoW;
		if (logoImg != null) logoW = LOGO * logoImg.getWidth() / (double) logoImg.getHeight();
		else logoW = LOGO * logoShape.getBounds2D().getWidth() / logoShape.getBounds2D().getHeight();
		int w = (int) Math.ceil(PAD + logoW + LOGO * GAP + wb.getWidth() + PAD);
		int h = LOGO + 2 * PAD;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		double centerY = PAD + LOGO / 2.0;
		if (logoImg != null) {
			g.drawImage(logoImg, PAD, PAD, (int) Math.round(logoW), LOGO, null);
		} else {
			Rectangle2D sb = logoShape.getBounds2D();
			double s = LOGO / sb.getHeight();
			AffineTransform t = new AffineTransform();
			t.translate(PAD, PAD);
			t.scale(s, s);
			t.translate(-sb.getX(), -sb.getY());
			g.setColor(text);
			g.fill(t.createTransformedShape(logoShape));
		}
		// the capital height sits centered on the logo: baseline = center + cap / 2
		double x = PAD + logoW + LOGO * GAP - wb.getX();
		double baseline = centerY + cap / 2;
		g.setColor(text);
		g.fill(AffineTransform.getTranslateInstance(x, baseline).createTransformedShape(word));
		g.dispose();
		ImageIO.write(img, "png", out);
		System.out.println(out.getName() + " " + w + "x" + h + " font " + Math.round(size) + "px cap " + Math.round(cap) + "px");
	}

	/** The glass logo without its transparent margin, so its height is the symbol's height. */
	static BufferedImage crop(BufferedImage src) {
		int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				if ((src.getRGB(x, y) >>> 24) > 24) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	/** The mark's single path: M, L, Q and Z with absolute coordinates. */
	static Shape svgPath(String svg) {
		String d = svg.replaceAll("(?s).*\\sd=\"([^\"]+)\".*", "$1");
		String[] t = d.trim().split("[\\s,]+");
		Path2D.Double p = new Path2D.Double();
		for (int i = 0; i < t.length; ) {
			switch (t[i++]) {
				case "M" -> p.moveTo(Double.parseDouble(t[i++]), Double.parseDouble(t[i++]));
				case "L" -> p.lineTo(Double.parseDouble(t[i++]), Double.parseDouble(t[i++]));
				case "Q" -> p.quadTo(Double.parseDouble(t[i++]), Double.parseDouble(t[i++]), Double.parseDouble(t[i++]), Double.parseDouble(t[i++]));
				case "Z" -> p.closePath();
				default -> throw new IllegalArgumentException("unexpected path token " + t[i - 1]);
			}
		}
		return p;
	}
}
