package dev.lumina.client.mixin;

import dev.lumina.client.feature.visual.ItemPhysicsState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {
	@Unique
	private boolean lumina$onGround;
	@Unique
	private boolean lumina$inFluid;
	@Unique
	private float lumina$speed;
	@Unique
	private int lumina$entityId;

	@Override
	public void lumina$set(boolean onGround, boolean inFluid, float speed, int entityId) {
		this.lumina$onGround = onGround;
		this.lumina$inFluid = inFluid;
		this.lumina$speed = speed;
		this.lumina$entityId = entityId;
	}

	@Override
	public boolean lumina$onGround() {
		return lumina$onGround;
	}

	@Override
	public boolean lumina$inFluid() {
		return lumina$inFluid;
	}

	@Override
	public float lumina$speed() {
		return lumina$speed;
	}

	@Override
	public int lumina$entityId() {
		return lumina$entityId;
	}
}
