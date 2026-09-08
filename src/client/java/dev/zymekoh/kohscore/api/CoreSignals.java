package dev.zymekoh.kohscore.api;

/**
 * The payloads Core publishes on {@link CoreBus}. Sibling mods subscribe to whichever ones matter
 * to them and ignore the rest.
 */
public final class CoreSignals {

	private CoreSignals() {
	}

	/**
	 * Emitted roughly once a second with the current client health.
	 *
	 * @param framesPerSecond smoothed frame rate
	 * @param frameTimeMillis smoothed time for one frame
	 * @param heapUsedRatio   used heap over max heap, 0..1
	 * @param pressure        the level derived from the numbers above
	 */
	public record Performance(int framesPerSecond, float frameTimeMillis, float heapUsedRatio, PressureLevel pressure) {
	}

	/**
	 * Emitted when the particle allowance changes, so effect-heavy mods can resize their own pools
	 * instead of spawning particles that Core is about to reject.
	 *
	 * @param allowancePerSecond particles a single mod may spawn per second
	 * @param scale              multiplier to apply to burst sizes, 0..1
	 */
	public record ParticleBudget(int allowancePerSecond, float scale) {
	}

	/**
	 * Emitted when a domain changes hands, including when a claim is released.
	 *
	 * @param domain  the affected domain
	 * @param claim   the new owner, or {@code null} when Core has taken it back
	 */
	public record DomainOwnershipChanged(Domain domain, DomainClaim claim) {
	}

	/**
	 * Emitted when the player asks Core to reapply everything, e.g. after loading a profile.
	 */
	public record SettingsApplied() {
	}
}
