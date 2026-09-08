package dev.zymekoh.kohscore.optimize;

import dev.zymekoh.kohscore.api.CoreBus;
import dev.zymekoh.kohscore.api.CoreSignals;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.ParticleAllowance;
import dev.zymekoh.kohscore.api.PressureLevel;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import dev.zymekoh.kohscore.orchestration.DomainRegistry;
import dev.zymekoh.kohscore.orchestration.PriorityCompatibility;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The single place that decides whether a particle gets to exist.
 *
 * <p>Three gates, cheapest first, because this runs on every spawn attempt in the game: the global
 * switch, the per-type override, then distance. The shared rate limiter comes last since it mutates
 * state and should not be charged for a particle that was going to be rejected anyway.
 *
 * <p>Sibling mods reach the same limiter through {@link ParticleAllowance}, so three KoHs mods
 * firing at once degrade together instead of each one independently concluding it is still inside
 * its own private budget.
 */
public final class ParticleGovernor implements ParticleAllowance {

	private static final long PRECLAIM_LIFETIME_NANOS = 50_000_000L;
	/** At most roughly 100 ms of the configured rate may arrive in one render burst. */
	private static final int MAX_BURST_TOKENS = 512;

	private static final class Preclaim {
		long expiresAt;
		int remaining;
	}

	private final DomainRegistry domains;
	private final PressureMonitor pressureMonitor;
	private final CoreBus bus;
	private final ThreadLocal<Preclaim> preclaims = ThreadLocal.withInitial(Preclaim::new);

	private long tokenTimestampNanos = System.nanoTime();
	private double availableTokens;
	private int tokenRate = -1;

	private int lastPublishedAllowance = -1;
	private float lastPublishedScale = -1.0F;

	public ParticleGovernor(DomainRegistry domains, PressureMonitor pressureMonitor, CoreBus bus) {
		this.domains = domains;
		this.pressureMonitor = pressureMonitor;
		this.bus = bus;
	}

	/**
	 * Whether a spawn attempt should proceed.
	 *
	 * @param particleTypeId registry id of the type being spawned
	 * @param x              world position, used for distance culling
	 */
	public boolean shouldSpawn(String particleTypeId, double x, double y, double z) {
		// The four sibling mods are authoritative. Anchors owns these exact effects and its own
		// budget, so not even Core's global switch may consume or cancel them.
		if (PriorityCompatibility.particleReservation(particleTypeId) != null) {
			return true;
		}
		// Someone else owns particles outright, so Core does not get a vote.
		if (!this.domains.isCoreAllowed(Domain.PARTICLES)) {
			return true;
		}

		CoreConfig config = ConfigStore.get();
		if (config.particlesDisabledGlobally) {
			return false;
		}
		if (Boolean.FALSE.equals(config.particleOverrides.get(particleTypeId))) {
			return false;
		}
		if (config.particleCullDistance > 0 && isBeyond(config.particleCullDistance, x, y, z)) {
			return false;
		}
		int allowance = this.allowancePerSecond(config);
		// Unlimited is the default. Keep that hot path free of ThreadLocal access, a second clock
		// read and monitor entry; busy effects can attempt thousands of spawns in one frame.
		if (allowance <= 0) {
			return true;
		}
		return this.consumePreclaim() || this.claimBudget(1, allowance) > 0;
	}

