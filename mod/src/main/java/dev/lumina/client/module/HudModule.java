package dev.lumina.client.module;

import com.google.gson.JsonObject;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A module that draws a draggable element on the HUD. Positions are stored relative to the closest
 * screen edge (anchor) so elements stay in place when the window is resized.
 */
public abstract class HudModule extends Module {
	public enum AnchorX { LEFT, CENTER, RIGHT }

	public enum AnchorY { TOP, CENTER, BOTTOM }

	private final AnchorX defaultAx;
	private final AnchorY defaultAy;
	private final float defaultOx;
	private final float defaultOy;
	public AnchorX ax;
	public AnchorY ay;
	public float ox;
	public float oy;
	protected final NumberSetting scale;

	protected HudModule(String id, String name, String description, String icon, boolean defaultEnabled,
		AnchorX ax, AnchorY ay, float ox, float oy) {
		super(id, name, description, Category.HUD, icon, defaultEnabled);
		this.defaultAx = this.ax = ax;
		this.defaultAy = this.ay = ay;
		this.defaultOx = this.ox = ox;
		this.defaultOy = this.oy = oy;
		this.scale = add(new NumberSetting("scale", "Scale", 1.0, 0.5, 2.5, 0.05).suffix("x"));
	}

	/** Adds the shared background / color / font settings to this module. */
	protected HudStyle style(boolean withAccent) {
		return new HudStyle(s -> add(s), withAccent);
	}

	/** Unscaled width in GUI units. */
	public abstract float width();

	/** Unscaled height in GUI units. */
	public abstract float height();

	/**
	 * Draws the element with its top-left corner at the origin, unscaled.
	 *
	 * @param preview true inside the HUD editor; elements without data should show sample content
	 */
	public abstract void render(GuiGraphics g, boolean preview);

	/** False hides the element in game (e.g. no potion effects active). The editor always shows it. */
	public boolean shouldRender() {
		return true;
	}

	public float scale() {
		return scale.getFloat();
	}

	public float scaledWidth() {
		return width() * scale();
	}

	public float scaledHeight() {
		return height() * scale();
	}

	public float x(float screenW) {
		float w = scaledWidth();
		float x = switch (ax) {
			case LEFT -> ox;
			case CENTER -> screenW / 2f + ox - w / 2f;
			case RIGHT -> screenW - ox - w;
		};
		return Math.max(0, Math.min(screenW - w, x));
	}

	public float y(float screenH) {
		float h = scaledHeight();
		float y = switch (ay) {
			case TOP -> oy;
			case CENTER -> screenH / 2f + oy - h / 2f;
			case BOTTOM -> screenH - oy - h;
		};
		return Math.max(0, Math.min(screenH - h, y));
	}

	/** Stores a new top-left position and picks the anchor closest to the element. */
	public void setPosition(float x, float y, float screenW, float screenH) {
		float w = scaledWidth(), h = scaledHeight();
		x = Math.max(0, Math.min(screenW - w, x));
		y = Math.max(0, Math.min(screenH - h, y));
		float cx = x + w / 2f, cy = y + h / 2f;
		if (cx < screenW / 3f) {
			ax = AnchorX.LEFT;
			ox = x;
		} else if (cx > screenW * 2f / 3f) {
			ax = AnchorX.RIGHT;
			ox = screenW - x - w;
		} else {
			ax = AnchorX.CENTER;
			ox = cx - screenW / 2f;
		}
		if (cy < screenH / 3f) {
			ay = AnchorY.TOP;
			oy = y;
		} else if (cy > screenH * 2f / 3f) {
			ay = AnchorY.BOTTOM;
			oy = screenH - y - h;
		} else {
			ay = AnchorY.CENTER;
			oy = cy - screenH / 2f;
		}
		dev.lumina.client.setting.SettingChanges.markDirty();
	}

	public void resetPosition() {
		ax = defaultAx;
		ay = defaultAy;
		ox = defaultOx;
		oy = defaultOy;
		dev.lumina.client.setting.SettingChanges.markDirty();
	}

	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		JsonObject pos = new JsonObject();
		pos.addProperty("ax", ax.name());
		pos.addProperty("ay", ay.name());
		pos.addProperty("ox", ox);
		pos.addProperty("oy", oy);
		o.add("position", pos);
		return o;
	}

	@Override
	public void load(JsonObject o) {
		super.load(o);
		if (o.has("position") && o.get("position").isJsonObject()) {
			JsonObject pos = o.getAsJsonObject("position");
			try {
				ax = AnchorX.valueOf(pos.get("ax").getAsString());
				ay = AnchorY.valueOf(pos.get("ay").getAsString());
				ox = pos.get("ox").getAsFloat();
				oy = pos.get("oy").getAsFloat();
			} catch (RuntimeException e) {
				resetPosition();
			}
		}
	}
}
