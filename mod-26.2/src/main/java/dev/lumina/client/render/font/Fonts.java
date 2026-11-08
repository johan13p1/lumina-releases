package dev.lumina.client.render.font;

public final class Fonts {
	public static TtfFont REGULAR;
	public static TtfFont MEDIUM;
	public static TtfFont SEMIBOLD;
	public static TtfFont BOLD;
	public static TtfFont ICONS;

	private Fonts() {
	}

	public static void init() {
		if (REGULAR != null) return;
		REGULAR = TtfFont.load("regular", "outfit-regular.ttf");
		MEDIUM = TtfFont.load("medium", "outfit-medium.ttf");
		SEMIBOLD = TtfFont.load("semibold", "outfit-semibold.ttf");
		BOLD = TtfFont.load("bold", "outfit-bold.ttf");
		ICONS = TtfFont.load("icons", "lucide.ttf");
	}

	public static void trim() {
		if (REGULAR == null) return;
		REGULAR.trim();
		MEDIUM.trim();
		SEMIBOLD.trim();
		BOLD.trim();
		ICONS.trim();
	}

	public static void clearAll() {
		if (REGULAR == null) return;
		REGULAR.clearCaches();
		MEDIUM.clearCaches();
		SEMIBOLD.clearCaches();
		BOLD.clearCaches();
		ICONS.clearCaches();
	}
}
