package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local-only nametag distance and privacy rules. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private <T extends LivingEntity> void kohsCore$limitNametag(T entity, double distanceToCameraSq,
			CallbackInfoReturnable<Boolean> callback) {
		CoreConfig config = ConfigStore.get();
		if (!KoHsCoreClient.ownsDomain(Domain.ENTITY_CULLING)) {
			return;
		}
		if (config.hidePlayerNametags && entity instanceof Player
				&& entity != Minecraft.getInstance().player) {
			callback.setReturnValue(Boolean.FALSE);
			return;
		}
		int distance = config.nametagDistance;
		if (distance > 0 && distanceToCameraSq > (double) distance * distance) {
			callback.setReturnValue(Boolean.FALSE);
		}
	}
}
