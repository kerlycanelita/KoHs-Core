package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import dev.zymekoh.kohscore.KoHsCoreClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distance culling for entities.
 *
 * <p>This is the visibility test vanilla already runs per entity, not part of the chunk renderer, so
 * it stays out of Sodium's way entirely — Sodium replaces terrain rendering and leaves entity
 * visibility alone.
 *
 * <p>The local player is never culled, and the config default is off, so this method returns after
 * one boolean check unless the player has deliberately turned culling on.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void kohsCore$cullByDistance(E entity, Frustum culler, double camX, double camY,
			double camZ, CallbackInfoReturnable<Boolean> callback) {
		CoreConfig config = ConfigStore.get();
		boolean textDisplay = entity instanceof TextDisplay;
		if (textDisplay ? config.textCullDistance <= 0
				: !config.entityCullingEnabled || config.entityCullDistance <= 0) {
			return;
		}
		if (!KoHsCoreClient.ownsDomain(Domain.ENTITY_CULLING)) {
			return;
		}
		// Players stay visible at every distance. Besides avoiding PvP information changes, this
		// leaves Inventory Tweaks' container player previews and Death Effects' models authoritative.
		if (entity instanceof Player) {
			return;
		}

		// Drops and orbs get their own, usually shorter, distance: they are what actually pile up,
		// and none of them carry information worth a draw call at range.
		int limit = textDisplay ? config.textCullDistance : config.entityCullDistance;
		if (!textDisplay && config.itemCullDistance > 0
				&& (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) {
			limit = config.itemCullDistance;
		}

		double dx = entity.getX() - camX;
		double dy = entity.getY() - camY;
		double dz = entity.getZ() - camZ;
		if (dx * dx + dy * dy + dz * dz > (double) limit * limit) {
			callback.setReturnValue(Boolean.FALSE);
		}
	}
}
