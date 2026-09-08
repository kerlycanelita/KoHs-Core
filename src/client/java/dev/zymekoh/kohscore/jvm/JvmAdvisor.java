package dev.zymekoh.kohscore.jvm;

import dev.zymekoh.kohscore.jvm.JvmProfile.Goal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link HardwareProbe} into concrete JVM arguments.
 *
 * <p>Heap size is not guessed from a table of round numbers. It is what is left after subtracting
 * what the operating system needs to stay responsive and what the JVM itself spends outside the
 * heap, then scaled by how much headroom the chosen goal wants to keep in reserve.
 *
 * <p>Two things Core deliberately does not do: it never edits the launcher, and it never claims to
 * have applied any of this. Heap size and collector are fixed when the JVM starts, so a mod cannot
 * change them from inside a running game. These are arguments to paste, and the screen says so.
 */
public final class JvmAdvisor {

	/**
	 * Headroom left for the OS, the browser and Discord, by machine size.
	 *
	 * <p>Being wrong here is asymmetric: an over-large heap that pushes Windows into the page file
	 * costs far more than an under-large one that collects slightly more often.
	 */
	private static double operatingSystemReserveGib(double totalGib) {
		if (totalGib <= 8.5) {
			return 2.5;
		}
		if (totalGib <= 16.5) {
			return 3.5;
		}
		if (totalGib <= 32.5) {
			return 4.5;
		}
		return 6.0;
	}

	/**
	 * What the java process uses beyond {@code -Xmx}: metaspace, code cache, GC bookkeeping, direct
	 * buffers, and the driver's mapped GPU memory. Consistently around this on a modded client.
	 */
	private static final double NATIVE_OVERHEAD_GIB = 1.5;

	private JvmAdvisor() {
	}

	/** Builds all three profiles for the current machine, in display order. */
	public static List<JvmProfile> allProfiles(HardwareProbe probe) {
		return List.of(profile(probe, Goal.LATENCY), profile(probe, Goal.BALANCED), profile(probe, Goal.HEAVY));
	}

	/** Builds one profile. */
	public static JvmProfile profile(HardwareProbe probe, Goal goal) {
		List<String> rationale = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		int heapGib = recommendHeapGib(probe, goal, rationale, warnings);
		boolean useZgc = shouldUseZgc(probe, goal, heapGib, rationale);

		List<String> arguments = new ArrayList<>();
		arguments.add("-Xms" + heapGib + "G");
		arguments.add("-Xmx" + heapGib + "G");
		rationale.add("Xms equals Xmx so the heap never has to grow mid-game, which is a "
				+ "stop-the-world pause exactly when you are least able to afford one.");

		if (useZgc) {
			appendZgcArguments(probe, goal, arguments, rationale, warnings);
		} else {
			appendG1Arguments(probe, goal, heapGib, arguments, rationale);
		}

		appendSharedArguments(probe, heapGib, arguments, rationale, warnings);
		appendGeneralWarnings(probe, heapGib, warnings);

		return new JvmProfile(goal, heapGib, useZgc ? "ZGC" : "G1", List.copyOf(arguments), List.copyOf(rationale),
				List.copyOf(warnings));
	}

	private static int recommendHeapGib(HardwareProbe probe, Goal goal, List<String> rationale,
			List<String> warnings) {
		double totalGib = probe.totalMemoryGib();

		if (totalGib <= 0.0) {
			// The probe has not landed or failed outright. Say so instead of inventing a number.
			warnings.add("Physical RAM could not be read, so this heap size is a generic default "
					+ "rather than a measurement of your machine.");
			return goal == Goal.HEAVY ? 6 : 4;
		}

		double osReserve = operatingSystemReserveGib(totalGib);
		double usable = totalGib - osReserve - NATIVE_OVERHEAD_GIB;

		double fraction = switch (goal) {
			case LATENCY -> 0.55;
			case BALANCED -> 0.65;
			case HEAVY -> 0.80;
		};
		int ceiling = switch (goal) {
			// Past a point a bigger heap makes pauses worse, not better, because there is simply
			// more to walk. Beyond about 8 GiB that outweighs the extra room for a client.
			case LATENCY -> 8;
			case BALANCED -> 10;
			case HEAVY -> 16;
		};

		int heap = (int) Math.floor(usable * fraction);
		heap = Math.max(2, Math.min(heap, Math.min(ceiling, (int) Math.floor(usable))));

		rationale.add(String.format(Locale.ROOT,
				"%.1f GiB installed, minus %.1f GiB for the OS and %.1f GiB the JVM spends outside the "
						+ "heap, leaves %.1f GiB to work with. This profile takes %d%% of that.",
				totalGib, osReserve, NATIVE_OVERHEAD_GIB, Math.max(0.0, usable), Math.round(fraction * 100)));

		if (heap == 2 && usable < 3.0) {
			warnings.add("This machine is short on RAM for a modded client. 2 GiB is the floor, not a "
					+ "recommendation; closing background applications will help more than any flag here.");
		}
		return heap;
	}

