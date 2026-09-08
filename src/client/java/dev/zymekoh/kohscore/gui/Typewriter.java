package dev.zymekoh.kohscore.gui;

import dev.zymekoh.kohscore.config.ConfigStore;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Types a description out one character at a time.
 *
 * <p>The speed is a setting rather than a constant because the useful range is wider than it looks.
 * At the 0.1 ms floor the whole sentence lands inside a single frame, which is effectively instant;
 * the 6 ms default is about the fastest speed where the effect is still legible as an animation at
 * 60 Hz. Anything slower than roughly 30 ms starts to feel like waiting.
 *
 * <p>Progress is measured against wall-clock time, so the reveal takes the same real duration
 * regardless of frame rate, and a hovered-then-unhovered-then-rehovered control restarts cleanly.
 */
public final class Typewriter {

	private static final String ELLIPSIS = "...";

	private Component text = Component.empty();
	private String rawText = "";
	private long startedAtMillis;
	private int totalCharacters;
	private int cachedWidth = -1;
	private int cachedVisible = -1;
	private List<FormattedCharSequence> cachedLines = List.of();

	/**
	 * Points the typewriter at a new string. Re-setting the same text is a no-op, so holding the
	 * cursor still does not restart the animation every frame.
	 */
	public void setText(Component next) {
		if (next.equals(this.text)) {
			return;
		}
		this.text = next;
		this.rawText = next.getString();
		this.totalCharacters = this.rawText.length();
		this.startedAtMillis = System.nanoTime() / 1_000_000L;
		this.cachedWidth = -1;
		this.cachedVisible = -1;
		this.cachedLines = List.of();
	}

	/** Clears the panel. */
	public void clear() {
		this.setText(Component.empty());
	}

	public Component text() {
		return this.text;
	}

	/** How many characters should be visible this frame. */
	private int visibleCharacters() {
		if (!ConfigStore.get().typewriterDescriptions) {
			return this.totalCharacters;
		}
		float millisPerChar = ConfigStore.get().typewriterMillisPerChar;
		long elapsed = System.nanoTime() / 1_000_000L - this.startedAtMillis;
		if (millisPerChar <= 0.0F) {
			return this.totalCharacters;
		}
		int revealed = (int) (elapsed / millisPerChar);
		return Math.min(this.totalCharacters, Math.max(0, revealed));
	}

	/** Whether the whole string is on screen. */
	public boolean isComplete() {
		return this.visibleCharacters() >= this.totalCharacters;
	}

	/**
	 * Draws the partially revealed text, wrapped to a width.
	 *
	 * @return the height in pixels that was drawn
	 */
	public int render(GuiGraphicsExtractor graphics, Font font, int x, int y, int maxWidth, int color) {
		return this.render(graphics, font, x, y, maxWidth, Integer.MAX_VALUE, color);
	}

	/**
	 * Draws the revealed text, wrapped to a width and capped to a height.
	 *
	 * <p>The height cap matters on short windows, where the description panel shrinks to two or
	 * three lines. Without it the last line is sliced in half by the panel's scissor, which reads as
	 * a rendering bug rather than as truncation. Whole lines are dropped instead, and an ellipsis on
	 * the final one says the sentence continues.
	 *
	 * @param maxHeight pixels available; lines that would not fit whole are not drawn
	 * @return the height in pixels that was drawn
	 */
	public int render(GuiGraphicsExtractor graphics, Font font, int x, int y, int maxWidth, int maxHeight,
			int color) {
		if (this.totalCharacters == 0) {
			return 0;
		}
		int visible = this.visibleCharacters();
		if (visible != this.cachedVisible || maxWidth != this.cachedWidth) {
			String shown = visible >= this.rawText.length()
					? this.rawText : this.rawText.substring(0, visible);
			// Wrapping the truncated string rather than the full one means lines appear as they fill,
			// which is the behaviour people expect from a terminal. Cache it until another character
			// appears so a completed tooltip allocates nothing on subsequent frames.
			this.cachedLines = font.split(Component.literal(shown), Math.max(1, maxWidth));
			this.cachedVisible = visible;
			this.cachedWidth = maxWidth;
		}
		List<FormattedCharSequence> lines = this.cachedLines;
		int lineStep = font.lineHeight + 1;

		// n lines occupy (n - 1) steps plus one full line of glyphs. Solving that for the available
		// height gives the number that fit whole; anything beyond it is dropped rather than sliced.
		int drawableLines = maxHeight == Integer.MAX_VALUE
				? lines.size()
				: Math.max(1, (maxHeight - font.lineHeight) / lineStep + 1);
		boolean truncated = lines.size() > drawableLines;
		int lineCount = Math.min(lines.size(), drawableLines);

		int lineY = y;
		for (int index = 0; index < lineCount; index++) {
			graphics.text(font, lines.get(index), x, lineY, color);
			lineY += lineStep;
		}

		if (truncated) {
			// Marks the cut at the end of the last line that did fit, so a clipped description does
			// not read as the whole description.
			int ellipsisWidth = font.width(ELLIPSIS);
			graphics.text(font, Component.literal(ELLIPSIS), x + Math.max(0, maxWidth - ellipsisWidth),
					lineY - lineStep, color);
		}

		// A caret while typing, so a half-drawn sentence reads as in progress rather than as a bug.
		if (!truncated && visible < this.rawText.length() && !lines.isEmpty()) {
			long blink = System.nanoTime() / 1_000_000L / 300L;
			if (blink % 2L == 0L) {
				FormattedCharSequence lastLine = lines.get(lines.size() - 1);
				int caretX = x + font.width(lastLine);
				int caretY = lineY - font.lineHeight - 1;
				graphics.fill(caretX + 1, caretY, caretX + 2, caretY + font.lineHeight,
						CoreTheme.withAlpha(CoreTheme.ACCENT_BRIGHT, 200));
			}
		}

		return Math.max(0, lineY - y);
	}
}
