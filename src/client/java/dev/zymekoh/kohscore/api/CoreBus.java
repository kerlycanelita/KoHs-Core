package dev.zymekoh.kohscore.api;

import java.util.function.Consumer;

/**
 * A tiny typed publish/subscribe channel shared by every KoHs mod on the client.
 *
 * <p>Deliberately minimal: no ordering guarantees, no priorities, no unsubscribe-by-token. It exists
 * so mods can react to each other without importing each other, not to become a framework.
 * Everything is delivered synchronously on the thread that published.
 */
public interface CoreBus {

	/**
	 * Registers a listener for one payload type. Listeners are held for the life of the game.
	 *
	 * @param type     the payload class, typically one of the records in {@link CoreSignals}
	 * @param listener invoked for every matching publish
	 */
	<T> void subscribe(Class<T> type, Consumer<T> listener);

	/**
	 * Delivers an event to every listener registered for its exact class.
	 *
	 * <p>A listener that throws is logged and skipped; one misbehaving mod must not stop the others
	 * from being notified.
	 */
	void publish(Object event);
}
