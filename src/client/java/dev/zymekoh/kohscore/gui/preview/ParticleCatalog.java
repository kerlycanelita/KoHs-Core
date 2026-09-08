package dev.zymekoh.kohscore.gui.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Every particle type the game knows about, in a form the settings screen can list and search.
 *
 * <p>Built from {@code BuiltInRegistries.PARTICLE_TYPE} rather than a hardcoded list, so particles
 * added by other mods appear alongside the vanilla ones and get a preview too.
 */
public final class ParticleCatalog {

	/**
	 * One row in the particle tab.
	 *
	 * @param id          registry id, e.g. {@code minecraft:soul_fire_flame}
	 * @param displayName human readable name derived from the path
	 * @param namespace   owning mod id, used for the group headers
	 * @param archetype   how to draw the preview
	 * @param searchBlob  lowercase haystack the search box matches against
	 */
	public record Entry(String id, Component displayName, String namespace, ParticleArchetype archetype,
			String searchBlob) {
	}

	/**
	 * Keyword to recipe, checked in order, first match wins.
	 *
	 * <p>Order matters: {@code soul_fire_flame} has to be tested before {@code flame}, and
	 * {@code falling_lava} before {@code lava}, or the broader entry would swallow the specific one.
	 */
	private static final List<Map.Entry<String, ParticleArchetype>> KEYWORDS = List.of(
			Map.entry("soul_fire_flame", ParticleArchetype.SOUL_FLAME),
			Map.entry("soul", ParticleArchetype.SOUL_FLAME),
			Map.entry("campfire", ParticleArchetype.SMOKE),
			Map.entry("flame", ParticleArchetype.FLAME),
			Map.entry("fire", ParticleArchetype.FLAME),
			Map.entry("lava", ParticleArchetype.LAVA),
			Map.entry("smoke", ParticleArchetype.SMOKE),
			Map.entry("cloud", ParticleArchetype.CLOUD),
			Map.entry("gust", ParticleArchetype.CLOUD),
			Map.entry("explosion", ParticleArchetype.EXPLOSION),
			Map.entry("firework", ParticleArchetype.SPARK),
			Map.entry("flash", ParticleArchetype.SPARK),
			Map.entry("crit", ParticleArchetype.SPARK),
			Map.entry("enchanted_hit", ParticleArchetype.SPARK),
			Map.entry("electric_spark", ParticleArchetype.SPARK),
			Map.entry("enchant", ParticleArchetype.ENCHANT),
			Map.entry("portal", ParticleArchetype.PORTAL),
			Map.entry("reverse", ParticleArchetype.PORTAL),
			Map.entry("witch", ParticleArchetype.MAGIC),
			Map.entry("effect", ParticleArchetype.MAGIC),
			Map.entry("instant", ParticleArchetype.MAGIC),
			Map.entry("glow", ParticleArchetype.MAGIC),
			Map.entry("end_rod", ParticleArchetype.MAGIC),
			Map.entry("totem", ParticleArchetype.MAGIC),
			Map.entry("dragon_breath", ParticleArchetype.MAGIC),
			Map.entry("trial", ParticleArchetype.MAGIC),
			Map.entry("ominous", ParticleArchetype.MAGIC),
			Map.entry("vault", ParticleArchetype.MAGIC),
			Map.entry("infested", ParticleArchetype.MAGIC),
			Map.entry("raid_omen", ParticleArchetype.MAGIC),
			Map.entry("sculk", ParticleArchetype.SCULK),
			Map.entry("shriek", ParticleArchetype.SCULK),
			Map.entry("sonic_boom", ParticleArchetype.SCULK),
			Map.entry("warped", ParticleArchetype.SCULK),
			Map.entry("dripping", ParticleArchetype.DRIP),
			Map.entry("falling", ParticleArchetype.DRIP),
			Map.entry("landing", ParticleArchetype.DRIP),
			Map.entry("splash", ParticleArchetype.WATER),
			Map.entry("bubble", ParticleArchetype.BUBBLE),
			Map.entry("underwater", ParticleArchetype.BUBBLE),
			Map.entry("fishing", ParticleArchetype.WATER),
			Map.entry("rain", ParticleArchetype.RAIN),
			Map.entry("snowflake", ParticleArchetype.SNOW),
			Map.entry("snow", ParticleArchetype.SNOW),
			Map.entry("white_ash", ParticleArchetype.ASH),
			Map.entry("ash", ParticleArchetype.ASH),
			Map.entry("spore", ParticleArchetype.ASH),
			Map.entry("pollen", ParticleArchetype.ASH),
			Map.entry("heart", ParticleArchetype.HEART),
			Map.entry("note", ParticleArchetype.NOTE),
			Map.entry("block", ParticleArchetype.BLOCK),
			Map.entry("item", ParticleArchetype.BLOCK),
			Map.entry("dust", ParticleArchetype.DUST),
			Map.entry("mycelium", ParticleArchetype.DUST));

	private static List<Entry> cached;

	private ParticleCatalog() {
	}

	/** All particle types, sorted by namespace then name. Built once and reused. */
	public static List<Entry> all() {
		if (cached == null) {
			cached = build();
		}
		return cached;
	}

	/** Drops the cache so a resource reload picks up newly registered types. */
	public static void invalidate() {
		cached = null;
	}

	private static List<Entry> build() {
		List<Entry> entries = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.PARTICLE_TYPE.keySet()) {
			String path = id.getPath();
			String pretty = prettify(path);
			entries.add(new Entry(
					id.toString(),
					Component.literal(pretty),
					id.getNamespace(),
					archetypeFor(path),
					(id + " " + pretty).toLowerCase(Locale.ROOT)));
		}
		entries.sort((left, right) -> {
			// Vanilla first, then other mods alphabetically, so the list opens on familiar ground.
			boolean leftVanilla = "minecraft".equals(left.namespace());
			boolean rightVanilla = "minecraft".equals(right.namespace());
			if (leftVanilla != rightVanilla) {
				return leftVanilla ? -1 : 1;
			}
			int byNamespace = left.namespace().compareTo(right.namespace());
			return byNamespace != 0 ? byNamespace : left.id().compareTo(right.id());
		});
		return List.copyOf(entries);
	}

	private static ParticleArchetype archetypeFor(String path) {
		for (Map.Entry<String, ParticleArchetype> keyword : KEYWORDS) {
			if (path.contains(keyword.getKey())) {
				return keyword.getValue();
			}
		}
		return ParticleArchetype.GENERIC;
	}

	/** {@code soul_fire_flame} becomes {@code Soul Fire Flame}. */
	private static String prettify(String path) {
		StringBuilder pretty = new StringBuilder(path.length());
		boolean capitaliseNext = true;
		for (int index = 0; index < path.length(); index++) {
			char character = path.charAt(index);
			if (character == '_' || character == '/' || character == '.') {
				pretty.append(' ');
				capitaliseNext = true;
			} else if (capitaliseNext) {
				pretty.append(Character.toUpperCase(character));
				capitaliseNext = false;
			} else {
				pretty.append(character);
			}
		}
		return pretty.toString();
	}
}
