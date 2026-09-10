package dev.zymekoh.kohscore.profile;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.server.level.ParticleStatus;

/** Client-thread-only, explicit application. No tick hooks, auto-reapplication or input injection. */
public final class ProfileManager {
	private static ProfileChanges previous;
	private ProfileManager() { }

	public static ProfileChanges.Result apply(GameProfile profile) {
		var options = Minecraft.getInstance().options;
		var config = ConfigStore.get();
		boolean pretty = profile == GameProfile.QUALITY;
		boolean fps = profile == GameProfile.FPS;
		ProfileChanges changes = new ProfileChanges();
		// CUSTOM has no bulk preset side effects. Every actual setting has its own owner guard.
		option(changes, Domain.GRAPHICS_QUALITY, options.graphicsPreset(), GraphicsPreset.CUSTOM);
		option(changes, Domain.GRAPHICS_QUALITY, options.ambientOcclusion(), pretty);
		option(changes, Domain.GRAPHICS_QUALITY, options.entityShadows(), pretty);
		option(changes, Domain.VIEW_DISTANCE, options.biomeBlendRadius(), pretty ? 2 : 0);
		option(changes, Domain.GRAPHICS_QUALITY, options.cutoutLeaves(), pretty);
		option(changes, Domain.GRAPHICS_QUALITY, options.improvedTransparency(), pretty);
		texture(changes, options.mipmapLevels(), pretty ? 4 : fps ? 0 : 1);
		texture(changes, options.maxAnisotropyBit(), pretty ? 2 : 1);
		texture(changes, options.textureFiltering(), pretty ? TextureFilteringMethod.ANISOTROPIC : TextureFilteringMethod.NONE);
		option(changes, Domain.CLOUDS, options.cloudStatus(), pretty ? CloudStatus.FANCY : CloudStatus.OFF);
		option(changes, Domain.CLOUDS, options.cloudRange(), pretty ? 96 : 32);
		option(changes, Domain.WEATHER, options.weatherRadius(), pretty ? 10 : 3);
		option(changes, Domain.VIEW_DISTANCE, options.renderDistance(), pretty ? 12 : fps ? 2 : 4);
		option(changes, Domain.VIEW_DISTANCE, options.prioritizeChunkUpdates(), PrioritizeChunkUpdates.NONE);
		// Do not edit simulation distance, player visibility/culling, FOV or mouse sensitivity.
		option(changes, Domain.PARTICLES, options.particles(), pretty ? ParticleStatus.ALL : fps ? ParticleStatus.MINIMAL : ParticleStatus.DECREASED);
		// Preserve the user's FPS cap in every profile, and never enable VSync automatically.
		if (!pretty) option(changes, Domain.FRAME_PACING, options.enableVsync(), false);
		if (profile == GameProfile.LATENCY) {
			option(changes, Domain.INPUT_LATENCY, options.rawMouseInput(), true);
		}
		changes.add(() -> KoHsCoreClient.ownsDomain(Domain.ANIMATED_TEXTURES),
				() -> config.animatedTextureInterval, value -> config.animatedTextureInterval = value,
				pretty ? 1 : fps ? 4 : 2);
		// The custom profile replaces the old tier label, but never clears per-effect user overrides.
		changes.add(() -> KoHsCoreClient.ownsDomain(Domain.GRAPHICS_QUALITY),
				() -> config.extremeQualityTier, value -> config.extremeQualityTier = value, 0);
		ProfileChanges.Result result = changes.apply();
		if (result.changed() > 0) {
			previous = changes;
			KoHsCoreClient.graphicsGovernor().discardPresetSnapshot();
			save();
		}
		return result;
	}

	public static boolean canUndo() { return previous != null && previous.canUndo(); }
	public static ProfileChanges.Result undo() {
		if (!canUndo()) return new ProfileChanges.Result(0, 0);
		ProfileChanges.Result result = previous.undo();
		previous = null;
		save();
		return result;
	}
	private static void save() {
		ConfigStore.save();
		Minecraft.getInstance().options.save();
	}
	private static <T> void option(ProfileChanges changes, Domain domain, OptionInstance<T> option, T target) {
		changes.add(() -> KoHsCoreClient.ownsDomain(domain), option::get, option::set, target);
	}
	private static <T> void texture(ProfileChanges changes, OptionInstance<T> option, T target) {
		changes.add(() -> KoHsCoreClient.ownsDomain(Domain.GRAPHICS_QUALITY), option::get, value -> {
			option.set(value);
			KoHsCoreClient.graphicsGovernor().markTextureReloadRequired();
		}, target);
	}
}
