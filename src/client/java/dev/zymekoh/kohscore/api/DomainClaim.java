package dev.zymekoh.kohscore.api;

import java.util.Objects;

/**
 * One mod's declaration that it is driving a {@link Domain}.
 *
 * @param domain      what the owner is taking over
 * @param ownerModId  the Fabric mod id doing the taking
 * @param ownerName   human readable name shown in Core's UI
 * @param reason      short sentence explaining why, shown on the greyed-out control
 * @param exclusive   {@code true} when no second module may claim the same domain. Core itself
 *                    stands down for every claim, shared or exclusive; a shared claim only means
 *                    that several external modules may cooperate on the domain.
 */
public record DomainClaim(Domain domain, String ownerModId, String ownerName, String reason, boolean exclusive) {

	public DomainClaim {
		Objects.requireNonNull(domain, "domain");
		Objects.requireNonNull(ownerModId, "ownerModId");
		Objects.requireNonNull(ownerName, "ownerName");
		Objects.requireNonNull(reason, "reason");
	}

	/** Convenience factory for a full takeover. */
	public static DomainClaim exclusive(Domain domain, String ownerModId, String ownerName, String reason) {
		return new DomainClaim(domain, ownerModId, ownerName, reason, true);
	}

	/** Convenience factory for a cooperative external claim. Core still stands down. */
	public static DomainClaim shared(Domain domain, String ownerModId, String ownerName, String reason) {
		return new DomainClaim(domain, ownerModId, ownerName, reason, false);
	}
}
