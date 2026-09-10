package dev.zymekoh.kohscore.hud;

import dev.zymekoh.kohscore.api.CoreSignals;
import org.jspecify.annotations.Nullable;

/**
 * The last input-latency figure a sibling mod published, and how long it stays believable.
 *
 * <p>Core cannot measure this. It knows when a frame started and when it ended, which is frame
 * time; the age of a device report is only knowable to whatever is reading the device. So the
 * overlay shows a number it was handed, and shows nothing at all rather than a stale one -- a
 * latency readout that keeps displaying after its source stopped publishing is worse than an empty
 * line, because it looks like a measurement.
 *
 * <p>The window is deliberately several times the publishing interval. A source that reports twice
 * a second may miss one while the player is in a menu and the camera is reading nothing; dropping
 * the line for that would make the overlay flicker for a reason the player cannot see.
 */
public final class InputLatencyReadout {

	private static final long FRESHNESS_NANOS = 3_000_000_000L;

	private static volatile CoreSignals.@Nullable InputLatency latest;
	private static volatile long receivedNanos;

	private InputLatencyReadout() {
	}

	public static void accept(CoreSignals.InputLatency signal) {
		if (signal == null || signal.sampleCount() <= 0) {
			// A source with nothing to report is saying its path is idle, not that it is at zero.
			latest = null;
			return;
		}
		latest = signal;
		receivedNanos = System.nanoTime();
	}

	/** The current figure, or null when nothing has published recently enough to trust. */
	public static CoreSignals.@Nullable InputLatency current() {
		CoreSignals.InputLatency signal = latest;
		if (signal == null || System.nanoTime() - receivedNanos > FRESHNESS_NANOS) {
			return null;
		}
		return signal;
	}
}
