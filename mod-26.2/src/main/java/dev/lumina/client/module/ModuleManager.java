package dev.lumina.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.lumina.client.setting.KeySetting;
import dev.lumina.client.setting.Setting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class ModuleManager {
	private final Map<String, Module> byId = new LinkedHashMap<>();
	private final Map<Class<?>, Module> byClass = new LinkedHashMap<>();
	private final List<HudModule> hud = new ArrayList<>();

	public <M extends Module> M register(M module) {
		if (byId.containsKey(module.id())) throw new IllegalStateException("Duplicate module " + module.id());
		byId.put(module.id(), module);
		byClass.put(module.getClass(), module);
		if (module instanceof HudModule h) hud.add(h);
		return module;
	}

	public List<Module> all() {
		return Collections.unmodifiableList(new ArrayList<>(byId.values()));
	}

	public List<HudModule> hud() {
		return Collections.unmodifiableList(hud);
	}

	public Module byId(String id) {
		return byId.get(id);
	}

	@SuppressWarnings("unchecked")
	public <M extends Module> M get(Class<M> type) {
		return (M) byClass.get(type);
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		for (Module m : byId.values()) {
			try {
				m.tick();
			} catch (RuntimeException e) {
				dev.lumina.client.Lumina.LOG.error("Module {} failed to tick", m.id(), e);
			}
			if (m.hasToggleKey()) {
				boolean down = mc.gui.screen() == null && m.toggleKey.isDown();
				if (down && !m.toggleKeyWasDown) m.toggle();
				m.toggleKeyWasDown = down;
			}
		}
	}

	/** Runs onEnable for modules that were enabled by the loaded config. */
	public void ready() {
		applySavedKeys();
		for (Module m : byId.values()) {
			try {
				m.onReady();
				if (m.enabled()) m.onEnable();
			} catch (RuntimeException e) {
				dev.lumina.client.Lumina.LOG.error("Module {} failed to start", m.id(), e);
			}
		}
	}

	/** Keys follow the shared Lumina config (see KeySetting#applySavedKey); options.txt is saved with them. */
	private void applySavedKeys() {
		boolean changed = false;
		for (Module m : byId.values()) {
			for (Setting<?> s : m.settings()) {
				if (s instanceof KeySetting k && k.applySavedKey()) changed = true;
			}
		}
		if (changed) {
			KeyMapping.resetMapping();
			Minecraft.getInstance().options.save();
		}
	}
}
