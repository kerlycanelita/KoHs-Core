package dev.zymekoh.kohscore.api;

/**
 * How hard the client is currently struggling, derived from frame time and heap headroom.
 *
 * <p>Core broadcasts this so sibling mods can scale their own effects down before the player feels
 * a stutter, instead of every mod independently guessing at the frame budget.
 */
public enum PressureLevel {
	/** Comfortable. Spend freely. */
	RELAXED(1.0F),
	/** Frame budget is getting tight. Trim the optional flourishes. */
	ELEVATED(0.6F),
	/** Actively dropping frames. Keep only what the player asked for. */
	HIGH(0.3F),
	/** Emergency. Suspend anything cosmetic. */
	CRITICAL(0.1F);

	private final float budgetScale;

	PressureLevel(float budgetScale) {
		this.budgetScale = budgetScale;
	}

	/**
	 * Multiplier a mod should apply to its own effect budget, from 1.0 down to 0.1.
	 */
	public float budgetScale() {
		return this.budgetScale;
	}

	public boolean atLeast(PressureLevel other) {
		return this.ordinal() >= other.ordinal();
	}
}
