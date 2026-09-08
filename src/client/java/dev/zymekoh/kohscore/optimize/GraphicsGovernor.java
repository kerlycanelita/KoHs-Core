package dev.zymekoh.kohscore.optimize;

import dev.zymekoh.kohscore.KoHsCore;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import dev.zymekoh.kohscore.orchestration.DomainRegistry;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.server.level.ParticleStatus;

/** Applies Core's quality tiers through public vanilla options, with per-domain ownership gates. */
public final class GraphicsGovernor {

	private final DomainRegistry domains;
	private Snapshot original;
	private boolean textureReloadPending;

	private record Snapshot(
			boolean ambientOcclusion,
			boolean entityShadows,
			int biomeBlendRadius,
			int mipmapLevels,
			int maxAnisotropyBit,
			TextureFilteringMethod filtering,
			boolean cutoutLeaves,
			boolean improvedTransparency,
			CloudStatus clouds,
			int cloudRange,
			int weatherRadius,
			double entityDistanceScaling,
			int renderDistance,
			int simulationDistance,
			PrioritizeChunkUpdates chunkUpdates,
			ParticleStatus particles) {
	}

	public GraphicsGovernor(DomainRegistry domains) {
		this.domains = domains;
	}

	/** Applies the selected tier once. Core never reasserts values every frame. */
	public void apply() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.options == null) {
			return;
		}
		Options options = minecraft.options;
		CoreConfig config = ConfigStore.get();
		if (config.extremeQualityTier <= 0) {
			this.restore(options);
			return;
		}
		if (this.original == null) {
			this.original = capture(options);
		}

		int tier = config.extremeQualityTier;
		if (this.domains.isCoreAllowed(Domain.GRAPHICS_QUALITY)) {
			// CUSTOM has no side effects. Setting FAST would itself rewrite clouds, particles, view
			// distance and other domains before Core had a chance to honour their owners. The preset
			// is itself graphics state, so leave it untouched when Sodium/Iris owns that domain.
			options.graphicsPreset().set(GraphicsPreset.CUSTOM);
			set(options.ambientOcclusion(), false);
			set(options.entityShadows(), false);
			set(options.biomeBlendRadius(), 0);
			set(options.cutoutLeaves(), false);
			set(options.improvedTransparency(), false);
			int mipmaps = tier >= 2 ? 0 : 1;
			TextureFilteringMethod filtering = TextureFilteringMethod.NONE;
			this.textureReloadPending |= set(options.mipmapLevels(), mipmaps);
			this.textureReloadPending |= set(options.maxAnisotropyBit(), 1);
			this.textureReloadPending |= set(options.textureFiltering(), filtering);
		}

		if (this.domains.isCoreAllowed(Domain.CLOUDS)) {
			set(options.cloudStatus(), CloudStatus.OFF);
			set(options.cloudRange(), tier >= 3 ? 32 : 48);
		}
		if (this.domains.isCoreAllowed(Domain.WEATHER)) {
			// 26.1.2 constrains this option to 3..10. Keep the aggressive tiers at the
			// real vanilla minimum instead of relying on OptionInstance to clamp it.
			set(options.weatherRadius(), 3);
		}
		if (this.domains.isCoreAllowed(Domain.ENTITY_CULLING)) {
			set(options.entityDistanceScaling(), tier >= 2 ? 0.5 : 0.75);
		}
		if (this.domains.isCoreAllowed(Domain.PARTICLES)) {
			set(options.particles(), tier >= 2 ? ParticleStatus.MINIMAL : ParticleStatus.DECREASED);
		}
		if (this.domains.isCoreAllowed(Domain.VIEW_DISTANCE)) {
			int renderDistance = switch (tier) {
				case 1 -> Math.min(options.renderDistance().get(), 6);
				case 2 -> Math.max(4, Math.min(8, config.sharpRadiusChunks + 2));
				default -> 2;
			};
			set(options.renderDistance(), renderDistance);
			set(options.simulationDistance(), tier >= 2 ? 5 : Math.min(options.simulationDistance().get(), 6));
			set(options.prioritizeChunkUpdates(), PrioritizeChunkUpdates.NONE);
		}

		KoHsCore.LOGGER.info("Applied quality tier {} without touching reserved domains", tier);
	}

	private void restore(Options options) {
		if (this.original == null) {
			return;
		}
		Snapshot snapshot = this.original;
		if (this.domains.isCoreAllowed(Domain.GRAPHICS_QUALITY)) {
			options.graphicsPreset().set(GraphicsPreset.CUSTOM);
			set(options.ambientOcclusion(), snapshot.ambientOcclusion());
			set(options.entityShadows(), snapshot.entityShadows());
			set(options.biomeBlendRadius(), snapshot.biomeBlendRadius());
			set(options.cutoutLeaves(), snapshot.cutoutLeaves());
			set(options.improvedTransparency(), snapshot.improvedTransparency());
			this.textureReloadPending |= set(options.mipmapLevels(), snapshot.mipmapLevels());
			this.textureReloadPending |= set(options.maxAnisotropyBit(), snapshot.maxAnisotropyBit());
			this.textureReloadPending |= set(options.textureFiltering(), snapshot.filtering());
		}
		if (this.domains.isCoreAllowed(Domain.CLOUDS)) {
			set(options.cloudStatus(), snapshot.clouds());
			set(options.cloudRange(), snapshot.cloudRange());
		}
		if (this.domains.isCoreAllowed(Domain.WEATHER)) {
			set(options.weatherRadius(), snapshot.weatherRadius());
		}
		if (this.domains.isCoreAllowed(Domain.ENTITY_CULLING)) {
			set(options.entityDistanceScaling(), snapshot.entityDistanceScaling());
		}
		if (this.domains.isCoreAllowed(Domain.PARTICLES)) {
			set(options.particles(), snapshot.particles());
		}
		if (this.domains.isCoreAllowed(Domain.VIEW_DISTANCE)) {
			set(options.renderDistance(), snapshot.renderDistance());
			set(options.simulationDistance(), snapshot.simulationDistance());
			set(options.prioritizeChunkUpdates(), snapshot.chunkUpdates());
		}
		this.original = null;
		KoHsCore.LOGGER.info("Restored the pre-preset values for every domain Core still owns");
	}

	/** Performs at most one texture-atlas refresh after the settings screen closes. */
	public void flushTextureChanges() {
		if (!this.textureReloadPending) {
			return;
		}
		this.textureReloadPending = false;
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.updateMaxMipLevel(minecraft.options.mipmapLevels().get());
		minecraft.delayTextureReload();
	}

	/** Marks a direct mipmap/filter control change for the same deferred refresh. */
	public void markTextureReloadRequired() {
		this.textureReloadPending = true;
	}

	private static <T> boolean set(net.minecraft.client.OptionInstance<T> option, T value) {
		if (java.util.Objects.equals(option.get(), value)) {
			return false;
		}
		option.set(value);
		return true;
	}

	private static Snapshot capture(Options options) {
		return new Snapshot(
				options.ambientOcclusion().get(),
				options.entityShadows().get(),
				options.biomeBlendRadius().get(),
				options.mipmapLevels().get(),
				options.maxAnisotropyBit().get(),
				options.textureFiltering().get(),
				options.cutoutLeaves().get(),
				options.improvedTransparency().get(),
				options.cloudStatus().get(),
				options.cloudRange().get(),
				options.weatherRadius().get(),
				options.entityDistanceScaling().get(),
				options.renderDistance().get(),
				options.simulationDistance().get(),
				options.prioritizeChunkUpdates().get(),
				options.particles().get());
	}

	public boolean hasSnapshot() {
		return this.original != null;
	}
}
