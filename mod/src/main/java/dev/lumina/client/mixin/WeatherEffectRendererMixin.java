package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.WeatherChangerModule;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
	@ModifyReturnValue(method = "getPrecipitationAt", at = @At("RETURN"))
	private Biome.Precipitation lumina$snow(Biome.Precipitation original) {
		WeatherChangerModule m = Lumina.modules().get(WeatherChangerModule.class);
		if (m != null && m.forceSnow()) return Biome.Precipitation.SNOW;
		if (m != null && m.enabled() && (m.weather.is("Rain") || m.weather.is("Thunder")) && original == Biome.Precipitation.SNOW) {
			return Biome.Precipitation.RAIN;
		}
		return original;
	}
}
