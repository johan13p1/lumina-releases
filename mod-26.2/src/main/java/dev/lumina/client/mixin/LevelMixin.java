package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.world.WeatherChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public abstract class LevelMixin {
	@ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
	private float lumina$rain(float original) {
		if (!((Object) this instanceof ClientLevel) || Lumina.modules() == null) return original;
		WeatherChangerModule m = Lumina.modules().get(WeatherChangerModule.class);
		if (m == null) return original;
		float v = m.rain();
		return Float.isNaN(v) ? original : v;
	}

	@ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
	private float lumina$thunder(float original) {
		if (!((Object) this instanceof ClientLevel) || Lumina.modules() == null) return original;
		WeatherChangerModule m = Lumina.modules().get(WeatherChangerModule.class);
		if (m == null) return original;
		float v = m.thunder();
		return Float.isNaN(v) ? original : v;
	}
}
