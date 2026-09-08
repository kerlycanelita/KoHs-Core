package dev.zymekoh.kohscore.optimize;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import net.minecraft.resources.Identifier;

/** Read-only decisions for cosmetic render hooks. No entity or world tick is ever cancelled. */
public final class AnimationGovernor {
	private AnimationGovernor() {}

	public static boolean enabled(String category, Domain domain) {
		if (!KoHsCoreClient.ownsDomain(domain)) return true;
		var config = ConfigStore.get();
		return config.animationsEnabled && !Boolean.FALSE.equals(config.animationCategories.get(category));
	}

	public static String textureCategory(Identifier id) {
		// Resources belonging to other mods remain under their author's control.
		if (!"minecraft".equals(id.getNamespace())) return "reserved";
		String path = id.getPath();
		if (path.contains("fire")) return "fire";
		if (path.contains("water")) return "water";
		if (path.contains("lava")) return "lava";
		if (path.contains("portal")) return "portal";
		if (path.contains("leaves") || path.contains("grass") || path.contains("kelp")
				|| path.contains("vine") || path.contains("sapling") || path.contains("flower")) return "vegetation";
		if (path.contains("redstone") || path.contains("repeater") || path.contains("comparator")) return "redstone";
		return "other_textures";
	}

	public static boolean textureEnabled(String category) {
		return category.equals("reserved") || !KoHsCoreClient.ownsDomain(Domain.ANIMATED_TEXTURES)
				|| (enabled("textures", Domain.ANIMATED_TEXTURES) && enabled(category, Domain.ANIMATED_TEXTURES));
	}
}
