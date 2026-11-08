package dev.lumina.client.feature.cosmetics;

import dev.lumina.client.Lumina;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

/**
 * Lets Wavey Capes (tr7zw, a separate mod the launcher installs) move and draw Lumina capes. Wavey
 * Capes removes the vanilla cape layer, so without this Lumina capes would not show while it is
 * installed. Lumina contains none of its code: it signs up through Wavey Capes' public support list
 * by reflection, and works the same as before when the mod is missing.
 */
public final class WaveyCapesBridge {
	private static boolean active;
	private static @Nullable Method renderState, avatar;

	private WaveyCapesBridge() {
	}

	/** True when Wavey Capes draws the capes. */
	public static boolean active() {
		return active;
	}

	public static void install() {
		if (active || !FabricLoader.getInstance().isModLoaded("waveycapes")) return;
		try {
			ClassLoader loader = WaveyCapesBridge.class.getClassLoader();
			Class<?> supportType = Class.forName("dev.tr7zw.waveycapes.support.ModSupport", true, loader);
			Class<?> rendererType = Class.forName("dev.tr7zw.waveycapes.render.CapeRenderer", true, loader);
			Class<?> infoType = Class.forName("dev.tr7zw.waveycapes.render.CapeInfos", true, loader);
			Class<?> manager = Class.forName("dev.tr7zw.waveycapes.support.SupportManager", true, loader);
			Constructor<?> info = infoType.getConstructor(rendererType, RenderType.class, boolean.class);

			Object renderer = Proxy.newProxyInstance(loader, new Class<?>[] {rendererType}, handler((proxy, method, args) -> {
				if (!method.getName().equals("getCapeInfo")) return null;
				CapeTexture tex = texture(args[0]);
				return tex == null ? null : info.newInstance(proxy, CapeRenderer.renderType(tex.frameTexture()), false);
			}));
			Object support = Proxy.newProxyInstance(loader, new Class<?>[] {supportType}, handler((proxy, method, args) -> switch (method.getName()) {
				// the old overload takes a player; only the one with Wavey's player wrapper counts
				case "shouldBeUsed" -> !method.getParameterTypes()[0].getName().endsWith("AbstractClientPlayer") && texture(args[0]) != null;
				case "getRenderer" -> renderer;
				// ahead of other cape mods: a Lumina cape is what the player picked
				case "priority" -> 100;
				case "blockFeatureRenderer" -> false;
				default -> null;
			}));
			@SuppressWarnings("unchecked")
			Set<Object> supports = (Set<Object>) manager.getMethod("getSupportedMods").invoke(null);
			supports.add(support);
			active = true;
			Lumina.LOG.info("Wavey Capes draws Lumina capes");
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			Lumina.LOG.warn("Wavey Capes is installed, but Lumina capes could not be handed to it", e);
		}
	}

	/** The cape texture for the player behind Wavey Capes' player wrapper, or null. */
	private static @Nullable CapeTexture texture(Object wrapper) {
		if (wrapper == null || Lumina.modules() == null) return null;
		AvatarRenderState state;
		try {
			if (renderState == null) {
				renderState = wrapper.getClass().getMethod("getRenderState");
				avatar = wrapper.getClass().getMethod("getAvatar");
			}
			// Wavey Capes keeps its physics on the player entity: without one there is nothing to move
			if (avatar.invoke(wrapper) == null) return null;
			if (!(renderState.invoke(wrapper) instanceof AvatarRenderState s)) return null;
			state = s;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
		CosmeticsModule cosmetics = Lumina.modules().get(CosmeticsModule.class);
		if (cosmetics == null) return null;
		CapeLibrary.Entry entry = cosmetics.capeOf(state.id);
		return entry == null ? null : cosmetics.capes.texture(entry);
	}

	private interface Body {
		@Nullable Object call(Object proxy, Method method, Object[] args) throws Throwable;
	}

	/** Answers the interface's own methods with {@code body}, and the rest the way Java objects and default methods would. */
	private static InvocationHandler handler(Body body) {
		return (proxy, method, args) -> {
			switch (method.getName()) {
				case "hashCode":
					if (method.getParameterCount() == 0) return System.identityHashCode(proxy);
					break;
				case "equals":
					if (method.getParameterCount() == 1) return proxy == args[0];
					break;
				case "toString":
					if (method.getParameterCount() == 0) return "Lumina capes";
					break;
				default:
			}
			Object result = body.call(proxy, method, args == null ? new Object[0] : args);
			if (result == null && method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
			if (result == null && method.getReturnType() == boolean.class) return false;
			return result;
		};
	}
}
