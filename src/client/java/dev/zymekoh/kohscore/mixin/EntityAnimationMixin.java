package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.optimize.AnimationGovernor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityAnimationMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void kohsCore$staticPose(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
        // Preserve players, mod entities and positional interpolation.
        if (entity instanceof Player || !"minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace())) return;
        EntityRenderState state = cir.getReturnValue();
        if (state instanceof ItemEntityRenderState item) {
            if (!AnimationGovernor.enabled("items", Domain.ENTITY_ANIMATIONS)) { item.ageInTicks = 0; item.bobOffset = 0; }
            return;
        }
        if (AnimationGovernor.enabled("entities", Domain.ENTITY_ANIMATIONS)) return;
        state.ageInTicks = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.walkAnimationPos = 0;
            living.walkAnimationSpeed = 0;
            living.wornHeadAnimationPos = 0;
        }
        if (state instanceof ArmedEntityRenderState armed) armed.attackTime = 0;
    }
}
