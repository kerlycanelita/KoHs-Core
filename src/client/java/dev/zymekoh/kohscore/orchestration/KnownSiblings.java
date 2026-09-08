package dev.zymekoh.kohscore.orchestration;

import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.DomainClaim;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Claims Core files on behalf of mods that have not adopted the {@code kohs:core} entrypoint yet.
 *
 * <p>Every KoHs mod predates Core, and none of them are going to be edited just to make Core happy.
 * So Core reads the room instead: if one of these mod ids is present, Core assumes the ownership
 * documented here and steps aside, exactly as if the mod had claimed the domain itself.
 *
 * <p>A real claim from {@link ModuleDiscovery} always wins over an entry here — see
 * {@link DomainRegistry#claim}. That is what makes this table safe to keep around after a sibling
 * does adopt the API.
 */
final class KnownSiblings {

	private KnownSiblings() {
	}

	private record Entry(String modId, String displayName, Domain domain, boolean exclusive, String reason) {
	}

	/**
	 * Ownership Core infers from a mod simply being installed.
	 *
	 * <p>These capabilities are deliberately narrower than broad words such as "input" or
	 * "effects". Inventory Tweaks observing a mouse callback must not disable Raw Input, and Death
	 * Effects replacing a dead player model must not disable the vignette control.
	 */
	private static final List<Entry> TABLE = List.of(
			// --- The KoHs family -------------------------------------------------------------
			new Entry("kohs_inventory_tweaks", "KoHs Inventory Tweaks", Domain.INVENTORY_UI, true,
					"kohs_core.yield.inventory_tweaks_cursor"),
			new Entry("kohs_anchors", "KoHs Anchors", Domain.ANCHOR_EFFECTS, true,
					"kohs_core.yield.anchors_particles"),
			new Entry("kohs_death_effects", "KoHs Death Effects", Domain.DEATH_EFFECTS, true,
					"kohs_core.yield.death_effects_screen"),
			new Entry("herzium", "Herzium", Domain.HOTBAR_EFFECTS, true,
					"kohs_core.yield.herzium_screen"),
			// Synapse owns the peripheral path end to end: it takes Windows Raw Input away
			// from GLFW, feeds the camera from its own report queue and routes remapped
			// buttons. Core's Input controls write the very options that path replaces, so
			// they must go inert rather than describe a pipeline that is no longer running.
			new Entry("kohs_synapse", "KoHs Synapse", Domain.INPUT_LATENCY, true,
					"kohs_core.yield.synapse_input"),

			// --- Third party optimisers ------------------------------------------------------
			new Entry("sodium", "Sodium", Domain.GRAPHICS_QUALITY, true, "kohs_core.yield.sodium_graphics"),
			new Entry("sodium", "Sodium", Domain.VIEW_DISTANCE, true, "kohs_core.yield.sodium_distance"),
			new Entry("sodium-extra", "Sodium Extra", Domain.PARTICLES, true, "kohs_core.yield.sodium_extra"),
			new Entry("sodium-extra", "Sodium Extra", Domain.CLOUDS, true, "kohs_core.yield.sodium_extra"),
			new Entry("sodium-extra", "Sodium Extra", Domain.WEATHER, true, "kohs_core.yield.sodium_extra"),
			new Entry("iris", "Iris Shaders", Domain.GRAPHICS_QUALITY, true, "kohs_core.yield.iris"),
			new Entry("nvidium", "Nvidium", Domain.VIEW_DISTANCE, true, "kohs_core.yield.nvidium"),
			new Entry("entityculling", "Entity Culling", Domain.ENTITY_CULLING, true,
					"kohs_core.yield.entityculling"),
			new Entry("moreculling", "More Culling", Domain.ENTITY_CULLING, true, "kohs_core.yield.moreculling"),
			new Entry("dynamic-fps", "Dynamic FPS", Domain.FRAME_PACING, true, "kohs_core.yield.dynamic_fps"),
			new Entry("dynamic-fps", "Dynamic FPS", Domain.BACKGROUND_CPU, true, "kohs_core.yield.dynamic_fps"),
			new Entry("particlecore", "Particle Core", Domain.PARTICLES, true, "kohs_core.yield.particle_core"),
			new Entry("badoptimizations", "BadOptimizations", Domain.LIGHTMAP, true,
					"kohs_core.yield.badoptimizations"),
			new Entry("polytone", "Polytone", Domain.LIGHTMAP, true, "kohs_core.yield.polytone"),
			new Entry("sodium-extra", "Sodium Extra", Domain.ANIMATED_TEXTURES, true,
					"kohs_core.yield.sodium_extra"),
			new Entry("ghostmode", "GhostMode", Domain.STREAMING, true, "kohs_core.yield.ghostmode"),
			new Entry("rawinputbuffer", "Raw Input Buffer", Domain.INPUT_LATENCY, true,
					"kohs_core.yield.raw_input"),
			new Entry("ixeris", "Ixeris", Domain.INPUT_LATENCY, true, "kohs_core.yield.ixeris"),
			new Entry("ferritecore", "FerriteCore", Domain.MEMORY, true, "kohs_core.yield.ferritecore"),
			new Entry("c2me", "C2ME", Domain.VIEW_DISTANCE, true, "kohs_core.yield.c2me"),
			new Entry("cull-leaves", "Cull Leaves", Domain.GRAPHICS_QUALITY, true, "kohs_core.yield.cull_leaves"));

	/** Every claim implied by the mods actually present in this launch. */
	static List<DomainClaim> detect() {
		FabricLoader loader = FabricLoader.getInstance();
		List<DomainClaim> claims = new ArrayList<>();
		for (Entry entry : TABLE) {
			if (loader.isModLoaded(entry.modId())) {
				claims.add(new DomainClaim(entry.domain(), entry.modId(), entry.displayName(), entry.reason(),
						entry.exclusive()));
			}
		}
		return claims;
	}
}
