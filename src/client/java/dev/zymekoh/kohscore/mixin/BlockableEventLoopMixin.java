package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses a longer idle park only while Minecraft is unfocused. Focused gameplay is untouched. */
@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

	@Inject(method = "waitForTasks", at = @At("HEAD"), cancellable = true)
	private void kohsCore$parkLongerInBackground(CallbackInfo callback) {
		if (!ConfigStore.get().backgroundCpuThrottle || !KoHsCoreClient.ownsDomain(Domain.BACKGROUND_CPU)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && !minecraft.isWindowActive()) {
			LockSupport.parkNanos("KoHs Core background idle", 500_000L);
			callback.cancel();
		}
	}
}
