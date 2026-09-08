package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.optimize.AnimationGovernor;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class HandAnimationMixin {
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float kohsCore$staticSwing(float attack) {
        return AnimationGovernor.enabled("hands", Domain.HAND_ANIMATIONS) ? attack : 0;
    }
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float kohsCore$staticEquip(float equip) {
        return AnimationGovernor.enabled("hands", Domain.HAND_ANIMATIONS) ? equip : 0;
    }
    @Inject(method = {"applyEatTransform", "applyBrushTransform"}, at = @At("HEAD"), cancellable = true)
    private void kohsCore$staticUse(CallbackInfo ci) {
        if (!AnimationGovernor.enabled("hands", Domain.HAND_ANIMATIONS)) ci.cancel();
    }
}
