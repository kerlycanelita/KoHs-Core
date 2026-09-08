package dev.zymekoh.kohscore.optimize;

/**
 * Heap telemetry for Core's allocation guards.
 *
 * <p>Earlier prototypes called {@code System.gc()} on a timer. That can pause every game thread at
 * once and produces exactly the symptom an optimiser must avoid: severe one-frame stalls while the
 * FPS average still looks healthy. Core never requests a collection now. Java's collector decides
 * when to collect; Core reduces transient allocations before they reach it through the particle
 * governor and optional visibility limits.
 *
 * <p>The input path is intentionally untouched. No mouse/key callback, cooldown, action or packet
 * is changed by memory management, so this stays a purely local L0 optimisation.
 */
public final class MemoryGovernor {

	private volatile float lastHeapUsedRatio;

	/** Kept for API compatibility; automatic explicit collections are deliberately unavailable. */
	public boolean compactionAvailable() {
		return false;
	}

	/** Compatibility hook; Core observes no global input and changes no input behaviour. */
	public void noteInput() {
		// Intentionally empty.
	}

	/** Records telemetry only. This method never blocks, allocates, sleeps or requests GC. */
	public void tick(double playerX, double playerZ, float heapUsedRatio) {
		this.lastHeapUsedRatio = Math.max(0.0F, Math.min(1.0F, heapUsedRatio));
	}

	/** No-op compatibility hook: screen changes no longer trigger a forced collection. */
	public void onScreenChange() {
		// Intentionally empty.
	}

	/** No explicit collection button is exposed because it cannot guarantee a hitch-free frame. */
	public boolean compactNow() {
		return false;
	}

	/** Most recent occupancy ratio sampled by the pressure monitor. */
	public float heapUsedRatio() {
		return this.lastHeapUsedRatio;
	}

	/** Used heap in mebibytes. */
	public static long usedHeapMib() {
		Runtime runtime = Runtime.getRuntime();
		return (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L;
	}

	/** Maximum heap in mebibytes. */
	public static long maxHeapMib() {
		return Runtime.getRuntime().maxMemory() / 1_048_576L;
	}
}
