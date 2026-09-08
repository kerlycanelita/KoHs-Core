package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.optimize.AnimationGovernor;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockAnimationMixin {
    @Inject(method = "tryExtractRenderState", at = @At("RETURN"))
    private void kohsCore$staticDecorations(CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (AnimationGovernor.enabled("blocks", Domain.BLOCK_ANIMATIONS)) return;
        var s = cir.getReturnValue();
        if (s == null || !"minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                .getKey(s.blockEntityType).getNamespace())) return;
        if (s instanceof BannerRenderState b) b.phase = 0;
        if (s instanceof EnchantTableRenderState b) { b.time = 0; b.flip = 0; b.yRot = 0; }
        if (s instanceof ConduitRenderState b) { b.animTime = 0; b.activeRotation = 0; b.animationPhase = 0; }
        if (s instanceof BellRenderState b) { b.ticks = 0; b.shakeDirection = null; }
        if (s instanceof ChestRenderState b) b.open = b.open > 0.5F ? 1 : 0;
        if (s instanceof ShulkerBoxRenderState b) b.progress = b.progress > 0.5F ? 1 : 0;
        if (s instanceof DecoratedPotRenderState b) b.wobbleProgress = 0;
        if (s instanceof SkullBlockRenderState b) b.animationProgress = 0;
        if (s instanceof SpawnerRenderState b) b.spin = 0;
        if (s instanceof VaultRenderState b) b.spin = 0;
        if (s instanceof BeaconRenderState b) b.animationTime = 0;
        if (s instanceof EndGatewayRenderState b) b.animationTime = 0;
    }
}
