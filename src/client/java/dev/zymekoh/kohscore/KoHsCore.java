package dev.zymekoh.kohscore;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants. Not an entrypoint; see {@link KoHsCoreClient}. */
public final class KoHsCore {

	public static final String MOD_ID = "kohs_core";
	public static final String MOD_NAME = "KoHs Core";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	/** The entrypoint key sibling mods register a {@code CoreModule} under. */
	public static final String MODULE_ENTRYPOINT = "kohs:core";

	private KoHsCore() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
