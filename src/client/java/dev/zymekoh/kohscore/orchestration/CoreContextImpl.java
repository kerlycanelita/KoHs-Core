package dev.zymekoh.kohscore.orchestration;

import dev.zymekoh.kohscore.KoHsCore;
import dev.zymekoh.kohscore.api.CoreBus;
import dev.zymekoh.kohscore.api.CoreContext;
import dev.zymekoh.kohscore.api.CoreSettingsPage;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.DomainClaim;
import dev.zymekoh.kohscore.api.ParticleAllowance;
import dev.zymekoh.kohscore.api.PressureLevel;
import dev.zymekoh.kohscore.optimize.PressureMonitor;
import java.util.ArrayList;
import java.util.List;

/** The handle Core gives each sibling module. */
public final class CoreContextImpl implements CoreContext {

	private final CoreBus bus;
	private final DomainRegistry domains;
	private final ParticleAllowance particles;
	private final PressureMonitor pressureMonitor;
	private final List<CoreSettingsPage> pages = new ArrayList<>();

	public CoreContextImpl(CoreBus bus, DomainRegistry domains, ParticleAllowance particles,
			PressureMonitor pressureMonitor) {
		this.bus = bus;
		this.domains = domains;
		this.particles = particles;
		this.pressureMonitor = pressureMonitor;
	}

	@Override
	public CoreBus bus() {
		return this.bus;
	}

	@Override
	public ParticleAllowance particles() {
		return this.particles;
	}

	@Override
	public boolean claim(DomainClaim claim) {
		return this.domains.claim(claim);
	}

	@Override
	public void release(Domain domain, String ownerModId) {
		this.domains.release(domain, ownerModId);
	}

	@Override
	public void addSettingsPage(CoreSettingsPage page) {
		for (CoreSettingsPage existing : this.pages) {
			if (existing.id().equals(page.id())) {
				KoHsCore.LOGGER.warn("Ignoring a duplicate settings page id: {}", page.id());
				return;
			}
		}
		this.pages.add(page);
		KoHsCore.LOGGER.info("Added settings page '{}'", page.id());
	}

	@Override
	public PressureLevel pressure() {
		return this.pressureMonitor.pressure();
	}

	/** Pages contributed by sibling mods, in registration order. */
	public List<CoreSettingsPage> pages() {
		return List.copyOf(this.pages);
	}
}
