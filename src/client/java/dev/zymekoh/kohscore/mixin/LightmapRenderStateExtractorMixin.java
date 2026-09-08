package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.config.ConfigStore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Avoids recomputing an unchanged lightmap every client tick.
 *
 * <p>Unknown visual changes are bounded to three skipped ticks (150 ms). Dynamic effects such as
 * darkness, expiring night vision, water adaptation, lightning and boss darkening always force an
 * update. This never touches input or packets, but it is opt-in because the bounded visual delay is
 * still a real trade-off.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private GameRenderer renderer;

	@Unique private boolean kohsCore$initialized;
	@Unique private int kohsCore$skippedTicks;
	@Unique private int kohsCore$skyColor;
	@Unique private float kohsCore$skyFactor;
	@Unique private float kohsCore$waterVision;
	@Unique private float kohsCore$endFlash;
	@Unique private float kohsCore$bossDarkening;
	@Unique private double kohsCore$gamma;
	@Unique private double kohsCore$darknessScale;
	@Unique private boolean kohsCore$underWater;
	@Unique private boolean kohsCore$nightVision;
	@Unique private boolean kohsCore$conduitPower;
	@Unique private boolean kohsCore$hideLightning;
	@Unique private DimensionType kohsCore$dimension;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void kohsCore$skipUnchangedLightmap(CallbackInfo callback) {
		if (!ConfigStore.get().lightmapThrottle || !KoHsCoreClient.ownsDomain(Domain.LIGHTMAP)) {
			this.kohsCore$initialized = false;
			this.kohsCore$skippedTicks = 0;
			return;
		}
		ClientLevel level = this.minecraft.level;
		LocalPlayer player = this.minecraft.player;
		if (level == null || player == null) {
			return;
		}

		Camera camera = this.renderer.getMainCamera();
		EnvironmentAttributeProbe probe = camera.attributeProbe();
		int skyColor = probe.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, 1.0F);
		float skyFactor = probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, 1.0F);
		float waterVision = player.getWaterVision();
		boolean underWater = player.isUnderWater();
		MobEffectInstance nightVisionEffect = player.getEffect(MobEffects.NIGHT_VISION);
		boolean nightVision = nightVisionEffect != null;
		boolean conduitPower = player.hasEffect(MobEffects.CONDUIT_POWER);
		boolean hideLightning = this.minecraft.options.hideLightningFlash().get();
		EndFlashState flash = level.endFlashState();
		float endFlash = flash == null || hideLightning ? 0.0F
				: flash.getIntensity(this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		float bossDarkening = this.renderer.getBossOverlayWorldDarkening(1.0F);
		double gamma = this.minecraft.options.gamma().get();
		double darknessScale = this.minecraft.options.darknessEffectScale().get();
		DimensionType dimension = level.dimensionType();

		boolean animatedEffect = player.hasEffect(MobEffects.DARKNESS)
				|| nightVisionEffect != null && nightVisionEffect.endsWithin(200)
				|| underWater && waterVision > 0.0F && waterVision < 1.0F;
		boolean dirty = !this.kohsCore$initialized
				|| skyColor != this.kohsCore$skyColor
				|| Float.compare(skyFactor, this.kohsCore$skyFactor) != 0
				|| Float.compare(waterVision, this.kohsCore$waterVision) != 0
				|| Float.compare(endFlash, this.kohsCore$endFlash) != 0
				|| Float.compare(bossDarkening, this.kohsCore$bossDarkening) != 0
				|| Double.compare(gamma, this.kohsCore$gamma) != 0
				|| Double.compare(darknessScale, this.kohsCore$darknessScale) != 0
				|| underWater != this.kohsCore$underWater
				|| nightVision != this.kohsCore$nightVision
				|| conduitPower != this.kohsCore$conduitPower
				|| hideLightning != this.kohsCore$hideLightning
				|| dimension != this.kohsCore$dimension
				|| animatedEffect
				|| this.kohsCore$skippedTicks >= 3;

		this.kohsCore$initialized = true;
		this.kohsCore$skyColor = skyColor;
		this.kohsCore$skyFactor = skyFactor;
		this.kohsCore$waterVision = waterVision;
		this.kohsCore$endFlash = endFlash;
		this.kohsCore$bossDarkening = bossDarkening;
		this.kohsCore$gamma = gamma;
		this.kohsCore$darknessScale = darknessScale;
		this.kohsCore$underWater = underWater;
		this.kohsCore$nightVision = nightVision;
		this.kohsCore$conduitPower = conduitPower;
		this.kohsCore$hideLightning = hideLightning;
		this.kohsCore$dimension = dimension;

		if (dirty) {
			this.kohsCore$skippedTicks = 0;
		} else {
			this.kohsCore$skippedTicks++;
			callback.cancel();
		}
	}
}
