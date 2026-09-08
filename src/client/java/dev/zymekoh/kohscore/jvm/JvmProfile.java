package dev.zymekoh.kohscore.jvm;

import java.util.List;

/**
 * A concrete set of JVM arguments, with the reasoning that produced them.
 *
 * @param goal        which trade-off this profile was tuned for
 * @param heapGib     the heap size, used for both {@code -Xms} and {@code -Xmx}
 * @param collector   short display name of the garbage collector
 * @param arguments   the flags, in the order they should be pasted
 * @param rationale   one line per decision, shown under the flags
 * @param warnings    anything the player should know before using this profile
 */
public record JvmProfile(Goal goal, int heapGib, String collector, List<String> arguments, List<String> rationale,
		List<String> warnings) {

	/** What the profile optimises for. */
	public enum Goal {
		/**
		 * Shortest, most consistent pauses. What you want for PvP, where a 40 ms hitch during a
		 * combo matters more than the average frame rate.
		 */
		LATENCY("kohs_core.jvm.goal.latency"),
		/** A sensible middle. Good default for normal play. */
		BALANCED("kohs_core.jvm.goal.balanced"),
		/** Room for large modpacks, accepting longer pauses in exchange for headroom. */
		HEAVY("kohs_core.jvm.goal.heavy");

		private final String translationKey;

		Goal(String translationKey) {
			this.translationKey = translationKey;
		}

		public String translationKey() {
			return this.translationKey;
		}
	}

	/** The whole argument line, ready to paste into a launcher. */
	public String commandLine() {
		return String.join(" ", this.arguments);
	}
}
