package dev.zymekoh.kohscore.optimize;

import dev.zymekoh.kohscore.api.CoreBus;
import dev.zymekoh.kohscore.api.CoreSignals;
import dev.zymekoh.kohscore.api.PressureLevel;
import dev.zymekoh.kohscore.config.ConfigStore;
import net.minecraft.client.Minecraft;

/**
 * Works out how hard the client is struggling and tells everyone else once a second.
 *
 * <p>Frame rate alone is a poor signal, because a player capped at 60 on a strong machine looks
 * identical to a player fighting for 60 on a weak one. So this combines the frame time against the
 * player's own cap with heap occupancy, and only escalates when a reading persists. Escalation is
 * immediate but de-escalation is slow, which stops effects from flickering on and off around a
 * threshold.
 */
public final class PressureMonitor {

	private static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;
	/** Longer gaps mean rendering was paused (screen switch, breakpoint, minimisation), not a frame. */
	private static final long MAX_TRACKED_FRAME_NANOS = 1_000_000_000L;
	/** How many consecutive calm samples are needed before stepping back down a level. */
	private static final int RECOVERY_SAMPLES = 3;

	private final CoreBus bus;

	private long lastSampleNanos = System.nanoTime();
	private long lastFrameNanos;
	private long worstFrameNanos;
	private int framesSinceSample;
	private int calmSamples;

	private volatile PressureLevel pressure = PressureLevel.RELAXED;
	private volatile int framesPerSecond;
	private volatile float frameTimeMillis;
	private volatile float worstFrameTimeMillis;
	private volatile float heapUsedRatio;

	public PressureMonitor(CoreBus bus) {
		this.bus = bus;
	}

	/** Called once per rendered frame. Deliberately trivial; the real work happens once a second. */
	public void onFrame() {
		long now = System.nanoTime();
		if (this.lastFrameNanos != 0L) {
			long frameNanos = now - this.lastFrameNanos;
			if (frameNanos > 0L && frameNanos <= MAX_TRACKED_FRAME_NANOS) {
				this.worstFrameNanos = Math.max(this.worstFrameNanos, frameNanos);
			}
		}
		this.lastFrameNanos = now;
		this.framesSinceSample++;
		long elapsed = now - this.lastSampleNanos;
		if (elapsed >= SAMPLE_INTERVAL_NANOS) {
			this.sample(elapsed);
			this.lastSampleNanos = now;
			this.framesSinceSample = 0;
		}
	}

	private void sample(long elapsedNanos) {
		float seconds = elapsedNanos / 1_000_000_000.0F;
		this.framesPerSecond = Math.round(this.framesSinceSample / seconds);
		this.frameTimeMillis = this.framesSinceSample == 0 ? 0.0F : seconds * 1000.0F / this.framesSinceSample;
		this.worstFrameTimeMillis = this.worstFrameNanos / 1_000_000.0F;
		this.worstFrameNanos = 0L;

		Runtime runtime = Runtime.getRuntime();
		long max = runtime.maxMemory();
		long used = runtime.totalMemory() - runtime.freeMemory();
		this.heapUsedRatio = max <= 0L ? 0.0F : (float) used / max;

		PressureLevel measured = measure(this.framesPerSecond, this.heapUsedRatio,
				targetFrameRate(), this.worstFrameTimeMillis);
		PressureLevel previous = this.pressure;

		if (measured.ordinal() > previous.ordinal()) {
			// Trouble is acted on the moment it is seen.
			this.pressure = measured;
			this.calmSamples = 0;
		} else if (measured.ordinal() < previous.ordinal()) {
			// Recovery has to be earned, so effects do not flicker around the threshold.
			this.calmSamples++;
			if (this.calmSamples >= RECOVERY_SAMPLES) {
				this.pressure = PressureLevel.values()[previous.ordinal() - 1];
				this.calmSamples = 0;
			}
		} else {
			this.calmSamples = 0;
		}

		this.bus.publish(new CoreSignals.Performance(this.framesPerSecond, this.frameTimeMillis,
				this.heapUsedRatio, this.pressure));
	}

	private static PressureLevel measure(int fps, float heapRatio, int targetFps, float worstFrameMillis) {
		float heapThreshold = ConfigStore.get().heapPressureThreshold;
		float targetFrameMillis = 1000.0F / Math.max(1, targetFps);

		// A nearly full heap is worse news than a low frame rate: it means GC pauses are coming.
		if (heapRatio >= Math.min(0.97F, heapThreshold + 0.10F)
				|| worstFrameMillis >= Math.max(120.0F, targetFrameMillis * 8.0F)) {
			return PressureLevel.CRITICAL;
		}
		if (fps > 0 && fps < Math.max(10, Math.round(targetFps * 0.35F))) {
			return PressureLevel.CRITICAL;
		}
		if (heapRatio >= heapThreshold
				|| worstFrameMillis >= Math.max(55.0F, targetFrameMillis * 4.0F)
				|| (fps > 0 && fps < Math.max(18, Math.round(targetFps * 0.58F)))) {
			return PressureLevel.HIGH;
		}
		if (worstFrameMillis >= Math.max(28.0F, targetFrameMillis * 2.25F)
				|| (fps > 0 && fps < Math.max(28, Math.round(targetFps * 0.82F)))) {
			return PressureLevel.ELEVATED;
		}
		return PressureLevel.RELAXED;
	}

	/** The FPS the player actually asked for, not an assumed 60 FPS desktop. */
	private static int targetFrameRate() {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			int configured = minecraft.options.framerateLimit().get();
			int refresh = minecraft.getWindow() == null ? 0 : minecraft.getWindow().getRefreshRate();
			if (minecraft.options.enableVsync().get() && refresh > 0) {
				return configured >= 260 ? refresh : Math.min(configured, refresh);
			}
			if (configured < 260) {
				return Math.max(30, configured);
			}
			return refresh > 0 ? refresh : 60;
		} catch (RuntimeException ignored) {
			return 60;
		}
	}

	public PressureLevel pressure() {
		return this.pressure;
	}

	public int framesPerSecond() {
		return this.framesPerSecond;
	}

	public float frameTimeMillis() {
		return this.frameTimeMillis;
	}

	/** Worst real rendered-frame gap observed in the latest one-second sample. */
	public float worstFrameTimeMillis() {
		return this.worstFrameTimeMillis;
	}

	public float heapUsedRatio() {
		return this.heapUsedRatio;
	}
}
