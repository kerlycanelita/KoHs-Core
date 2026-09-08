package dev.zymekoh.kohscore.orchestration;

import dev.zymekoh.kohscore.KoHsCore;
import dev.zymekoh.kohscore.api.CoreBus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The shared event channel. Synchronous, unordered, and forgiving: a listener that throws is logged
 * and skipped so one broken mod cannot silence the rest.
 */
public final class CoreBusImpl implements CoreBus {

	private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

	@Override
	public <T> void subscribe(Class<T> type, Consumer<T> listener) {
		this.listeners.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(listener);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void publish(Object event) {
		List<Consumer<?>> targets = this.listeners.get(event.getClass());
		if (targets == null) {
			return;
		}
		// CopyOnWriteArrayList lets listeners subscribe from a callback without allocating a fresh
		// snapshot on every performance signal.
		for (Consumer<?> consumer : targets) {
			try {
				((Consumer<Object>) consumer).accept(event);
			} catch (RuntimeException failure) {
				KoHsCore.LOGGER.warn("A {} listener threw; skipping it", event.getClass().getSimpleName(), failure);
			}
		}
	}
}
