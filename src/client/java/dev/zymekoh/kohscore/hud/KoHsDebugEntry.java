package dev.zymekoh.kohscore.hud;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.optimize.StreamCadenceAdvisor;
import java.util.Locale;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** KoHs' selectable F3/F3+F6 readout. */
public final class KoHsDebugEntry implements DebugScreenEntry {

	@Override
	public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel,
			@Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
		displayer.addLine(String.format(Locale.ROOT, "KoHs: %d FPS, %.1f ms, worst %.1f ms",
				KoHsCoreClient.framesPerSecond(), KoHsCoreClient.frameTimeMillis(),
				KoHsCoreClient.worstFrameTimeMillis()));
		StreamCadenceAdvisor.Result cadence = StreamCadenceAdvisor.current();
		if (cadence.state() != StreamCadenceAdvisor.State.DISABLED) {
			displayer.addLine(String.format(Locale.ROOT, "KoHs stream cadence: %s (%d/%d FPS, %.2fx)",
					cadence.state().name().toLowerCase(Locale.ROOT), cadence.gameFps(), cadence.streamFps(),
					cadence.ratio()));
		}
	}

	@Override
	public boolean isAllowed(boolean reducedDebugInfo) {
		return true;
	}
}
