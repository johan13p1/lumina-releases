package dev.lumina.client.hud;

import dev.lumina.client.Lumina;
import dev.lumina.client.gui.HudEditorScreen;
import dev.lumina.client.module.HudModule;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public final class HudRenderer {
	private static final Set<String> failed = new HashSet<>();

	private HudRenderer() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Lumina.id("hud"), HudRenderer::render);
		VanillaHudReplacements.register();
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker tick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || mc.gui.screen() instanceof HudEditorScreen) return;
		if (Lumina.hideHudInDebug() && mc.debugEntries.isOverlayVisible()) return;
		float w = g.guiWidth(), h = g.guiHeight();
		for (HudModule m : Lumina.modules().hud()) {
			if (!m.enabled() || !m.shouldRender()) continue;
			draw(g, m, w, h, false);
		}
	}

	public static void draw(GuiGraphicsExtractor g, HudModule m, float screenW, float screenH, boolean preview) {
		float x = m.x(screenW), y = m.y(screenH);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(m.scale(), m.scale());
		try {
			m.render(g, preview);
		} catch (RuntimeException e) {
			if (failed.add(m.id())) Lumina.LOG.error("HUD element {} failed to render", m.id(), e);
		}
		pose.popMatrix();
	}
}
