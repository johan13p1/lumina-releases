import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Lumina's own 32x32 menu icons for Coordinates (compass), Zoom (spyglass) and Name Tags (name tag),
 * drawn from simple shapes so they belong to Lumina (MIT) like the rest of the icons.
 *
 *   java tools/ItemIcons.java <output folder>
 *   java tools/ItemIcons.java mod/src/main/resources/assets/lumina/textures/gui/module
 */
public class ItemIcons {
	static final int OUTLINE = 0xFF141417;

	public static void main(String[] a) throws Exception {
		File dir = new File(a.length > 0 ? a[0] : ".");
		write(compass(), new File(dir, "coordinates.png"));
		write(spyglass(), new File(dir, "zoom.png"));
		write(nameTag(), new File(dir, "name_tags.png"));
	}

	// ------------------------------------------------------------------ compass

	static int[][] compass() {
		int[][] p = new int[32][32];
		double cx = 16, cy = 14, rx = 14.2, ry = 10, depth = 4.5;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				double px = x + 0.5, py = y + 0.5;
				double top = ell(px, py, cx, cy, rx, ry);
				double face = ell(px, py, cx, cy - 0.3, rx - 3.0, ry - 2.4);
				// the side of the case: the top ellipse moved down by its depth
				boolean side = false;
				for (double d = 0; d <= depth; d += 0.25) side |= ell(px, py, cx, cy + d, rx, ry) <= 1;
				if (face <= 1) {
					double shade = (py - (cy - ry)) / (2 * ry);
					p[y][x] = shade < 0.45 ? 0xFF2E333B : 0xFF262A31;
				} else if (top <= 1) {
					boolean upper = py < cy - 0.5;
					boolean left = px < cx - 3;
					p[y][x] = upper ? (left ? 0xFFE9EDF0 : 0xFFD2D8DC) : 0xFFAEB6BC;
				} else if (side) {
					double sx = (px - cx) / rx;
					p[y][x] = sx < -0.55 ? 0xFF7B848B : sx > 0.45 ? 0xFF8E979E : 0xFFB8C0C6;
				}
			}
		}
		// ticks at N, E, S, W
		set(p, 16, 7, 0xFF59616B);
		set(p, 16, 20, 0xFF59616B);
		set(p, 7, 14, 0xFF59616B);
		set(p, 24, 14, 0xFF59616B);
		// needle: red north, light grey south, 2 px wide
		for (int y = 8; y <= 13; y++) {
			set(p, 15, y, y <= 9 ? 0xFFFF6A63 : 0xFFE5403B);
			set(p, 16, y, 0xFFB52A2B);
		}
		set(p, 16, 8, 0);
		set(p, 15, 8, 0xFFFF6A63);
		for (int y = 14; y <= 19; y++) {
			set(p, 15, y, 0xFFC9CED2);
			set(p, 16, y, 0xFF8C949A);
		}
		set(p, 16, 19, 0);
		set(p, 15, 19, 0xFFC9CED2);
		// pin
		set(p, 15, 14, 0xFFF4F6F7);
		set(p, 16, 14, 0xFF6C747B);
		return outline(p);
	}

	// ------------------------------------------------------------------ spyglass

	static int[][] spyglass() {
		int[][] p = new int[32][32];
		double ax = 4.5, ay = 27.5;                         // back end (eyepiece)
		double dx = Math.sqrt(0.5), dy = -Math.sqrt(0.5);   // towards the front, up and right
		double nx = Math.sqrt(0.5), ny = Math.sqrt(0.5);    // across, towards the shadow side
		// sections along the tube: from, to, half width, light, base, dark
		double[][] parts = {
			{0, 3, 2.7, 0xFF6B4A39, 0xFF4E352A, 0xFF35241D},
			{3, 12, 3.3, 0xFFB36A52, 0xFF8E4D3C, 0xFF68372C},
			{12, 14, 3.9, 0xFFF5D27A, 0xFFDDA640, 0xFFA8761F},
			{14, 19.5, 3.9, 0xFFD99552, 0xFFB8722F, 0xFF87501E},
			{19.5, 26, 5.0, 0xFFFFDD7A, 0xFFE3AA3E, 0xFFAA7620},
		};
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				double px = x + 0.5 - ax, py = y + 0.5 - ay;
				double t = px * dx + py * dy, v = px * nx + py * ny;
				for (double[] s : parts) {
					if (t >= s[0] && t < s[1] && Math.abs(v) <= s[2]) {
						p[y][x] = (int) (long) (v < -s[2] * 0.35 ? s[3] : v > s[2] * 0.45 ? s[5] : s[4]);
					}
				}
			}
		}
		// the lens, seen on the front end
		double lx = ax + dx * 26, ly = ay + dy * 26;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				double ex = x + 0.5 - lx, ey = y + 0.5 - ly;
				double r = Math.hypot(ex, ey);
				if (r <= 5.2) p[y][x] = r > 3.8 ? 0xFFE3AA3E : 0;
				if (r <= 3.8) {
					double diag = ex + ey; // negative = upper left
					p[y][x] = diag < -2.2 ? 0xFFC8F4F1 : diag < 1.2 ? 0xFF55C6C9 : 0xFF2F8E97;
				}
			}
		}
		return outline(p);
	}

	// ------------------------------------------------------------------ name tag

	static int[][] nameTag() {
		int[][] p = new int[32][32];
		double ox = 3.5, oy = 28.5;                         // the tag's loose end
		double dx = Math.sqrt(0.5), dy = -Math.sqrt(0.5);
		double nx = Math.sqrt(0.5), ny = Math.sqrt(0.5);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				double px = x + 0.5 - ox, py = y + 0.5 - oy;
				double u = px * dx + py * dy, v = px * nx + py * ny;
				double half = u < 16 ? 6.1 : 6.1 - (u - 16);  // pointed end towards the ring
				if (u >= 0 && u <= 21.5 && Math.abs(v) <= half) {
					p[y][x] = v < -3 ? 0xFFF4DEB0 : v > 3.2 ? 0xFFC9A066 : 0xFFE6C78F;
					// two lines of writing
					if ((Math.abs(v + 1.9) < 0.4 && ((u > 3.5 && u < 8) || (u > 9.5 && u < 13.5)))
						|| (Math.abs(v - 1.9) < 0.4 && u > 3.5 && u < 10.5)) {
						p[y][x] = 0xFF7A5A34;
					}
				}
			}
		}
		// the ring through the hole at the pointed end
		double rx = ox + dx * 23.5, ry = oy + dy * 23.5;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				double ex = x + 0.5 - rx, ey = y + 0.5 - ry;
				double r = Math.hypot(ex, ey);
				if (r <= 5.2 && r >= 3.1) {
					double diag = ex + ey;
					p[y][x] = diag < -2.5 ? 0xFFEEF3F5 : diag < 2 ? 0xFFBAC4CA : 0xFF7F8B92;
				}
			}
		}
		return outline(p);
	}

	// ------------------------------------------------------------------ helpers

	static double ell(double x, double y, double cx, double cy, double rx, double ry) {
		double ex = (x - cx) / rx, ey = (y - cy) / ry;
		return ex * ex + ey * ey;
	}

	static void set(int[][] p, int x, int y, int c) {
		if (x >= 0 && y >= 0 && x < 32 && y < 32) p[y][x] = c;
	}

	/** A one pixel dark outline around everything that is drawn, like the other menu icons. */
	static int[][] outline(int[][] p) {
		int[][] o = new int[32][32];
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				o[y][x] = p[y][x];
				if (p[y][x] != 0) continue;
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0], ny = y + d[1];
					if (nx >= 0 && ny >= 0 && nx < 32 && ny < 32 && p[ny][nx] != 0) o[y][x] = OUTLINE;
				}
			}
		}
		return o;
	}

	static void write(int[][] p, File f) throws Exception {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) img.setRGB(x, y, p[y][x]);
		ImageIO.write(img, "png", f);
		System.out.println("wrote " + f);
	}
}
