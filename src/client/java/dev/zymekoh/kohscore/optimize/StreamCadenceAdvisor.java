package dev.zymekoh.kohscore.optimize;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.config.ConfigStore;

/** Read-only stream cadence diagnostics. Never changes VSync or Minecraft's frame limit. */
public final class StreamCadenceAdvisor {

	public enum State {
		DISABLED,
		WAITING,
		BELOW_STREAM_RATE,
		CLEAN_MULTIPLE,
		VARIABLE
	}

	public record Result(State state, int gameFps, int streamFps, double ratio) {
	}

	private StreamCadenceAdvisor() {
	}

	public static Result current() {
		int streamFps = ConfigStore.get().streamOutputFps;
		if (streamFps <= 0) {
			return new Result(State.DISABLED, 0, 0, 0.0);
		}
		int gameFps = KoHsCoreClient.framesPerSecond();
		if (gameFps <= 0) {
			return new Result(State.WAITING, 0, streamFps, 0.0);
		}
		double ratio = (double) gameFps / streamFps;
		if (gameFps < streamFps) {
			return new Result(State.BELOW_STREAM_RATE, gameFps, streamFps, ratio);
		}
		long nearestMultiple = Math.max(1L, Math.round(ratio));
		boolean clean = Math.abs(ratio - nearestMultiple) <= 0.03;
		return new Result(clean ? State.CLEAN_MULTIPLE : State.VARIABLE, gameFps, streamFps, ratio);
	}
}
