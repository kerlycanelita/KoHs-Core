package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses a particle light lookup only within the same tick and the same block position. */
@Mixin(Particle.class)
public abstract class ParticleLightCacheMixin {

	@Shadow @Final protected ClientLevel level;
	@Shadow protected double x;
	@Shadow protected double y;
	@Shadow protected double z;

	@Unique private long kohsCore$lightTick = Long.MIN_VALUE;
	@Unique private long kohsCore$lightPos = Long.MIN_VALUE;
	@Unique private int kohsCore$light;

	@Inject(method = "getLightCoords", at = @At("HEAD"), cancellable = true)
	private void kohsCore$reuseLight(float partialTick, CallbackInfoReturnable<Integer> callback) {
		if (!ConfigStore.get().particleStateCache || !KoHsCoreClient.ownsDomain(Domain.PARTICLES)) {
			return;
		}
		long tick = this.level.getGameTime();
		long pos = BlockPos.asLong(Mth.floor(this.x), Mth.floor(this.y), Mth.floor(this.z));
		if (tick == this.kohsCore$lightTick && pos == this.kohsCore$lightPos) {
			callback.setReturnValue(this.kohsCore$light);
		}
	}

	@Inject(method = "getLightCoords", at = @At("RETURN"))
	private void kohsCore$rememberLight(float partialTick, CallbackInfoReturnable<Integer> callback) {
		if (!ConfigStore.get().particleStateCache || !KoHsCoreClient.ownsDomain(Domain.PARTICLES)) {
			return;
		}
		this.kohsCore$lightTick = this.level.getGameTime();
		this.kohsCore$lightPos = BlockPos.asLong(Mth.floor(this.x), Mth.floor(this.y), Mth.floor(this.z));
		this.kohsCore$light = callback.getReturnValue();
	}
}
