package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.WeatherChangerModule;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Since 26.1 the time of day comes from world clocks instead of the level data. In 26.2 the client
 * reads them only through getTotalTicks (26.3 adds a per-clock instance with a partial tick).
 */
@Mixin(ClientClockManager.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
	private long lumina$time(long original) {
		if (Lumina.modules() == null) return original;
		WeatherChangerModule m = Lumina.modules().get(WeatherChangerModule.class);
		if (m == null) return original;
		long t = m.dayTime(original);
		return t < 0 ? original : t;
	}
}