	private static boolean isBeyond(int distance, double x, double y, double z) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gameRenderer == null) {
			return false;
		}
		Camera camera = minecraft.gameRenderer.getMainCamera();
		if (camera == null) {
			return false;
		}
		Vec3 eye = camera.position();
		double dx = x - eye.x;
		double dy = y - eye.y;
		double dz = z - eye.z;
		// Squared comparison keeps a square root out of the hottest path in the particle system.
		return dx * dx + dy * dy + dz * dz > (double) distance * distance;
	}

	@Override
	public int claim(int requested) {
		if (requested <= 0) {
			return 0;
		}
		int allowance = this.allowancePerSecond();
		if (allowance <= 0) {
			return requested;
		}
		int granted = this.claimBudget(requested, allowance);
		if (granted > 0) {
			// Integrated mods normally claim a burst and immediately call addParticle. Remember that
			// grant briefly so ParticleEngine's universal mixin does not charge the same spawn twice.
			Preclaim preclaim = this.preclaims.get();
			long now = System.nanoTime();
			if (now > preclaim.expiresAt) {
				preclaim.remaining = 0;
			}
			preclaim.remaining = Math.min(20_000, preclaim.remaining + granted);
			preclaim.expiresAt = now + PRECLAIM_LIFETIME_NANOS;
		}
		return granted;
	}

	private boolean consumePreclaim() {
		Preclaim preclaim = this.preclaims.get();
		long now = System.nanoTime();
		if (now > preclaim.expiresAt || preclaim.remaining <= 0) {
			preclaim.remaining = 0;
			return false;
		}
		preclaim.remaining--;
		return true;
	}

	private synchronized int claimBudget(int requested, int allowance) {
		long now = System.nanoTime();
		int capacity = burstCapacity(allowance);
		if (this.tokenRate != allowance) {
			this.tokenRate = allowance;
			this.availableTokens = capacity;
			this.tokenTimestampNanos = now;
		} else {
			long elapsed = Math.max(0L, now - this.tokenTimestampNanos);
			this.tokenTimestampNanos = now;
			this.availableTokens = Math.min(capacity,
					this.availableTokens + elapsed * (allowance / 1_000_000_000.0));
		}

		int granted = Math.max(0, Math.min(requested, (int) Math.floor(this.availableTokens)));
		this.availableTokens -= granted;
		return granted;
	}

	private static int burstCapacity(int allowance) {
		return Math.max(1, Math.min(MAX_BURST_TOKENS, (allowance + 9) / 10));
	}

	@Override
	public boolean isTypeEnabled(String particleTypeId) {
		if (PriorityCompatibility.particleReservation(particleTypeId) != null
				|| !this.domains.isCoreAllowed(Domain.PARTICLES)) {
			return true;
		}
		CoreConfig config = ConfigStore.get();
		if (config.particlesDisabledGlobally) {
			return false;
		}
		return !Boolean.FALSE.equals(config.particleOverrides.get(particleTypeId));
	}

	@Override
	public int allowancePerSecond() {
		if (!this.domains.isCoreAllowed(Domain.PARTICLES)) {
			return 0;
		}
		return this.allowancePerSecond(ConfigStore.get());
	}

	private int allowancePerSecond(CoreConfig config) {
		int configured = config.particleAllowancePerSecond;
		if (!config.particleAdaptiveBudget || !config.memoryManagementEnabled) {
			return configured;
		}
		if (configured <= 0) {
			// Unlimited remains unlimited while the client is healthy. Under sustained pressure an
			// emergency ceiling prevents new transient objects from accelerating the next GC cycle.
			return switch (this.pressureMonitor.pressure()) {
				case RELAXED -> 0;
				case ELEVATED -> 6_000;
				case HIGH -> 2_500;
				case CRITICAL -> 800;
			};
		}
		// Under load the budget shrinks on its own, so a heavy fight thins the particles out
		// instead of turning into a slideshow.
		return Math.max(1, Math.round(configured * this.pressureMonitor.pressure().budgetScale()));
	}

	@Override
	public float burstScale() {
		if (!this.domains.isCoreAllowed(Domain.PARTICLES)) {
			return 1.0F;
		}
		CoreConfig config = ConfigStore.get();
		float scale = config.particleBurstScale;
		if (config.particleAdaptiveBudget && config.memoryManagementEnabled) {
			scale *= this.pressureMonitor.pressure().budgetScale();
		}
		return Math.max(0.0F, Math.min(1.0F, scale));
	}

	/**
	 * Publishes the budget when it changes, so mods sizing their own pools do not have to poll.
	 * Called once a second from the client tick.
	 */
	public void publishIfChanged() {
		int allowance = this.allowancePerSecond();
		float scale = this.burstScale();
		if (allowance != this.lastPublishedAllowance || Math.abs(scale - this.lastPublishedScale) > 0.001F) {
			this.lastPublishedAllowance = allowance;
			this.lastPublishedScale = scale;
			this.bus.publish(new CoreSignals.ParticleBudget(allowance, scale));
		}
	}

	/** Current pressure, exposed for the diagnostics tab. */
	public PressureLevel pressure() {
		return this.pressureMonitor.pressure();
	}
}
