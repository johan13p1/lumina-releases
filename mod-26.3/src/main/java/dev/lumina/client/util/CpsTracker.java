package dev.lumina.client.util;

import java.util.ArrayDeque;

/** Counts mouse clicks in the last second. */
public final class CpsTracker {
	private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
	private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

	private CpsTracker() {
	}

	public static synchronized void click(int button) {
		long now = System.currentTimeMillis();
		if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) LEFT.addLast(now);
		else if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) RIGHT.addLast(now);
	}

	private static int count(ArrayDeque<Long> q) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
		return q.size();
	}

	public static synchronized int left() {
		return count(LEFT);
	}

	public static synchronized int right() {
		return count(RIGHT);
	}
}
