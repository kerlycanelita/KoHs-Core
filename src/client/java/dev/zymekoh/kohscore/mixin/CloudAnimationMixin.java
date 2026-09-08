package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.optimize.AnimationGovernor;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
public abstract class CloudAnimationMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private long kohsCore$cloudTime(long time) {
        return AnimationGovernor.enabled("clouds", Domain.CLOUDS) ? time : 0;
    }
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float kohsCore$cloudPartial(float partial) {
        return AnimationGovernor.enabled("clouds", Domain.CLOUDS) ? partial : 0;
    }
}
