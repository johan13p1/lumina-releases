package dev.lumina.client.feature.world;

public final class Waypoint {
	public String name;
	public int x;
	public int y;
	public int z;
	public String dimension;
	public int color;
	public boolean visible = true;

	public Waypoint(String name, int x, int y, int z, String dimension, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.color = color;
	}
}
