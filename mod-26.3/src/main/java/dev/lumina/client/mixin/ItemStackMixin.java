package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.ShinyPotsModule;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
	private boolean lumina$shinyPots(boolean original) {
		if (original || Lumina.modules() == null) return original;
		ShinyPotsModule m = Lumina.modules().get(ShinyPotsModule.class);
		if (m == null || !m.enabled()) return false;
		ItemStack self = (ItemStack) (Object) this;
		PotionContents contents = self.get(DataComponents.POTION_CONTENTS);
		if (contents == null || !contents.hasEffects()) return false;
		return !self.is(Items.TIPPED_ARROW) || m.arrows.on();
	}
}
