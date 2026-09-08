package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.optimize.ParticleGovernor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The gate every particle spawn passes through.
 *
 * <p>{@code createParticle} returning null is already a normal outcome in vanilla — it is what
 * happens when no provider is registered for a type — so cancelling here needs no special handling
 * anywhere upstream.
 *
 * <p>This runs on every spawn attempt in the game, which on a busy frame is thousands of calls.
 * Hence the id cache: resolving a {@link ParticleType} through the registry each time would put a
 * map lookup and a string build on a very hot path.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

	@Unique
	private static final Map<ParticleType<?>, String> KOHS_CORE$ID_CACHE = new ConcurrentHashMap<>();
	@Shadow @Final private Map<ParticleRenderType, ParticleGroup<?>> particles;

	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void kohsCore$gateSpawn(ParticleOptions options, double x, double y, double z, double xa, double ya,
			double za, CallbackInfoReturnable<Particle> callback) {
		ParticleGovernor governor = KoHsCoreClient.particleGovernorOrNull();
		if (governor == null) {
			// Mixins can fire before the client entrypoint has finished wiring things up.
			return;
		}
		String id = KOHS_CORE$ID_CACHE.computeIfAbsent(options.getType(), type -> {
			Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
			return key == null ? "" : key.toString();
		});
		if (!id.isEmpty() && !governor.shouldSpawn(id, x, y, z)) {
			callback.setReturnValue(null);
		}
	}

	/** Vanilla already frustum-culls quad particles; this only avoids entering extraction at all. */
	@Inject(method = "extract", at = @At("HEAD"), cancellable = true)
	private void kohsCore$skipEmptyExtraction(ParticlesRenderState renderState, Frustum frustum, Camera camera,
			float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
		if (ConfigStore.get().skipEmptyRenderPasses && this.particles.isEmpty()) {
			callback.cancel();
		}
	}
}
