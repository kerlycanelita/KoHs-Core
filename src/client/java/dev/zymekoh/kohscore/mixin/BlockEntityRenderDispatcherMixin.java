package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.PressureLevel;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distance culling for block entities, applied where their render state is built.
 *
 * <p>A server hub is mostly block entities: chests, signs, banners, skulls, beacons and shulkers,
 * each of which builds a fresh render state every frame for as long as it is in view. Vanilla's own
 * per-renderer {@code shouldRender} runs after that decision has already been made for the common
 * case, so cancelling here is what actually removes the work rather than moving it.
 *
 * <p>This is not the chunk renderer. Block entities are drawn outside the terrain pipeline, which is
 * why this stays compatible with Sodium the same way the entity cull does.
 *
 * <p>Cost when off: one integer comparison. The default is 0, which is vanilla.
 */
@Mixin(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

	@Shadow
	private Vec3 cameraPos;

	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void kohsCore$cullDistantBlockEntities(BlockEntity blockEntity, float partialTicks,
			ModelFeatureRenderer.CrumblingOverlay breakProgress,
			CallbackInfoReturnable<BlockEntityRenderState> callback) {
		CoreConfig config = ConfigStore.get();
		if (config.blockEntityCullDistance <= 0) {
			return;
		}
		if (!KoHsCoreClient.ownsDomain(Domain.ENTITY_CULLING)) {
			return;
		}

		Vec3 camera = this.cameraPos;
		if (camera == null) {
			return;
		}

		int limit = kohsCore$effectiveDistance(config);
		BlockPos position = blockEntity.getBlockPos();
		// Block centre, so a limit reads as the distance to the block the player sees rather than
		// to its north-west-bottom corner.
		double dx = position.getX() + 0.5 - camera.x;
		double dy = position.getY() + 0.5 - camera.y;
		double dz = position.getZ() + 0.5 - camera.z;
		if (dx * dx + dy * dy + dz * dz > (double) limit * limit) {
			callback.setReturnValue(null);
		}
	}

	/**
	 * The distance actually in force this frame.
	 *
	 * <p>With the adaptive switch on, the configured distance is what the player gets while frames
	 * are cheap and the ceiling they never exceed while frames are not. It tightens rather than
	 * loosens, so turning it on can only remove work; and it never falls below sixteen blocks,
	 * because a hub where the chest in front of you disappears is not a faster hub, it is a broken
	 * one.
	 */
	private static int kohsCore$effectiveDistance(CoreConfig config) {
		if (!config.blockEntityAdaptiveDistance) {
			return config.blockEntityCullDistance;
		}
		PressureLevel pressure = KoHsCoreClient.pressure();
		double factor = switch (pressure) {
			case RELAXED -> 1.0;
			case ELEVATED -> 0.75;
			case HIGH -> 0.5;
			case CRITICAL -> 0.35;
		};
		return Math.max(16, (int) Math.round(config.blockEntityCullDistance * factor));
	}
}
