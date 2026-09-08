package dev.zymekoh.kohscore.orchestration;

import dev.zymekoh.kohscore.KoHsCore;
import dev.zymekoh.kohscore.api.CoreBus;
import dev.zymekoh.kohscore.api.CoreSignals;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.DomainClaim;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Who is driving each {@link Domain} right now.
 *
 * <p>Two sources feed this: the inferred table in {@link KnownSiblings}, seeded at startup, and real
 * claims arriving through the {@code kohs:core} entrypoint. Real claims outrank inferred ones, so a
 * sibling that later adopts the API immediately replaces the guess Core was making about it.
 */
public final class DomainRegistry {

	private final Map<Domain, LinkedHashMap<String, DomainClaim>> claims = new EnumMap<>(Domain.class);
	private final Map<Domain, Set<String>> inferredOwners = new EnumMap<>(Domain.class);
	private final CoreBus bus;
	/** Lock-free ownership check for particle/entity hot paths; one bit per enum ordinal. */
	private volatile long contestedMask;

	public DomainRegistry(CoreBus bus) {
		this.bus = bus;
	}

	/** Seeds the registry from the mods present in this launch. Call once, before modules load. */
	public synchronized void seedFromInstalledMods() {
		for (DomainClaim claim : KnownSiblings.detect()) {
			this.claims.computeIfAbsent(claim.domain(), ignored -> new LinkedHashMap<>())
					.put(claim.ownerModId(), claim);
			this.inferredOwners.computeIfAbsent(claim.domain(), ignored -> new LinkedHashSet<>())
					.add(claim.ownerModId());
			this.contestedMask |= bit(claim.domain());
		}
		if (!this.claims.isEmpty()) {
			KoHsCore.LOGGER.info("Yielding {} domain(s) to already-installed mods: {}", this.claims.size(),
					this.claims.values().stream().flatMap(owners -> owners.values().stream())
							.map(DomainClaim::ownerName).distinct().toList());
		}
	}

	/**
	 * Records an explicit claim from a mod.
	 *
	 * @return {@code true} when granted
	 */
	public synchronized boolean claim(DomainClaim claim) {
		LinkedHashMap<String, DomainClaim> owners =
				this.claims.computeIfAbsent(claim.domain(), ignored -> new LinkedHashMap<>());
		DomainClaim sameOwner = owners.get(claim.ownerModId());
		Set<String> inferred = this.inferredOwners.computeIfAbsent(claim.domain(), ignored -> new LinkedHashSet<>());

		// A module may replace Core's inferred description of itself, but never another mod's
		// reservation. This keeps the four priority mods authoritative even before they adopt the API.
		if (sameOwner != null) {
			owners.put(claim.ownerModId(), claim);
			inferred.remove(claim.ownerModId());
			this.contestedMask |= bit(claim.domain());
			this.bus.publish(new CoreSignals.DomainOwnershipChanged(claim.domain(), claim));
			return true;
		}

		for (DomainClaim existing : owners.values()) {
			if (existing.exclusive() || claim.exclusive()) {
				KoHsCore.LOGGER.info("Denied {} claim on {}: {} already reserves it{}",
						claim.ownerModId(), claim.domain().id(), existing.ownerModId(),
						existing.exclusive() ? " exclusively" : " cooperatively");
				return false;
			}
		}

		owners.put(claim.ownerModId(), claim);
		inferred.remove(claim.ownerModId());
		this.contestedMask |= bit(claim.domain());
		this.bus.publish(new CoreSignals.DomainOwnershipChanged(claim.domain(), claim));
		KoHsCore.LOGGER.info("{} claimed {}{}", claim.ownerName(), claim.domain().id(),
				claim.exclusive() ? " (exclusive)" : " (shared)");
		return true;
	}

	/** Drops a claim, but only if the caller is the one holding it. */
	public synchronized void release(Domain domain, String ownerModId) {
		LinkedHashMap<String, DomainClaim> owners = this.claims.get(domain);
		if (owners != null && owners.remove(ownerModId) != null) {
			Set<String> inferred = this.inferredOwners.get(domain);
			if (inferred != null) {
				inferred.remove(ownerModId);
				if (inferred.isEmpty()) {
					this.inferredOwners.remove(domain);
				}
			}
			if (owners.isEmpty()) {
				this.claims.remove(domain);
				this.contestedMask &= ~bit(domain);
			}
			this.bus.publish(new CoreSignals.DomainOwnershipChanged(domain, null));
		}
	}

	/** The current owner of a domain, or {@code null} when Core still owns it. */
	public synchronized @Nullable DomainClaim owner(Domain domain) {
		List<DomainClaim> owners = this.owners(domain);
		for (DomainClaim owner : owners) {
			if (owner.exclusive()) {
				return owner;
			}
		}
		return owners.isEmpty() ? null : owners.getFirst();
	}

	/** Every current owner in deterministic registration order. */
	public synchronized List<DomainClaim> owners(Domain domain) {
		Map<String, DomainClaim> owners = this.claims.get(domain);
		return owners == null ? List.of() : List.copyOf(owners.values());
	}

	/** Whether Core is still allowed to write this domain's values. */
	public boolean isCoreAllowed(Domain domain) {
		return (this.contestedMask & bit(domain)) == 0L;
	}

	/** Whether someone other than Core has a stake in this domain, exclusive or not. */
	public boolean isContested(Domain domain) {
		return (this.contestedMask & bit(domain)) != 0L;
	}

	/** Immutable view for the settings screen. */
	public synchronized Map<Domain, List<DomainClaim>> snapshot() {
		Map<Domain, List<DomainClaim>> copy = new EnumMap<>(Domain.class);
		for (Map.Entry<Domain, LinkedHashMap<String, DomainClaim>> entry : this.claims.entrySet()) {
			copy.put(entry.getKey(), List.copyOf(entry.getValue().values()));
		}
		return Map.copyOf(copy);
	}

	private static long bit(Domain domain) {
		return 1L << domain.ordinal();
	}
}
