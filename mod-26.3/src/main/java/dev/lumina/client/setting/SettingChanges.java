package dev.lumina.client.setting;

/** Tracks whether anything changed since the last config save. */
public final class SettingChanges {
	private static volatile boolean dirty;
	private static long lastChange;

	private SettingChanges() {
	}

	public static void markDirty() {
		dirty = true;
		lastChange = System.currentTimeMillis();
	}

	/** True once changes have settled for a moment, so dragging a slider doesn't write the file every frame. */
	public static boolean shouldSave() {
		return dirty && System.currentTimeMillis() - lastChange > 1500;
	}

	public static void clear() {
		dirty = false;
	}
}
