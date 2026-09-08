package dev.zymekoh.kohscore.gui;

import dev.zymekoh.kohscore.config.ConfigStore;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The purple particles drifting behind Core's screen.
 *
 * <p>Dense and quick, as asked for, but bounded: the count scales with the window area and stops at
 * a ceiling, so a fullscreen 4K window does not quietly turn the settings menu into the most
 * expensive thing the game is drawing.
 *
 * <p>Motion integrates against real elapsed time with a clamp, not against frames. That keeps the
 * drift speed identical at 30 and at 300 FPS, and stops a stutter from teleporting every particle
 * across the screen.
 */
public final class ParticleField {

	private static final int MAX_PARTICLES = 520;
	private static final int MIN_PARTICLES = 190;
	private static final long PIXELS_PER_PARTICLE = 4_200L;
	/** A frame longer than this is treated as this long, so a hitch does not become a jump. */
	private static final float MAX_FRAME_SECONDS = 0.05F;

	private static final class Particle {
		float x;
		float y;
		float speedX;
		float speedY;
		int size;
		int baseAlpha;
		int trail;
		float twinklePhase;
	}

	private Particle[] particles = new Particle[0];
	private long lastFrameMillis = System.nanoTime() / 1_000_000L;

	/** Rebuilds the field for a new window size. Deterministic, so it looks the same each time. */
	public void resize(int width, int height) {
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		long area = (long) safeWidth * safeHeight;

		float density = ConfigStore.get().guiParticleDensity;
		int count = Mth.clamp(Math.round((MIN_PARTICLES + area / PIXELS_PER_PARTICLE) * density),
				24, MAX_PARTICLES);

		Random random = new Random(0x4B4F48534355L ^ (long) safeWidth << 32 ^ safeHeight);
		this.particles = new Particle[count];
		for (int index = 0; index < count; index++) {
			Particle particle = new Particle();
			// One depth value drives size, brightness, speed and trail, which is what makes the
			// field read as having layers rather than as uniform noise.
			float depth = random.nextFloat();
			boolean near = depth > 0.80F;
			boolean far = depth < 0.42F;
			float speedScale = 0.7F + depth * 2.4F;

			particle.x = random.nextFloat() * safeWidth;
			particle.y = random.nextFloat() * safeHeight;
			particle.speedX = (10.0F + random.nextFloat() * 34.0F) * speedScale;
			particle.speedY = (-64.0F - random.nextFloat() * 46.0F) * speedScale;
			particle.size = far ? 1 : near ? 3 + random.nextInt(2) : 2;
			particle.baseAlpha = far ? 18 + random.nextInt(26) : near ? 108 + random.nextInt(80)
					: 52 + random.nextInt(58);
			particle.trail = far ? 0 : particle.size * (near ? 3 : 2);
			particle.twinklePhase = random.nextFloat() * Mth.TWO_PI;
			this.particles[index] = particle;
		}
		this.lastFrameMillis = System.nanoTime() / 1_000_000L;
	}

	/**
	 * Advances and draws the field. Call first in the screen's render so everything else sits on
	 * top of it.
	 */
	public void render(GuiGraphicsExtractor graphics, int width, int height, int mouseX, int mouseY) {
		if (this.particles.length == 0) {
			return;
		}

		long nowMillis = System.nanoTime() / 1_000_000L;
		float elapsed = Mth.clamp((nowMillis - this.lastFrameMillis) / 1000.0F, 0.0F, MAX_FRAME_SECONDS);
		this.lastFrameMillis = nowMillis;

		float reactionRadius = Mth.clamp(Math.min(width, height) / 3.4F, 46.0F, 120.0F);
		float radiusSquared = reactionRadius * reactionRadius;
		float twinkle = nowMillis / 320.0F;

		for (Particle particle : this.particles) {
			float dx = particle.x - mouseX;
			float dy = particle.y - mouseY;
			float distanceSquared = dx * dx + dy * dy;

			if (distanceSquared < radiusSquared && distanceSquared > 0.5F) {
				// The cursor opens a visible gap, which makes a static-looking field feel alive.
				float distance = (float) Math.sqrt(distanceSquared);
				float influence = 1.0F - distance / reactionRadius;
				float push = 52.0F * influence * influence * (0.4F + particle.size * 0.3F);
				particle.x += dx / distance * push * elapsed;
				particle.y += dy / distance * push * elapsed;
			}

			particle.x += particle.speedX * elapsed;
			particle.y += particle.speedY * elapsed;

			// Wrap rather than respawn, so density never dips.
			if (particle.x > width + 12.0F) {
				particle.x = -12.0F;
			} else if (particle.x < -12.0F) {
				particle.x = width + 12.0F;
			}
			if (particle.y < -10.0F) {
				particle.y = height + 10.0F;
			} else if (particle.y > height + 10.0F) {
				particle.y = -10.0F;
			}

			float pulse = 0.74F + 0.26F * Mth.sin(twinkle + particle.twinklePhase);
			int alpha = Mth.clamp(Math.round(particle.baseAlpha * pulse), 5, 240);
			int x = Math.round(particle.x);
			int y = Math.round(particle.y);

			if (particle.trail > 0) {
				graphics.fill(x, y + particle.size, x + particle.size, y + particle.size + particle.trail,
						CoreTheme.argb(Math.max(4, alpha / 3), 138, 58, 216));
			}
			graphics.fill(x, y, x + particle.size, y + particle.size,
					CoreTheme.argb(alpha, 216, 140, 252));
		}
	}
}
