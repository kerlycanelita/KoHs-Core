package dev.zymekoh.kohscore.orchestration;

import dev.zymekoh.kohscore.KoHsCore;
import dev.zymekoh.kohscore.api.CoreBus;
import dev.zymekoh.kohscore.api.CoreContext;
import dev.zymekoh.kohscore.api.CoreSettingsPage;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.DomainClaim;
import dev.zymekoh.kohscore.api.ParticleAllowance;
import dev.zymekoh.kohscore.api.PressureLevel;

/** A module-facing context that can mutate ownership only under its own Fabric mod id. */
final class ScopedCoreContext implements CoreContext {

	private final CoreContextImpl delegate;
	private final String ownerModId;

	ScopedCoreContext(CoreContextImpl delegate, String ownerModId) {
		this.delegate = delegate;
		this.ownerModId = ownerModId;
	}

	@Override
	public CoreBus bus() {
		return this.delegate.bus();
	}

	@Override
	public ParticleAllowance particles() {
		return this.delegate.particles();
	}

	@Override
	public boolean claim(DomainClaim claim) {
		if (!this.ownerModId.equals(claim.ownerModId())) {
			KoHsCore.LOGGER.warn("Denied domain claim from {} impersonating {}", this.ownerModId,
					claim.ownerModId());
			return false;
		}
		return this.delegate.claim(claim);
	}

	@Override
	public void release(Domain domain, String ownerModId) {
		if (!this.ownerModId.equals(ownerModId)) {
			KoHsCore.LOGGER.warn("Denied domain release from {} for owner {}", this.ownerModId, ownerModId);
			return;
		}
		this.delegate.release(domain, ownerModId);
	}

	@Override
	public void addSettingsPage(CoreSettingsPage page) {
		this.delegate.addSettingsPage(page);
	}

	@Override
	public PressureLevel pressure() {
		return this.delegate.pressure();
	}
}
