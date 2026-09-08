package dev.zymekoh.kohscore.gui.preview;

/**
 * A motion and colour recipe used to draw a particle preview.
 *
 * <p>Core does not run the real particle simulation in the settings screen. Doing that would mean
 * instantiating vanilla {@code Particle} objects, which need a live {@code ClientLevel} and a camera
 * that the options screen has no business creating. So each registry id is matched to one of these
 * recipes instead, and the preview animates from the recipe. It is a stylised likeness, not a
 * frame-accurate simulation, and it is honest about being one.
 *
 * @param spawnShape   where new particles appear inside the preview box
 * @param driftX       horizontal velocity in preview pixels per second
 * @param driftY       vertical velocity, negative is upward
 * @param spread       random velocity added on both axes
 * @param gravity      downward acceleration in pixels per second squared
 * @param sizeMin      smallest quad edge in pixels
 * @param sizeMax      largest quad edge in pixels
 * @param lifeSeconds  how long one particle lives
 * @param colorPrimary main tint, 0xRRGGBB
 * @param colorAccent  secondary tint blended in over the lifetime, 0xRRGGBB
 * @param expands      whether the quad grows as it ages, like smoke
 * @param flickers     whether alpha stutters frame to frame, like fire
 * @param density      how many live at once, scaled by the preview size
 */