	private static boolean shouldUseZgc(HardwareProbe probe, Goal goal, int heapGib, List<String> rationale) {
		int java = probe.javaMajorVersion();

		if (!probe.sixtyFourBit() || java < 21) {
			return false;
		}
		// ZGC keeps extra native mappings alongside the heap. On a small machine that overhead is
		// the difference between fitting in RAM and swapping.
		if (probe.totalMemoryGib() > 0.0 && probe.totalMemoryGib() < 12.0) {
			return false;
		}
		// Its collection work runs on its own threads. With too few cores it steals from rendering.
		if (probe.logicalCores() < 8) {
			return false;
		}
		if (heapGib < 4) {
			return false;
		}

		// Minecraft 26.1 itself switched the default collector from G1 to ZGC on capable machines.
		// Handing back a G1 flag block would therefore be a downgrade from what the player already
		// has, not a tune-up, so ZGC is the default here too and G1 is the fallback.
		rationale.add(goal == Goal.LATENCY
				? "ZGC, as 26.1 now defaults to: its pauses stay under a millisecond regardless of heap "
						+ "size, so collection stops showing up as a dropped frame mid-fight."
				: "ZGC, matching what 26.1 already selects for this machine. Switching to G1 here would "
						+ "be a downgrade from the default, not a tune-up.");
		return true;
	}

	private static void appendZgcArguments(HardwareProbe probe, Goal goal, List<String> arguments,
			List<String> rationale, List<String> warnings) {
		arguments.add("-XX:+UseZGC");

		int java = probe.javaMajorVersion();
		if (java >= 21 && java < 24) {
			// Generational ZGC is opt-in on 21 to 23, and the only ZGC from 24 onward, where passing
			// the flag is at best a warning and at worst a refusal to start.
			arguments.add("-XX:+ZGenerational");
			rationale.add("ZGenerational is explicit here because Java " + java
					+ " still defaults to the older single-generation ZGC.");
		}

		if (goal == Goal.LATENCY) {
			arguments.add("-XX:-ZUncommit");
			rationale.add("ZUncommit is off so the heap is never handed back to the OS and then "
					+ "re-committed, which is a stall you would feel.");
		}

		warnings.add("ZGC reserves more address space and native memory than G1. That is normal and "
				+ "not a leak, but Task Manager will show a larger figure than the heap size.");
	}

	private static void appendG1Arguments(HardwareProbe probe, Goal goal, int heapGib, List<String> arguments,
			List<String> rationale) {
		int pauseTarget = switch (goal) {
			case LATENCY -> 37;
			case BALANCED -> 50;
			case HEAVY -> 100;
		};
		boolean largeHeap = heapGib >= 12;

		arguments.add("-XX:+UseG1GC");
		arguments.add("-XX:+ParallelRefProcEnabled");
		arguments.add("-XX:MaxGCPauseMillis=" + pauseTarget);
		arguments.add("-XX:+UnlockExperimentalVMOptions");
		arguments.add("-XX:G1NewSizePercent=" + (largeHeap ? 40 : 30));
		arguments.add("-XX:G1MaxNewSizePercent=" + (largeHeap ? 50 : 40));
		arguments.add("-XX:G1HeapRegionSize=" + (largeHeap ? "16M" : "8M"));
		arguments.add("-XX:G1ReservePercent=20");
		arguments.add("-XX:G1HeapWastePercent=5");
		arguments.add("-XX:G1MixedGCCountTarget=4");
		arguments.add("-XX:InitiatingHeapOccupancyPercent=15");
		arguments.add("-XX:G1MixedGCLiveThresholdPercent=90");
		arguments.add("-XX:G1RSetUpdatingPauseTimePercent=5");
		arguments.add("-XX:SurvivorRatio=32");
		arguments.add("-XX:MaxTenuringThreshold=1");

		rationale.add("A " + pauseTarget + " ms pause target with an oversized young generation: "
				+ "Minecraft allocates enormous numbers of very short-lived objects, so collecting "
				+ "the young generation early and often is cheaper than letting them be promoted.");
		rationale.add("MaxTenuringThreshold of 1 stops that short-lived garbage from being copied "
				+ "between survivor spaces before it dies anyway.");
	}

