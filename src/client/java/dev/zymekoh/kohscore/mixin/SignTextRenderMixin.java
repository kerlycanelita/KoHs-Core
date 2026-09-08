package dev.zymekoh.kohscore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Avoids extracting and submitting distant sign glyphs while leaving the sign model visible.
 *
 * <p>Minecraft 26.1.2 creates render-state matrices for every submitted text line on every frame.
 * Server hubs with many signs can therefore saturate a GPU when the frame rate is unlimited. This
 * gate is presentation-only: it changes no block entity, interaction, input or packet.
 */
@Mixin(AbstractSignRenderer.class)
public abstract class SignTextRenderMixin {

	@Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
	private void kohsCore$cullDistantText(SignRenderState state, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, SignText signText, CallbackInfo callback) {
		CoreConfig config = ConfigStore.get();
		int limit = config.textCullDistance;
		if (limit <= 0 || !KoHsCoreClient.ownsDomain(Domain.ENTITY_CULLING)) {
			return;
		}

		Entity camera = Minecraft.getInstance().getCameraEntity();
		if (camera == null) {
			return;
		}
		BlockPos pos = state.blockPos;
		double dx = pos.getX() + 0.5D - camera.getX();
		double dy = pos.getY() + 0.5D - camera.getY();
		double dz = pos.getZ() + 0.5D - camera.getZ();
		if (dx * dx + dy * dy + dz * dz > (double) limit * limit) {
			callback.cancel();
		}
	}
}
