package dev.zymekoh.kohscore.orchestration;

import dev.zymekoh.kohscore.KoHsCore;
import dev.zymekoh.kohscore.api.CoreModule;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * Finds sibling mods that speak Core's protocol.
 *
 * <p>Discovery is by Fabric entrypoint rather than by classpath scanning or reflection, which gives
 * the dependency direction Core wants: a mod declaring a {@code kohs:core} entrypoint still loads
 * perfectly when Core is absent, because Fabric only ever instantiates entrypoints for keys that
 * something asks for. Nothing here is a hard dependency in either direction.
 */
public final class ModuleDiscovery {

	private ModuleDiscovery() {
	}

	/** Instantiates every registered module and hands each one the context. */
	public static void loadAll(CoreContextImpl context) {
		List<EntrypointContainer<CoreModule>> containers =
				FabricLoader.getInstance().getEntrypointContainers(KoHsCore.MODULE_ENTRYPOINT, CoreModule.class);

		if (containers.isEmpty()) {
			KoHsCore.LOGGER.info("No sibling modules registered under '{}'", KoHsCore.MODULE_ENTRYPOINT);
			return;
		}

		for (EntrypointContainer<CoreModule> container : containers) {
			String modId = container.getProvider().getMetadata().getId();
			try {
				// Each provider gets a scoped handle. A broken integration cannot impersonate a
				// priority mod or release another module's reservation by supplying its mod id.
				container.getEntrypoint().onCoreReady(new ScopedCoreContext(context, modId));
				KoHsCore.LOGGER.info("Connected module from {}", modId);
			} catch (Throwable failure) {
				// One module throwing must not stop the others, and must not take the client with
				// it. Catching Throwable here is deliberate: a module built against an older Core
				// would fail with a linkage error rather than an exception.
				KoHsCore.LOGGER.error("Module from {} failed during setup and was skipped", modId, failure);
			}
		}
	}
}
