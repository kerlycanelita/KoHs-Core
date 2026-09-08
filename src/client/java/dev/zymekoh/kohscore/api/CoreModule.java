package dev.zymekoh.kohscore.api;

/**
 * The interface a sibling mod implements to plug into Core.
 *
 * <p>Register it in {@code fabric.mod.json} under the {@code kohs:core} entrypoint:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "kohs:core": [ "dev.zymekoh.mymod.MyCoreModule" ]
 * }
 * }</pre>
 *
 * <p>Core discovers modules by entrypoint, so nothing here is a hard dependency: a mod that ships
 * this class still loads perfectly with Core absent, because Fabric only instantiates entrypoints
 * for entrypoint keys that something actually asks for.
 */
public interface CoreModule {

	/**
	 * Called once, on the client thread, after Core has finished its own setup.
	 *
	 * <p>Claim domains and add settings pages from here.
	 */
	void onCoreReady(CoreContext context);
}
