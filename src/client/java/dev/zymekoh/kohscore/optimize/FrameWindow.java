package dev.zymekoh.kohscore.optimize;

/** Monotonic frame intervals, sampled independently of HUD visibility. No per-frame allocations. */
public final class FrameWindow {
	private static final long INTERVAL = 1_000_000_000L;
	private boolean started;
	private long sampleStart;
	private long previous;
	private long worst;
	private int count;
	public record Sample(int fps, float meanMillis, float worstMillis) { }

	/** Returns a completed sample once a second, otherwise null. The first frame starts the clock. */
	public Sample frame(long now) {
		if (!started) {
			started = true;
			sampleStart = previous = now;
			return null;
		}
		long gap = now - previous;
		if (gap <= 0) return null;
		previous = now;
		worst = Math.max(worst, gap); // Keep severe >1s stalls visible as well.
		count++;
		long elapsed = now - sampleStart;
		if (elapsed < INTERVAL) return null;
		Sample sample = new Sample(Math.round(count * (1_000_000_000.0F / elapsed)),
				(float) ((double) elapsed / count / 1_000_000.0), worst / 1_000_000.0F);
		sampleStart = now;
		worst = 0;
		count = 0;
		return sample;
	}
}
