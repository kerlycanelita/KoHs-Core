package dev.zymekoh.kohscore.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.zymekoh.kohscore.optimize.SpriteAnimationControl;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin {
	@Shadow public abstract Identifier name();
	@Inject(method = "createAnimationState", at = @At("RETURN"))
	private void kohsCore$identify(GpuBufferSlice slice, int size,
			CallbackInfoReturnable<SpriteContents.AnimationState> cir) {
		if (cir.getReturnValue() instanceof SpriteAnimationControl control) control.kohsCore$setSprite(this.name());
	}
}
