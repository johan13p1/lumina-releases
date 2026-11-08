package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.WeatherChangerModule;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Since 26.1 the time of day comes from world clocks instead of the level data. */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
	private long lumina$time(long original) {
		long t = lumina$override(original);
		return t < 0 ? original : t;
	}

	@ModifyReturnValue(method = "partialTick", at = @At("RETURN"))
	private float lumina$partial(float original) {
		// a fixed time must not move between ticks
		return lumina$override(0) < 0 ? original : 0;
	}

	private static long lumina$override(long original) {
		if (Lumina.modules() == null) return -1;
		WeatherChangerModule m = Lumina.modules().get(WeatherChangerModule.class);
		return m == null ? -1 : m.dayTime(original);
	}
}
