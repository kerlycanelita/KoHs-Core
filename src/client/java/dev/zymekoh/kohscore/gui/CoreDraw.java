package dev.zymekoh.kohscore.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Drawing helpers built on the handful of primitives 26.1 actually gives a GUI.
 *
 * <p>There is no rounded-rectangle call and no shader here on purpose. Everything is {@code fill}
 * and {@code fillGradient}, which is why this screen costs almost nothing to draw even with a few
 * hundred particles moving behind it.
 */
public final class CoreDraw {

	private CoreDraw() {
	}

	/**
	 * A filled rectangle with rounded corners, approximated per scanline.
	 *
	 * <p>Two overlapping rectangles cover the middle, then the corner rows are filled individually
	 * using the circle equation. At the radii this UI uses, that is a handful of extra fills.
	 */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
			int color) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		if (r == 0) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}
		graphics.fill(x + r, y, x + width - r, y + height, color);
		graphics.fill(x, y + r, x + width, y + height - r, color);
		for (int dy = 0; dy < r; dy++) {
			int dx = (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
			graphics.fill(x + r - dx, y + dy, x + width - r + dx, y + dy + 1, color);
			graphics.fill(x + r - dx, y + height - dy - 1, x + width - r + dx, y + height - dy, color);
		}
	}

	/** A panel: drop shadow, border, fill, and a one-pixel highlight along the top edge. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int fill,
			int border) {
		roundedRect(graphics, x - 1, y + 1, width + 2, height + 2, radius + 1, CoreTheme.SHADOW);
		roundedRect(graphics, x, y, width, height, radius, border);
		roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);

		int inset = Math.max(2, radius);
		if (width > inset * 2 + 1 && height > 3) {
			graphics.fill(x + inset, y + 1, x + width - inset, y + 2,
					CoreTheme.withAlpha(CoreTheme.ACCENT_BRIGHT, 34));
		}
	}

	/**
	 * The surface behind one settings row.
	 *
	 * <p>Deliberately almost nothing at rest. Giving every row its own filled, bordered panel turns
	 * a list of ten settings into a grid of ten boxes, and the borders end up competing with the
	 * labels they are supposed to contain. A row is legible from its text alone, so the background
	 * only appears under the cursor, where it is doing real work: showing what the description panel
	 * is currently talking about.
	 *
	 * @param hover 0 at rest, 1 fully hovered
	 * @param alpha overall opacity, lowered when another mod owns the setting
	 * @param last  {@code true} to skip the trailing separator on the final row
	 */
	public static void rowSurface(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float hover,
			float alpha, boolean last) {
		if (hover > 0.01F) {
			roundedRect(graphics, x, y, width, height, 4,
					CoreTheme.fade(CoreTheme.withAlpha(CoreTheme.ROW_HOVER, Math.round(168 * hover)), alpha));
			// A short bar on the leading edge marks the active row without drawing a box around it.
			int barInset = Math.max(2, height / 5);
			graphics.fill(x, y + barInset, x + 2, y + height - barInset,
					CoreTheme.fade(CoreTheme.withAlpha(CoreTheme.ACCENT, Math.round(230 * hover)), alpha));
		}

		// The separator fades out as the highlight comes in, so the two never fight.
		if (!last && hover < 0.99F) {
			int separatorAlpha = Math.round(((CoreTheme.ROW_DIVIDER >>> 24) & 0xFF) * (1.0F - hover));
			graphics.fill(x + 4, y + height - 1, x + width - 4, y + height,
					CoreTheme.fade(CoreTheme.withAlpha(CoreTheme.ROW_DIVIDER, separatorAlpha), alpha));
		}
	}

	/** A soft halo around a rectangle, drawn as concentric fading outlines. */
	public static void glow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int spread,
			int alpha) {
		for (int step = spread; step > 0; step--) {
			int stepAlpha = Math.max(1, alpha * (spread - step + 1) / (spread * 3));
			roundedRect(graphics, x - step, y - step, width + step * 2, height + step * 2, 6 + step,
					CoreTheme.withAlpha(CoreTheme.ACCENT, stepAlpha));
		}
	}

	/** A one-pixel outline. */
	public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	/** A horizontal divider that fades out at both ends. */
	public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
		int half = width / 2;
		graphics.fillGradient(x, y, x + half, y + 1, CoreTheme.withAlpha(color, 0), color);
		graphics.fillGradient(x + half, y, x + width, y + 1, color, CoreTheme.withAlpha(color, 0));
	}

	/** Vertical fades at the top and bottom of a scrolling region. */
	public static void scrollFade(GuiGraphicsExtractor graphics, int x, int top, int width, int height,
			boolean fadeTop, boolean fadeBottom) {
		int fadeHeight = Math.min(14, Math.max(4, height / 5));
		int opaque = CoreTheme.withAlpha(CoreTheme.PANEL_DEEP, 220);
		int clear = CoreTheme.withAlpha(CoreTheme.PANEL_DEEP, 0);
		if (fadeTop) {
			graphics.fillGradient(x, top, x + width, top + fadeHeight, opaque, clear);
		}
		if (fadeBottom) {
			graphics.fillGradient(x, top + height - fadeHeight, x + width, top + height, clear, opaque);
		}
	}

	/** A scrollbar, drawn only when the content actually overflows. */
	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int height, int contentHeight,
			int viewportHeight, double scroll) {
		if (contentHeight <= viewportHeight) {
			return;
		}
		graphics.fill(x, y, x + 3, y + height, CoreTheme.SCROLL_TRACK);
		int thumbHeight = Math.max(16, height * viewportHeight / contentHeight);
		int travel = height - thumbHeight;
		int maxScroll = contentHeight - viewportHeight;
		int thumbY = y + (int) Math.round(travel * (scroll / maxScroll));
		graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, CoreTheme.SCROLL_THUMB);
	}

	/** Text truncated with an ellipsis when it will not fit. */
	public static void clippedText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
			int maxWidth, int color) {
		String raw = text.getString();
		if (font.width(raw) <= maxWidth) {
			graphics.text(font, text, x, y, color);
			return;
		}
		String trimmed = font.plainSubstrByWidth(raw, Math.max(0, maxWidth - font.width("...")));
		graphics.text(font, Component.literal(trimmed + "..."), x, y, color);
	}
}
