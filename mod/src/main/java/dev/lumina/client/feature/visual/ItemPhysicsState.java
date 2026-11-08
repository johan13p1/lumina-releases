package dev.lumina.client.feature.visual;

/** Extra data stored on item render states for {@link ItemPhysicsModule}. */
public interface ItemPhysicsState {
	void lumina$set(boolean onGround, boolean inFluid, float speed, int entityId);

	boolean lumina$onGround();

	boolean lumina$inFluid();

	float lumina$speed();

	int lumina$entityId();
}
