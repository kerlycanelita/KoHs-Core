package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoids setting up an empty toast render pass when no toast can be drawn. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {

	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private List<?> visibleToasts;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void kohsCore$skipEmptyToastPass(GuiGraphicsExtractor graphics, CallbackInfo callback) {
		CoreConfig config = ConfigStore.get();
		if (!config.skipEmptyRenderPasses || !this.visibleToasts.isEmpty()) {
			return;
		}
		boolean musicCanRender = this.minecraft.options.musicToast().get().renderToast()
				&& !(config.streamerMode && config.streamerHideNowPlaying);
		if (!musicCanRender) {
			callback.cancel();
		}
	}
}
