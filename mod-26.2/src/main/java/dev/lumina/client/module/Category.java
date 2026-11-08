package dev.lumina.client.module;

import dev.lumina.client.render.Icons;

public enum Category {
	HUD("HUD", Icons.LAYOUT),
	VISUAL("Visual", Icons.EYE),
	CAMERA("Camera", Icons.FOCUS),
	WORLD("World", Icons.SUN),
	UTILITY("Utility", Icons.WAND);

	private final String title;
	private final String icon;

	Category(String title, String icon) {
		this.title = title;
		this.icon = icon;
	}

	public String title() {
		return title;
	}

	public String icon() {
		return icon;
	}
}
