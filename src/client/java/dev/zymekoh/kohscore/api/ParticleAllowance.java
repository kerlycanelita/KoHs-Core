package dev.zymekoh.kohscore.api;

/**
 * Core's shared rate limiter for particle spawning.
 *
 * <p>Every KoHs mod that spawns particles asks here first. Because all of them draw from one budget,
 * three mods firing at once degrade smoothly instead of each one independently deciding it is still
 * within its own private limit.
 */
public interface ParticleAllowance {

	/**
	 * Requests permission to spawn up to {@code requested} particles.
	 *
	 * @param requested how many the caller would like
	 * @return how many it may actually spawn, between 0 and {@code requested}
	 *
	 * <p>Spawn the granted particles immediately on the calling thread. Core keeps that grant for a
	 * few milliseconds so its universal ParticleEngine gate does not charge the same particles a
	 * second time.
	 */
	int claim(int requested);

	/**
	 * Whether a particle type is currently allowed to spawn at all.
	 *
	 * @param particleTypeId registry id, e.g. {@code "minecraft:explosion"}
	 */
	boolean isTypeEnabled(String particleTypeId);

	/** Current allowance per second, for mods that want to size their own pools up front. */
	int allowancePerSecond();

	/** Multiplier to apply to burst sizes under the current pressure, 0..1. */
	float burstScale();
}
