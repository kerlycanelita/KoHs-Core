package dev.zymekoh.kohscore.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Decides which of Core's mixins actually get applied.
 *
 * <p>Core yields at the config screen by greying out controls, but a greyed-out control still means
 * the bytecode is loaded. This takes it one step further: when a dedicated mod already owns a job,
 * Core's mixin for that job is never applied at all, so there is nothing to conflict with and
 * nothing to debug when something goes wrong.
 *
 * <p>Deciding this here rather than at runtime matters because mixin application happens long before
 * the client entrypoint runs, and it is the only point at which Core can genuinely leave no trace.
 */
public final class CoreMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger("KoHs Core");

	/** Mods that fully own entity visibility. Their culling is better tested than Core's. */
	private static final List<String> ENTITY_CULLING_OWNERS = List.of("entityculling", "moreculling");

	/** Mods that already gate particle spawning per type. */
	private static final List<String> PARTICLE_OWNERS = List.of("sodium-extra", "particlecore");

	@Override
	public void onLoad(String mixinPackage) {
		// Nothing to prepare.
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);

		if ("EntityRenderDispatcherMixin".equals(simpleName)) {
			String owner = firstLoaded(ENTITY_CULLING_OWNERS);
			if (owner != null) {
				LOGGER.info("Skipping entity culling mixin: {} already handles it", owner);
				return false;
			}
		}

		if ("ParticleEngineMixin".equals(simpleName)) {
			String owner = firstLoaded(PARTICLE_OWNERS);
			if (owner != null) {
				LOGGER.info("Skipping particle mixin: {} already handles it", owner);
				return false;
			}
		}

		return true;
	}

	private static String firstLoaded(List<String> modIds) {
		FabricLoader loader = FabricLoader.getInstance();
		for (String modId : modIds) {
			if (loader.isModLoaded(modId)) {
				return modId;
			}
		}
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
		// Nothing to do.
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		// Nothing to do.
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		// Nothing to do.
	}
}