public record ParticleArchetype(SpawnShape spawnShape, float driftX, float driftY, float spread, float gravity,
		float sizeMin, float sizeMax, float lifeSeconds, int colorPrimary, int colorAccent, boolean expands,
		boolean flickers, int density) {

	/** Where a fresh particle is placed inside the preview box. */
	public enum SpawnShape {
		/** Anywhere in the box. */
		SCATTER,
		/** From a point in the middle, moving outward. */
		CENTRE,
		/** Along the bottom edge. */
		FLOOR,
		/** Along the top edge. */
		CEILING,
		/** On a ring, moving inward toward the middle. */
		INWARD
	}

	// Reused recipes. Kept as constants so the lookup table below stays readable.

	public static final ParticleArchetype FLAME = new ParticleArchetype(SpawnShape.FLOOR, 0.0F, -18.0F, 6.0F, -4.0F,
			1.5F, 3.0F, 1.4F, 0xFFAA33, 0xFFE9A0, false, true, 26);
	public static final ParticleArchetype SOUL_FLAME = new ParticleArchetype(SpawnShape.FLOOR, 0.0F, -16.0F, 5.0F,
			-3.0F, 1.5F, 3.0F, 1.6F, 0x38D8E0, 0xCFF8FF, false, true, 24);
	public static final ParticleArchetype SMOKE = new ParticleArchetype(SpawnShape.FLOOR, 2.0F, -14.0F, 5.0F, -2.0F,
			2.0F, 5.0F, 2.2F, 0x4A4A4A, 0x9A9A9A, true, false, 20);
	public static final ParticleArchetype CLOUD = new ParticleArchetype(SpawnShape.CENTRE, 0.0F, -6.0F, 10.0F, 0.0F,
			3.0F, 7.0F, 1.8F, 0xD8D8E4, 0xFFFFFF, true, false, 16);
	public static final ParticleArchetype WATER = new ParticleArchetype(SpawnShape.CEILING, 0.0F, 26.0F, 7.0F, 42.0F,
			1.0F, 2.0F, 1.2F, 0x3F76E4, 0x8AB6FF, false, false, 26);
	public static final ParticleArchetype BUBBLE = new ParticleArchetype(SpawnShape.FLOOR, 0.0F, -22.0F, 4.0F, -6.0F,
			1.5F, 3.0F, 1.5F, 0x6FA8FF, 0xD7E9FF, false, false, 24);
	public static final ParticleArchetype LAVA = new ParticleArchetype(SpawnShape.FLOOR, 0.0F, -20.0F, 9.0F, 46.0F,
			2.0F, 4.0F, 1.4F, 0xE04B0F, 0xFFC24A, false, true, 14);
	public static final ParticleArchetype DRIP = new ParticleArchetype(SpawnShape.CEILING, 0.0F, 4.0F, 1.0F, 34.0F,
			1.5F, 2.5F, 1.9F, 0x5A93FF, 0xB9D4FF, false, false, 10);
	public static final ParticleArchetype MAGIC = new ParticleArchetype(SpawnShape.SCATTER, 0.0F, -8.0F, 14.0F, 0.0F,
			1.5F, 3.0F, 1.7F, 0xA855F7, 0xE9C7FF, false, false, 26);
	public static final ParticleArchetype PORTAL = new ParticleArchetype(SpawnShape.INWARD, 0.0F, 0.0F, 4.0F, 0.0F,
			1.0F, 2.5F, 1.5F, 0x8B2FD6, 0xD9A8FF, false, false, 30);
	public static final ParticleArchetype ENCHANT = new ParticleArchetype(SpawnShape.INWARD, 0.0F, 0.0F, 3.0F, 0.0F,
			1.0F, 2.0F, 1.4F, 0xC8C0A0, 0xFFF6D0, false, false, 30);
	public static final ParticleArchetype SPARK = new ParticleArchetype(SpawnShape.CENTRE, 0.0F, 0.0F, 46.0F, 24.0F,
			1.0F, 2.0F, 0.9F, 0xFFF2A8, 0xFFFFFF, false, true, 22);
	public static final ParticleArchetype EXPLOSION = new ParticleArchetype(SpawnShape.CENTRE, 0.0F, -2.0F, 30.0F,
			0.0F, 4.0F, 9.0F, 1.0F, 0xC8C8C8, 0xFFFFFF, true, false, 12);
	public static final ParticleArchetype DUST = new ParticleArchetype(SpawnShape.SCATTER, 3.0F, 3.0F, 6.0F, 2.0F,
			1.0F, 2.5F, 2.0F, 0xD05CD0, 0xFFB0FF, false, false, 22);
	public static final ParticleArchetype BLOCK = new ParticleArchetype(SpawnShape.CENTRE, 0.0F, -14.0F, 18.0F, 60.0F,
			2.0F, 4.0F, 1.1F, 0x8A7A5C, 0xB7A47C, false, false, 18);
	public static final ParticleArchetype SNOW = new ParticleArchetype(SpawnShape.CEILING, 3.0F, 12.0F, 5.0F, 2.0F,
			1.5F, 3.0F, 2.4F, 0xE8F4FF, 0xFFFFFF, false, false, 24);
	public static final ParticleArchetype RAIN = new ParticleArchetype(SpawnShape.CEILING, 2.0F, 60.0F, 4.0F, 20.0F,
			1.0F, 2.0F, 0.8F, 0x5A7FD0, 0x9FC0FF, false, false, 30);
	public static final ParticleArchetype HEART = new ParticleArchetype(SpawnShape.FLOOR, 0.0F, -12.0F, 5.0F, -1.0F,
			3.0F, 5.0F, 1.8F, 0xFF5A8A, 0xFFB6CE, false, false, 10);
	public static final ParticleArchetype NOTE = new ParticleArchetype(SpawnShape.FLOOR, 6.0F, -14.0F, 4.0F, 0.0F,
			3.0F, 5.0F, 1.6F, 0x5AD08A, 0xC0FFD8, false, false, 10);
	public static final ParticleArchetype SCULK = new ParticleArchetype(SpawnShape.SCATTER, 0.0F, -6.0F, 9.0F, 0.0F,
			1.5F, 3.5F, 1.9F, 0x1B7A82, 0x63E4E0, false, true, 22);
	public static final ParticleArchetype ASH = new ParticleArchetype(SpawnShape.SCATTER, 4.0F, 8.0F, 5.0F, 3.0F,
			1.0F, 2.0F, 2.6F, 0x6A6058, 0xA89C8E, false, false, 26);
	public static final ParticleArchetype GENERIC = new ParticleArchetype(SpawnShape.SCATTER, 0.0F, -6.0F, 9.0F, 2.0F,
			1.5F, 3.0F, 1.7F, 0xB98CE0, 0xE8D4FF, false, false, 20);
}
