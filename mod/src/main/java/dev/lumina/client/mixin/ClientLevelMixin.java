package dev.lumina.client.mixin;

import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.ParticleChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void lumina$hideBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (hide()) ci.cancel();
	}

	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
	private void lumina$hideCrack(BlockPos pos, Direction side, CallbackInfo ci) {
		if (hide()) ci.cancel();
	}

	private static boolean hide() {
		ParticleChangerModule m = Lumina.modules().get(ParticleChangerModule.class);
		return m != null && m.enabled() && m.hideBlockBreak.on();
	}
}
