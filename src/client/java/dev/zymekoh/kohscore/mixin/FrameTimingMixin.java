package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.KoHsCoreClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes completed frame-loop iterations. Never waits, alters input or submits GPU work. */
@Mixin(Minecraft.class)
public abstract class FrameTimingMixin {
    @Inject(method = "runTick", at = @At("RETURN"))
    private void kohsCore$sampleFrame(boolean advanceGameTime, CallbackInfo ci) {
        KoHsCoreClient.noteFrame();
    }
}
