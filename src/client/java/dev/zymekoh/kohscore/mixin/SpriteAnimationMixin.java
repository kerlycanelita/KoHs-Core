package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.optimize.AnimationGovernor;
import dev.zymekoh.kohscore.optimize.SpriteAnimationControl;
import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteContents.AnimationState.class)
public abstract class SpriteAnimationMixin implements SpriteAnimationControl {
	@Unique private String kohsCore$category = "reserved";
	@Unique private int kohsCore$intervalTick;
	@Unique private boolean kohsCore$draw = true;
	@Unique private boolean kohsCore$uploaded;
	@Override public void kohsCore$setSprite(Identifier id) {
		this.kohsCore$category = AnimationGovernor.textureCategory(id);
	}
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void kohsCore$pause(CallbackInfo ci) {
		boolean allowed = AnimationGovernor.textureEnabled(this.kohsCore$category);
		int interval = this.kohsCore$category.equals("reserved")
				|| !KoHsCoreClient.ownsDomain(Domain.ANIMATED_TEXTURES) ? 1 : ConfigStore.get().animatedTextureInterval;
		this.kohsCore$draw = allowed && (++this.kohsCore$intervalTick % Math.max(1, interval) == 0);
		if (!this.kohsCore$draw) ci.cancel();
	}
	@Inject(method = "needsToDraw", at = @At("HEAD"), cancellable = true)
	private void kohsCore$skipUpload(CallbackInfoReturnable<Boolean> cir) {
		// The initial frame must reach the atlas even when animations were disabled before loading.
		if (!this.kohsCore$uploaded) cir.setReturnValue(true);
		else if (!this.kohsCore$draw || !AnimationGovernor.textureEnabled(this.kohsCore$category)) cir.setReturnValue(false);
	}
	@Inject(method = "drawToAtlas", at = @At("RETURN"))
	private void kohsCore$uploaded(CallbackInfo ci) { this.kohsCore$uploaded = true; }
}
