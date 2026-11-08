package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.WeatherChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
	private long lumina$time(long original) {
		if (Lumina.modules() == null) return original;
		WeatherChangerModule m = Lumina.modules().get(WeatherChangerModule.class);
		if (m == null) return original;
		long t = m.dayTime(original);
		return t < 0 ? original : t;
	}
}
