package dev.lumina.client.mixin;

import com.mojang.authlib.GameProfile;
import dev.lumina.client.feature.skins.SkinChanger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A skin you just changed in the Skin Changer shows on your own player right away. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
	@Shadow
	public abstract GameProfile getProfile();

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void lumina$changedSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (getProfile().id().equals(Minecraft.getInstance().getUser().getProfileId())) {
			cir.setReturnValue(SkinChanger.get().patch(cir.getReturnValue()));
		}
	}
}
