package dev.zymekoh.kohscore.gui;

import dev.zymekoh.kohscore.api.Domain;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Core's own tabs, in rail order.
 *
 * <p>Each one names the {@link Domain} it writes to, which is what lets the rail grey a whole tab
 * out when another mod has taken that domain over.
 */
public enum CoreTab {

	PERFORMANCE(null),
	PROFILES(null),
	GRAPHICS(Domain.GRAPHICS_QUALITY),
	PARTICLES(Domain.PARTICLES),
	ANIMATIONS(Domain.ANIMATED_TEXTURES),
	ENTITIES(Domain.ENTITY_CULLING),
	INPUT(Domain.INPUT_LATENCY),
	STREAMING(Domain.STREAMING),
	MEMORY(Domain.MEMORY),
	JVM(null),
	INTEGRATION(null),
	SUITE(null);

	private final @Nullable Domain domain;
	private final String id = name().toLowerCase(Locale.ROOT);

	CoreTab(@Nullable Domain domain) {
		this.domain = domain;
	}

	public String id() {
		return this.id;
	}

	public net.minecraft.resources.Identifier icon() {
		// Reuse the existing tuning icon; no duplicate bitmap or missing texture on older packs.
		String iconId = this == PROFILES ? "integration" : this.id;
		return net.minecraft.resources.Identifier.fromNamespaceAndPath("kohs_core", "textures/gui/tabs/" + iconId + ".png");
	}

	/** The domain this tab writes to, or null for tabs that only read. */
	public @Nullable Domain domain() {
		return this.domain;
	}

	public Component title() {
		return Component.translatable("kohs_core.tab." + this.id);
	}

	public Component description() {
		return Component.translatable("kohs_core.tab." + this.id + ".description");
	}
}
