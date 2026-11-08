package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.visual.HitboxModule;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
	/** The module, or null while it is off: then the renderer stays vanilla. */
	@Unique
	private static @Nullable HitboxModule lumina$active() {
		HitboxModule m = Lumina.modules().get(HitboxModule.class);
		return m != null && m.enabled() ? m : null;
	}

	@ModifyVariable(method = "showHitboxes", at = @At("STORE"), ordinal = 0)
	private int lumina$color(int original) {
		HitboxModule m = lumina$active();
		if (m == null) return original;
		return original == -1 ? m.color.color() : original;
	}

	/** The red ring at eye height: the third cuboid, after the hitbox and the vehicle marker. Vanilla ignores the returned value. */
	@WrapOperation(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/Gizmos;cuboid(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/gizmos/GizmoStyle;)Lnet/minecraft/gizmos/GizmoProperties;", ordinal = 2))
	private @Nullable GizmoProperties lumina$eyeRing(AABB box, GizmoStyle style, Operation<GizmoProperties> original) {
		HitboxModule m = lumina$active();
		if (m == null) return original.call(box, style);
		return m.eyeRing.on() ? original.call(box, GizmoStyle.stroke(m.eyeRingColor.color())) : null;
	}

	/** The blue arrow along the view direction, 2 blocks long in vanilla. Vanilla ignores the returned value. */
	@WrapOperation(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/Gizmos;arrow(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)Lnet/minecraft/gizmos/GizmoProperties;", ordinal = 0))
	private @Nullable GizmoProperties lumina$lookArrow(Vec3 start, Vec3 end, int color, Operation<GizmoProperties> original) {
		HitboxModule m = lumina$active();
		if (m == null) return original.call(start, end, color);
		if (!m.lookArrow.on()) return null;
		Vec3 scaled = start.add(end.subtract(start).scale(m.lookArrowLength.getFloat() / 2f));
		return original.call(start, scaled, m.lookArrowColor.color());
	}
}
