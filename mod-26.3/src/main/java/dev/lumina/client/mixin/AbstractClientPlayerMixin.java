package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.camera.FovChangerModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
	private float lumina$staticFov(float original) {
		FovChangerModule m = Lumina.modules().get(FovChangerModule.class);
		if (m == null || !m.enabled() || !m.staticFov.on()) return original;
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (self.isUsingItem()) {
			if (self.getUseItem().is(Items.BOW) && m.keepBow.on()) {
				float t = Math.min(self.getTicksUsingItem() / 20.0F, 1.0F);
				return 1.0F - Mth.square(t) * 0.15F;
			}
			if (original == 0.1F) return original; // spyglass
		}
		return 1.0F;
	}
}
