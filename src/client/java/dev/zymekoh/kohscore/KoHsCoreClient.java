package dev.zymekoh.kohscore;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohscore.api.CoreSettingsPage;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.DomainClaim;
import dev.zymekoh.kohscore.api.PressureLevel;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.gui.CoreScreen;
import dev.zymekoh.kohscore.hud.PerformanceHud;
import dev.zymekoh.kohscore.jvm.HardwareProbe;
import dev.zymekoh.kohscore.optimize.GraphicsGovernor;
import dev.zymekoh.kohscore.optimize.MemoryGovernor;
import dev.zymekoh.kohscore.optimize.ParticleGovernor;
import dev.zymekoh.kohscore.optimize.PressureMonitor;
import dev.zymekoh.kohscore.orchestration.CoreBusImpl;
import dev.zymekoh.kohscore.orchestration.CoreContextImpl;
import dev.zymekoh.kohscore.orchestration.DomainRegistry;
import dev.zymekoh.kohscore.orchestration.ModuleDiscovery;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Wires everything together and owns the singletons the mixins and the screen reach for.
 *
 * <p>Static access is deliberate. A mixin has no constructor to inject into, and the alternative —
 * threading a context object through every injection point — would mean more mixins, not fewer.
 * Every accessor here tolerates being called before setup finishes, because mixins can and do fire
 * first.
 */
public final class KoHsCoreClient implements ClientModInitializer {

	private static @Nullable CoreBusImpl bus;
	private static @Nullable DomainRegistry domains;
	private static @Nullable PressureMonitor pressureMonitor;
	private static @Nullable ParticleGovernor particleGovernor;
	private static @Nullable GraphicsGovernor graphicsGovernor;
	private static @Nullable MemoryGovernor memoryGovernor;
	private static @Nullable CoreContextImpl context;
	private static @Nullable KeyMapping openMenuKey;

	private static long lastBudgetPublishMillis;

	@Override
	public void onInitializeClient() {
		ConfigStore.load();

		CoreBusImpl newBus = new CoreBusImpl();
		DomainRegistry newDomains = new DomainRegistry(newBus);
		PressureMonitor newPressure = new PressureMonitor(newBus);
		ParticleGovernor newParticles = new ParticleGovernor(newDomains, newPressure, newBus);

		bus = newBus;
		domains = newDomains;
		pressureMonitor = newPressure;
		particleGovernor = newParticles;
		graphicsGovernor = new GraphicsGovernor(newDomains);
		memoryGovernor = new MemoryGovernor();

		// Read the room before any module speaks, so an explicit claim can override the guess.
		newDomains.seedFromInstalledMods();

		context = new CoreContextImpl(newBus, newDomains, newParticles, newPressure);
		ModuleDiscovery.loadAll(context);

		HardwareProbe.beginProbe();

		this.registerKeyBinding();
		this.registerTicking();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigStore.flush());

		KoHsCore.LOGGER.info("KoHs Core ready");
	}

	private void registerKeyBinding() {
		KeyMapping.Category category =
				KeyMapping.Category.register(KoHsCore.id("menu"));
		openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.kohs_core.open",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
	}

	private void registerTicking() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			KeyMapping key = openMenuKey;
			if (key != null && key.consumeClick()) {
				client.setScreen(new CoreScreen(client.screen));
				while (key.consumeClick()) {
					// Drain key repeats from this tick; one press opens one screen.
				}
			}

			ParticleGovernor particles = particleGovernor;
			MemoryGovernor memory = memoryGovernor;
			PressureMonitor pressure = pressureMonitor;
			if (particles == null || memory == null || pressure == null) {
				return;
			}

			long now = System.currentTimeMillis();
			if (now - lastBudgetPublishMillis >= 1000L) {
				lastBudgetPublishMillis = now;
				particles.publishIfChanged();
			}

			if (client.player != null) {
				memory.tick(client.player.getX(), client.player.getZ(), pressure.heapUsedRatio());
			}
		});

		// A HUD element is the cheapest per-frame hook available without touching the render loop.
		// It only fires in game; the settings screen counts its own frames while it is open.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, KoHsCore.id("frame_counter"),
				(graphics, deltaTracker) -> {
					noteFrame();
					PerformanceHud.render(graphics);
				});
	}

	// --- Accessors used by mixins and the screen ---------------------------------------------

	/** The particle governor, or null before setup has run. */
	public static @Nullable ParticleGovernor particleGovernorOrNull() {
		return particleGovernor;
	}

	/** Whether Core is still allowed to act on a domain. Defaults to true before setup. */
	public static boolean ownsDomain(Domain domain) {
		DomainRegistry registry = domains;
		return registry == null || registry.isCoreAllowed(domain);
	}

	/** The mod that has taken a domain, or null when Core still has it. */
	public static @Nullable DomainClaim domainOwner(Domain domain) {
		DomainRegistry registry = domains;
		return registry == null ? null : registry.owner(domain);
	}

	/** Every external owner of a domain, in deterministic registration order. */
	public static List<DomainClaim> domainOwners(Domain domain) {
		DomainRegistry registry = domains;
		return registry == null ? List.of() : registry.owners(domain);
	}

	public static GraphicsGovernor graphicsGovernor() {
		GraphicsGovernor governor = graphicsGovernor;
		if (governor == null) {
			throw new IllegalStateException("Graphics governor requested before the client initialised");
		}
		return governor;
	}

	public static @Nullable MemoryGovernor memoryGovernorOrNull() {
		return memoryGovernor;
	}

	/** Settings pages contributed by sibling mods. */
	public static List<CoreSettingsPage> settingsPages() {
		CoreContextImpl current = context;
		return current == null ? List.of() : current.pages();
	}

	/** Called once per rendered frame. */
	public static void noteFrame() {
		PressureMonitor monitor = pressureMonitor;
		if (monitor != null) {
			monitor.onFrame();
		}
	}

	/** Compatibility signal retained for modules; memory management never forces a collection. */
	public static void noteInput() {
		MemoryGovernor memory = memoryGovernor;
		if (memory != null) {
			memory.noteInput();
		}
	}

	public static int framesPerSecond() {
		PressureMonitor monitor = pressureMonitor;
		return monitor == null ? 0 : monitor.framesPerSecond();
	}

	/** Average frame time over the last sample second, in milliseconds. */
	public static float frameTimeMillis() {
		PressureMonitor monitor = pressureMonitor;
		return monitor == null ? 0.0F : monitor.frameTimeMillis();
	}

	/** The single worst frame of the last sample second, in milliseconds. */
	public static float worstFrameTimeMillis() {
		PressureMonitor monitor = pressureMonitor;
		return monitor == null ? 0.0F : monitor.worstFrameTimeMillis();
	}

	public static PressureLevel pressure() {
		PressureMonitor monitor = pressureMonitor;
		return monitor == null ? PressureLevel.RELAXED : monitor.pressure();
	}

	/** Opens vanilla's own video screen, for anything Core deliberately does not surface. */
	public static void openVanillaVideoSettings(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreen(new VideoSettingsScreen(parent, minecraft, minecraft.options));
	}
}
