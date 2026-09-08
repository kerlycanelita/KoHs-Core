package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.config.ConfigStore;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips debug-world setup when the permanent renderer has provably nothing to emit. */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {

	@Shadow private List<?> renderers;
	@Shadow private long lastDebugEntriesVersion;

	@Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
	private void kohsCore$skipEmptyDebugPass(Frustum frustum, double camX, double camY, double camZ,
			float partialTicks, CallbackInfo callback) {
		if (!ConfigStore.get().skipEmptyRenderPasses || this.renderers.size() != 1) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.debugEntries.getCurrentlyEnabledVersion() != this.lastDebugEntriesVersion) {
			return;
		}
		boolean chunkDebug = minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_PATHS)
				|| minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_VISIBILITY);
		if (!chunkDebug && minecraft.gameRenderer.getMainCamera().getCapturedFrustum() == null) {
			callback.cancel();
		}
	}
}