	private static void appendSharedArguments(HardwareProbe probe, int heapGib, List<String> arguments,
			List<String> rationale, List<String> warnings) {
		arguments.add("-XX:+DisableExplicitGC");
		rationale.add("DisableExplicitGC ignores System.gc() calls from mods, each of which is an "
				+ "unnecessary full collection and a visible stutter.");

		arguments.add("-XX:+PerfDisableSharedMem");
		rationale.add("PerfDisableSharedMem stops the JVM writing its performance counters to a file, "
				+ "which removes a source of frame-time jitter that has nothing to do with the game.");

		// Both are part of 26.1's own default argument list. Omitting them from a "tuned" line would
		// quietly take away something the player already had.
		if (probe.javaMajorVersion() >= 25) {
			arguments.add("-XX:+UseCompactObjectHeaders");
			rationale.add("UseCompactObjectHeaders shrinks every object header from 12 bytes to 8. On a "
					+ "workload made of millions of small objects that is a real reduction in heap use "
					+ "and in the amount of memory the collector has to walk. 26.1 enables it by default.");
		}
		arguments.add("-XX:+UseStringDeduplication");
		rationale.add("UseStringDeduplication merges identical string contents, which a game holding "
				+ "thousands of registry ids and translation keys benefits from. Also a 26.1 default.");

		// Pre-touching costs startup time and commits the whole heap immediately. Worth it when
		// there is room, harmful when there is not.
		double totalGib = probe.totalMemoryGib();
		if (totalGib <= 0.0 || totalGib - heapGib >= 4.0) {
			arguments.add("-XX:+AlwaysPreTouch");
			rationale.add("AlwaysPreTouch pays the cost of faulting in every heap page at startup, "
					+ "so the game never pauses to do it while you are playing. Expect a few extra "
					+ "seconds on launch.");
		} else {
			warnings.add("AlwaysPreTouch was left out: it would commit all " + heapGib
					+ " GiB immediately, which is too close to what this machine has.");
		}
	}

	private static void appendGeneralWarnings(HardwareProbe probe, int recommendedHeapGib, List<String> warnings) {
		if (!probe.sixtyFourBit()) {
			warnings.add("This is a 32-bit Java installation and cannot address more than about "
					+ "1.5 GiB. Install 64-bit Java; no flag can work around it.");
		}

		int java = probe.javaMajorVersion();
		if (java > 0 && java < 21) {
			warnings.add("Java " + java + " is older than what Minecraft 26.1 targets. Java 25 has "
					+ "meaningfully better collectors; these flags assume you are on it.");
		}

		double currentHeap = probe.currentMaxHeapGib();
		if (currentHeap > 0.0) {
			if (currentHeap > recommendedHeapGib * 1.6) {
				warnings.add(String.format(Locale.ROOT,
						"You are currently running with about %.1f GiB, well above the %d GiB suggested "
								+ "here. A larger heap does not make the game faster; it makes each "
								+ "collection take longer.",
						currentHeap, recommendedHeapGib));
			} else if (currentHeap < 2.0) {
				warnings.add(String.format(Locale.ROOT,
						"You are currently running with only %.1f GiB, which will thrash on any modded "
								+ "setup.",
						currentHeap));
			}
		}

		if (probe.totalMemoryGib() > 0.0 && probe.totalMemoryGib() <= 8.5) {
			warnings.add("With 8 GiB or less installed, what you close in the background will affect "
					+ "your frame times more than any of these flags.");
		}
	}

	/**
	 * A short advisory about frame pacing, which is not a JVM flag but is the other half of input
	 * latency and is worth saying on the same screen.
	 */
	public static String framePacingAdvice(HardwareProbe probe) {
		int refresh = probe.refreshRateHz();
		if (refresh <= 0) {
			return "For the lowest input latency, keep VSync off and use Unlimited FPS. While streaming, "
					+ "leave GPU headroom by reducing visual work rather than forcing a frame cap.";
		}
		return "Your display runs at " + refresh + " Hz. For the lowest input latency, keep VSync off "
				+ "and use Unlimited FPS. A stream may need GPU headroom; Core will recommend reducing "
				+ "visual work and will never apply a frame cap for you.";
	}
}
