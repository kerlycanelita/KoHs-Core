package dev.zymekoh.kohscore.gui;

/**
 * The purple palette, in one place.
 *
 * <p>Deliberately close to the violet scale the other KoHs mods already use, so Core does not look
 * like a stranger sitting next to Herzium or Inventory Tweaks. Backgrounds are translucent because
 * Core's screen sits over the blurred game world rather than replacing it.
 *
 * <p>Status colours are muted rather than saturated: on a page listing a dozen rows, a pure red
 * reads as an alarm instead of as information.
 */
public final class CoreTheme {

	private CoreTheme() {
	}

	// Backdrop, drawn over the blurred world.
	public static final int BACKDROP_TOP = 0x8C2A0B4E;
	public static final int BACKDROP_BOTTOM = 0xA6140628;
	public static final int MODAL_DIM = 0xA20B0612;

	// Panels.
	public static final int PANEL = 0xD01A0E2E;
	public static final int PANEL_LIGHT = 0xC2210F38;
	public static final int PANEL_HOVER = 0xE32C1450;
	public static final int PANEL_SELECTED = 0xE83A1866;
	public static final int PANEL_DEEP = 0xC8140A22;

	// Edges.
	public static final int BORDER = 0xB09B70C5;
	public static final int BORDER_SOFT = 0x7053336F;
	public static final int HAIRLINE = 0x2E9878C4;
	public static final int SHADOW = 0x525B21B6;

	// Accents.
	public static final int ACCENT = 0xFFA855F7;
	public static final int ACCENT_BRIGHT = 0xFFD8B4FE;
	public static final int ACCENT_SOFT = 0xBFC77DFF;
	public static final int ACCENT_DEEP = 0xEA7C3AED;

	// Text.
	public static final int TEXT = 0xFFF7EEFF;
	public static final int TEXT_MUTED = 0xFFCDB9DF;
	public static final int TEXT_DIM = 0xFF9E8FB4;
	public static final int TEXT_DISABLED = 0xFF6E6080;

	// Status, kept desaturated on purpose.
	public static final int GOOD = 0xFF67C98A;
	public static final int WARN = 0xFFDCA85C;
	public static final int BAD = 0xFFDE6E72;

	// Settings rows. Both are quiet on purpose: the row is read from its text, not its container.
	public static final int ROW_HOVER = 0xFF3A1C5E;
	public static final int ROW_DIVIDER = 0x2E7B5EA0;

	// Scroll furniture.
	public static final int SCROLL_TRACK = 0x3A31204A;
	public static final int SCROLL_THUMB = 0x9EA974F0;

	/** Replaces the alpha channel of a colour. */
	public static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
	}

	/** Multiplies the existing alpha of a colour by a 0..1 factor. */
	public static int fade(int color, float factor) {
		int alpha = (color >>> 24) & 0xFF;
		return withAlpha(color, Math.round(alpha * Math.max(0.0F, Math.min(1.0F, factor))));
	}

	/** Blends two ARGB colours channel by channel. */
	public static int blend(int from, int to, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int a = lerpChannel(from >>> 24 & 0xFF, to >>> 24 & 0xFF, t);
		int r = lerpChannel(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
		int g = lerpChannel(from >> 8 & 0xFF, to >> 8 & 0xFF, t);
		int b = lerpChannel(from & 0xFF, to & 0xFF, t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** Builds an ARGB int from separate channels. */
	public static int argb(int alpha, int red, int green, int blue) {
		return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF);
	}

	/** Takes an 0xRRGGBB value and gives it an alpha. */
	public static int opaque(int rgb, int alpha) {
		return (alpha & 0xFF) << 24 | (rgb & 0x00FFFFFF);
	}

	private static int lerpChannel(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}

	// --- Easing ------------------------------------------------------------------------------

	/** Decelerating curve, good for entrances. */
	public static float cubicOut(float t) {
		float inverse = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
		return 1.0F - inverse * inverse * inverse;
	}

	/** Smoothstep over an elapsed/duration pair. */
	public static float ease(long elapsedMillis, long durationMillis) {
		if (elapsedMillis <= 0L) {
			return 0.0F;
		}
		if (elapsedMillis >= durationMillis) {
			return 1.0F;
		}
		float t = (float) elapsedMillis / durationMillis;
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * Frame-rate independent approach factor.
	 *
	 * <p>Multiplying a difference by a constant each frame makes animations run at different speeds
	 * on different machines. This gives the same curve in wall-clock time at any frame rate.
	 *
	 * @param elapsedSeconds time since the previous frame
	 * @param response       higher converges faster
	 */
	public static float approach(float elapsedSeconds, float response) {
		return 1.0F - (float) Math.exp(-response * elapsedSeconds);
	}
}
