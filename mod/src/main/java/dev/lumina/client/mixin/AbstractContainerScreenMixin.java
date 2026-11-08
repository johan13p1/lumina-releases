package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.ClientSettingsModule;
import dev.lumina.client.gui.UI;
import dev.lumina.client.render.Logo;
import dev.lumina.client.render.Theme;
import dev.lumina.client.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	/** How far the creative inventory's rows of tabs reach below and above its window. */
	private static final int CREATIVE_TABS_BELOW = 28, CREATIVE_TABS_ABOVE = 28;
	private static final float TEXT_SIZE = 9, LOGO = 11, GAP = 4, MARGIN = 6;

	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	protected int imageWidth;
	@Shadow
	protected int imageHeight;

	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	/**
	 * A faint Lumina mark right under the inventory, flush with its right edge. It is measured in the
	 * inventory's own units, so it keeps its size next to the inventory at every GUI scale, and it stays
	 * clear of the screen edges where recipe mods like JEI, REI and EMI put their lists and search bars.
	 * When the gap under a tall inventory is narrow, it sits in the middle of the gap; without room there
	 * it goes above the inventory, and only when neither fits into the bottom right corner.
	 */
	@Inject(method = "renderContents", at = @At("TAIL"))
	private void lumina$watermark(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ClientSettingsModule s = Lumina.settings();
		if (s == null || !s.inventoryWatermark.on()) return;
		float textW = Fonts.SEMIBOLD.width("Lumina", TEXT_SIZE);
		boolean creative = (Object) this instanceof CreativeModeInventoryScreen;
		int below = topPos + imageHeight + (creative ? CREATIVE_TABS_BELOW : 0);
		int above = topPos - (creative ? CREATIVE_TABS_ABOVE : 0);
		float right = leftPos + imageWidth;
		float marginBelow = Math.min(MARGIN, (height - below - LOGO) / 2f);
		float marginAbove = Math.min(MARGIN, (above - LOGO) / 2f);
		float cy;
		if (marginBelow >= 1) {
			cy = below + marginBelow + LOGO / 2f;
		} else if (marginAbove >= 1) {
			cy = above - marginAbove - LOGO / 2f;
		} else {
			right = width - 8;
			cy = height - 9;
		}
		float x = right - textW;
		int color = Theme.alpha(Theme.TEXT, 0.42f);
		Logo.draw(g, Logo.FLAT, x - GAP - LOGO, cy - LOGO / 2f, LOGO, color);
		UI.text(g, Fonts.SEMIBOLD, "Lumina", x, cy, TEXT_SIZE, color);
	}
}
