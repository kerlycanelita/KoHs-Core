package dev.zymekoh.kohscore.api;

import java.util.Locale;

/**
 * A slice of client behaviour that exactly one mod should be driving at a time.
 *
 * <p>Core owns every domain by default. When a sibling mod claims one, Core stops writing to it and
 * greys out the matching controls in its own screen rather than silently losing a tug of war with
 * whoever else is setting the same value every frame.
 */
public enum Domain {
	/** Which particle types spawn at all, and how many of them per second. */
	PARTICLES,
	/** Cloud rendering: on/off, height, render range. */
	CLOUDS,
	/** Graphics preset, leaf cutout, transparency, texture filtering, mipmaps. */
	GRAPHICS_QUALITY,
	/** Render and simulation distance, biome blend, chunk update priority. */
	VIEW_DISTANCE,
	/** Skipping entity and block-entity rendering by distance or visibility. */
	ENTITY_CULLING,
	/** Framerate cap, vsync, and the idle/unfocused throttles. */
	FRAME_PACING,
	/** Raw input, sensitivity handling, and anything else on the mouse-to-camera path. */
	INPUT_LATENCY,
	/** Recomputing and uploading the client lightmap. */
	LIGHTMAP,
	/** How often animated atlas sprites advance. */
	ANIMATED_TEXTURES,
	/** Cosmetic entity poses and dropped-item spin/bob, never position or gameplay ticks. */
	ENTITY_ANIMATIONS,
	/** Banner, book, chest, bell and conduit visual phases. */
	BLOCK_ANIMATIONS,
	/** First-person held-item swing, equip and use transforms. */
	HAND_ANIMATIONS,
	/** Idle behaviour of the client event loop while the window is unfocused. */
	BACKGROUND_CPU,
	/** Local privacy masking and stream-cadence diagnostics. */
	STREAMING,
	/** Cache trimming, allocation pressure, and the JVM advisor. */
	MEMORY,
	/** Weather and sky rendering density. */
	WEATHER,
	/** Screen-space visual extras: vignette, bobbing, glint, screen distortion. */
	SCREEN_EFFECTS,
	/** Inventory opening, cursor placement and container-specific rendering. */
	INVENTORY_UI,
	/** KoHs Anchors prediction and its protected explosion visuals. */
	ANCHOR_EFFECTS,
	/** Player death models, animations and replacement rendering. */
	DEATH_EFFECTS,
	/** Hotbar previews, hand transitions and related HUD rendering. */
	HOTBAR_EFFECTS;

	private final String id = name().toLowerCase(Locale.ROOT);

	/** Stable lowercase id, safe to persist and to send across a mod boundary. */
	public String id() {
		return this.id;
	}

	/** Translation key for the domain's display name. */
	public String translationKey() {
		return "kohs_core.domain." + this.id;
	}
}
