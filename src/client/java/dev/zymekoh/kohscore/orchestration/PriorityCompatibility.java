package dev.zymekoh.kohscore.orchestration;

import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Narrow compatibility reservations for the KoHs mods that always outrank Core.
 *
 * <p>A broad domain claim would make the entire particle page unusable just because Anchors owns
 * two explosion types. These reservations describe the exact resources Core must leave alone,
 * while the domain table in {@link KnownSiblings} documents the larger feature each sibling owns.
 */
public final class PriorityCompatibility {

	public record Reservation(String ownerModId, String ownerName, String reasonKey) {
	}

	private static final Reservation ANCHORS_PARTICLES = new Reservation(
			"kohs_anchors", "KoHs Anchors", "kohs_core.yield.anchors_particles");

	private static final Map<String, Reservation> RESERVED_PARTICLES = Map.of(
			"minecraft:explosion", ANCHORS_PARTICLES,
			"minecraft:explosion_emitter", ANCHORS_PARTICLES);
	private static final boolean ANCHORS_LOADED =
			FabricLoader.getInstance().isModLoaded(ANCHORS_PARTICLES.ownerModId());

	private PriorityCompatibility() {
	}

	/** Returns the owner of a particle type, but only while that owner is installed. */
	public static @Nullable Reservation particleReservation(String particleTypeId) {
		Reservation reservation = RESERVED_PARTICLES.get(particleTypeId);
		if (reservation == null || !ANCHORS_LOADED) {
			return null;
		}
		return reservation;
	}

	/** Whether at least one live reservation prevents a literal clear of the whole particle engine. */
	public static boolean hasReservedParticles() {
		return ANCHORS_LOADED;
	}
}
