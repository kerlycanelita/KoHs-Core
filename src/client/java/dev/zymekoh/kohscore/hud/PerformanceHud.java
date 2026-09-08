package dev.zymekoh.kohscore.hud;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import dev.zymekoh.kohscore.optimize.MemoryGovernor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A small frame-rate overlay.
 *
 * <p>F3 already shows a frame rate, but it also covers a third of the screen with information that
 * is useless mid-fight. This shows the three numbers that actually tell you whether a setting helped
 * and nothing else.
 *
 * <p>The worst-frame figure is the reason this exists. An average of 120 with one 40 ms frame every
 * second feels considerably worse than a steady 90, and an average alone cannot tell the two apart —
 * which is exactly the mistake that leads people to tune the wrong setting.
 */
public final class PerformanceHud {

	private static final int PADDING = 4;
	private static final int MARGIN = 4;

	private PerformanceHud() {
	}

	/** Draws the overlay when enabled. Called once per frame from the HUD element. */
	public static void render(GuiGraphicsExtractor graphics) {
		CoreConfig config = ConfigStore.get();
		if (!config.performanceHudEnabled) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.options.hideGui || minecraft.getDebugOverlay() != null
				&& minecraft.getDebugOverlay().showDebugScreen()) {
			// Never stack on top of F3; that screen already owns the corner.
			return;
		}

		Font font = minecraft.font;
		List<Component> lines = buildLines(config);
		if (lines.isEmpty()) {
			return;
		}

		int textWidth = 0;
		for (Component line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int boxWidth = textWidth + PADDING * 2;
		int boxHeight = lines.size() * (font.lineHeight + 1) - 1 + PADDING * 2;

		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		boolean right = config.performanceHudCorner == 1 || config.performanceHudCorner == 3;
		boolean bottom = config.performanceHudCorner >= 2;
		int x = right ? screenWidth - MARGIN - boxWidth : MARGIN;
		int y = bottom ? screenHeight - MARGIN - boxHeight : MARGIN;

		CoreDraw.roundedRect(graphics, x, y, boxWidth, boxHeight, 3,
				CoreTheme.withAlpha(CoreTheme.PANEL_DEEP, 150));

		int lineY = y + PADDING;
		for (int index = 0; index < lines.size(); index++) {
			// Only the first line is colour-coded; colouring every row turns a readout into a
			// traffic light nobody can read at a glance.
			int color = index == 0 ? pressureColor() : CoreTheme.TEXT_MUTED;
			graphics.text(font, lines.get(index), x + PADDING, lineY, color);
			lineY += font.lineHeight + 1;
		}
	}

	private static List<Component> buildLines(CoreConfig config) {
		List<Component> lines = new ArrayList<>(3);
		lines.add(Component.literal(KoHsCoreClient.framesPerSecond() + " FPS"));

		if (config.performanceHudDetail >= 1) {
			lines.add(Component.literal(String.format(Locale.ROOT, "%.1f ms",
					KoHsCoreClient.frameTimeMillis())));
		}
		if (config.performanceHudDetail >= 2) {
			lines.add(Component.literal(String.format(Locale.ROOT, "worst %.0f ms",
					KoHsCoreClient.worstFrameTimeMillis())));
			lines.add(Component.literal(MemoryGovernor.usedHeapMib() + " / "
					+ MemoryGovernor.maxHeapMib() + " MiB"));
		}
		return lines;
	}

	private static int pressureColor() {
		return switch (KoHsCoreClient.pressure()) {
			case RELAXED -> CoreTheme.GOOD;
			case ELEVATED -> CoreTheme.TEXT;
			case HIGH -> CoreTheme.WARN;
			case CRITICAL -> CoreTheme.BAD;
		};
	}
}
