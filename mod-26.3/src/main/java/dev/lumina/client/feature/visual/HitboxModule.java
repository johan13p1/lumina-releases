package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import dev.lumina.client.setting.BoolSetting;
import dev.lumina.client.setting.ColorSetting;
import dev.lumina.client.setting.HeaderSetting;
import dev.lumina.client.setting.NumberSetting;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;

/** Shows entity hitboxes (like F3+B) with a custom color. */
public final class HitboxModule extends Module {
	public final ColorSetting color;
	public final BoolSetting eyeRing;
	public final ColorSetting eyeRingColor;
	public final BoolSetting lookArrow;
	public final ColorSetting lookArrowColor;
	public final NumberSetting lookArrowLength;

	public HitboxModule() {
		super("hitbox", "Hitbox", "Show entity hitboxes with custom colors", Category.VISUAL, Icons.SCAN, false);
		color = add(new ColorSetting("color", "Color", 0xFFFFFFFF));
		withToggleKey();
		add(new HeaderSetting("Eye Ring"));
		eyeRing = add(new BoolSetting("eye_ring", "Show Eye Ring", true));
		eyeRingColor = add(new ColorSetting("eye_ring_color", "Ring Color", 0xFFFF0000).visibleWhen(eyeRing::on));
		add(new HeaderSetting("Look Arrow"));
		lookArrow = add(new BoolSetting("look_arrow", "Show Look Arrow", true));
		lookArrowColor = add(new ColorSetting("look_arrow_color", "Arrow Color", 0xFF0000FF).visibleWhen(lookArrow::on));
		lookArrowLength = add(new NumberSetting("look_arrow_length", "Arrow Length", 2, 0.5, 8, 0.5).suffix(" blocks").visibleWhen(lookArrow::on));
	}

	@Override
	protected void onEnable() {
		if (mc().debugEntries != null) mc().debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON);
	}

	@Override
	protected void onDisable() {
		if (mc().debugEntries != null) mc().debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER);
	}

	@Override
	public void tick() {
		// keep in sync when toggled with F3+B
		if (mc().debugEntries == null) return;
		boolean on = mc().debugEntries.getStatus(DebugScreenEntries.ENTITY_HITBOXES) == DebugScreenEntryStatus.ALWAYS_ON;
		if (on != enabled()) setEnabled(on);
	}
}
