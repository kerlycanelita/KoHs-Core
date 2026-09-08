package dev.zymekoh.kohscore.api;

/**
 * What Core hands a {@link CoreModule} when the client finishes loading.
 *
 * <p>Held for the whole session; a module may keep the reference.
 */
public interface CoreContext {

	/** The shared event channel. */
	CoreBus bus();

	/** The shared particle rate limiter. */
	ParticleAllowance particles();

	/**
	 * Takes ownership of a domain. Core stops writing to it and greys out its own controls.
	 *
	 * @return {@code true} if the claim was granted; {@code false} if another mod already holds it
	 *         exclusively, in which case the caller should stand down
	 */
	boolean claim(DomainClaim claim);

	/** Releases a domain previously claimed by the calling mod. */
	void release(Domain domain, String ownerModId);

	/** Adds a tab to Core's settings screen. */
	void addSettingsPage(CoreSettingsPage page);

	/** The current pressure level, for modules that poll instead of subscribing. */
	PressureLevel pressure();
}
