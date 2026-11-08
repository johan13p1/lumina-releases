package dev.lumina.client.feature.visual;

import dev.lumina.client.module.Category;
import dev.lumina.client.module.Module;
import dev.lumina.client.render.Icons;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;

/** Shows chunk borders (like F3+G). */
public final class ChunkBordersModule extends Module {
	public ChunkBordersModule() {
		super("chunk_borders", "Chunk Borders", "Show the borders of chunks", Category.WORLD, Icons.GRID, false);
		withToggleKey();
	}

	@Override
	protected void onEnable() {
		if (mc().debugEntries != null) mc().debugEntries.setStatus(DebugScreenEntries.CHUNK_BORDERS, DebugScreenEntryStatus.ALWAYS_ON);
	}

	@Override
	protected void onDisable() {
		if (mc().debugEntries != null) mc().debugEntries.setStatus(DebugScreenEntries.CHUNK_BORDERS, DebugScreenEntryStatus.NEVER);
	}

	@Override
	public void tick() {
		// keep in sync when toggled with F3+G
		if (mc().debugEntries == null) return;
		boolean on = mc().debugEntries.getStatus(DebugScreenEntries.CHUNK_BORDERS) == DebugScreenEntryStatus.ALWAYS_ON;
		if (on != enabled()) setEnabled(on);
	}
}
