package dev.lumina.client.setting;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class Setting<T> {
	private final String id;
	private final String name;
	private String description = "";
	private final T defaultValue;
	protected T value;
	private BooleanSupplier visibility = () -> true;
	private final List<Consumer<T>> listeners = new ArrayList<>();

	protected Setting(String id, String name, T defaultValue) {
		this.id = id;
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public T get() {
		return value;
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void set(T newValue) {
		T sanitized = sanitize(newValue);
		if (Objects.equals(value, sanitized)) return;
		value = sanitized;
		for (Consumer<T> l : listeners) l.accept(value);
		SettingChanges.markDirty();
	}

	protected T sanitize(T v) {
		return v;
	}

	public void reset() {
		set(defaultValue);
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier supplier) {
		this.visibility = supplier;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S describe(String text) {
		this.description = text;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S onChange(Consumer<T> listener) {
		this.listeners.add(listener);
		return (S) this;
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement element);
}
