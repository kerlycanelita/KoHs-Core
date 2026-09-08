package dev.zymekoh.kohscore.gui.preview;

import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * A live, animated likeness of one particle type, drawn inside a box.
 *
 * <p>This does not run the real particle simulation. Vanilla particles need a live {@code
 * ClientLevel} and a world camera, neither of which an options screen has any business creating, and
 * spawning real ones into the player's world just to show a thumbnail would be worse than useless.
 * So each type is matched to a {@link ParticleArchetype} and animated from that recipe: right
 * colour, right direction, right rhythm, drawn with the same {@code fill} calls as the rest of the
 * screen.
 *
 * <p>Instances are pooled by the list pane, one for each visible virtual row. Each instance owns its
 * animation clock and particle state; rebinding it to another entry resets that state deterministically.
 */
public final class ParticlePreview {

	/** Upper bound per box. Small on purpose: a dozen of these are on screen at once. */
	private static final int MAX_PARTICLES = 34;

	private static final class Particle {
		float x;
		float y;
		float velocityX;
		float velocityY;
		float size;
		float age;
		float life;
		float seed;
	}

	private final Particle[] particles = new Particle[MAX_PARTICLES];
	private final Random random = new Random();

	private ParticleArchetype archetype = ParticleArchetype.GENERIC;
	private int activeCount;
	private long lastFrameMillis = System.nanoTime() / 1_000_000L;
	private int boxWidth = 1;
	private int boxHeight = 1;
	private long configuredSeed;
	private boolean configured;

	public ParticlePreview() {
		for (int index = 0; index < MAX_PARTICLES; index++) {
			this.particles[index] = new Particle();
		}
	}

	/**
	 * Points the preview at a recipe and sizes it to a box.
	 *
	 * <p>Changing the recipe, size or entry seed resets the pool. Repeating the same configuration is
	 * allocation-free and leaves the independent animation running.
	 */
	public void configure(ParticleArchetype archetype, int width, int height, long seed) {
		int newWidth = Math.max(1, width);
		int newHeight = Math.max(1, height);
		boolean changed = !this.configured || this.archetype != archetype || this.boxWidth != newWidth
				|| this.boxHeight != newHeight || this.configuredSeed != seed;
		this.archetype = archetype;
		this.boxWidth = newWidth;
		this.boxHeight = newHeight;
		this.configuredSeed = seed;
		this.configured = true;

		// Scale the recipe's density down for small boxes; a 20px thumbnail wants a handful.
		float area = this.boxWidth * this.boxHeight / 4000.0F;
		this.activeCount = Mth.clamp(Math.round(archetype.density() * Mth.clamp(area, 0.20F, 1.0F)), 3,
				MAX_PARTICLES);

		if (changed) {
			this.random.setSeed(seed);
			for (int index = 0; index < this.activeCount; index++) {
				this.spawn(this.particles[index], true);
			}
			this.lastFrameMillis = System.nanoTime() / 1_000_000L;
		}
	}

	private void spawn(Particle particle, boolean stagger) {
		ParticleArchetype recipe = this.archetype;
		float centreX = this.boxWidth / 2.0F;
		float centreY = this.boxHeight / 2.0F;

		switch (recipe.spawnShape()) {
			case SCATTER -> {
				particle.x = this.random.nextFloat() * this.boxWidth;
				particle.y = this.random.nextFloat() * this.boxHeight;
				particle.velocityX = recipe.driftX() + spread(recipe.spread());
				particle.velocityY = recipe.driftY() + spread(recipe.spread());
			}
			case CENTRE -> {
				particle.x = centreX + spread(2.0F);
				particle.y = centreY + spread(2.0F);
				particle.velocityX = recipe.driftX() + spread(recipe.spread());
				particle.velocityY = recipe.driftY() + spread(recipe.spread());
			}
			case FLOOR -> {
				particle.x = this.random.nextFloat() * this.boxWidth;
				particle.y = this.boxHeight - 1.0F;
				particle.velocityX = recipe.driftX() + spread(recipe.spread() * 0.5F);
				particle.velocityY = recipe.driftY();
			}
			case CEILING -> {
				particle.x = this.random.nextFloat() * this.boxWidth;
				particle.y = 0.0F;
				particle.velocityX = recipe.driftX() + spread(recipe.spread() * 0.5F);
				particle.velocityY = recipe.driftY();
			}
			case INWARD -> {
				// Start on a ring and aim at the middle, which is how portal and enchant read.
				float angle = this.random.nextFloat() * Mth.TWO_PI;
				float radius = Math.min(centreX, centreY) * (0.75F + this.random.nextFloat() * 0.35F);
				particle.x = centreX + Mth.cos(angle) * radius;
				particle.y = centreY + Mth.sin(angle) * radius;
				float inwardSpeed = 12.0F + this.random.nextFloat() * 10.0F;
				particle.velocityX = -Mth.cos(angle) * inwardSpeed;
				particle.velocityY = -Mth.sin(angle) * inwardSpeed;
			}
		}

		particle.size = recipe.sizeMin() + this.random.nextFloat() * (recipe.sizeMax() - recipe.sizeMin());
		particle.life = recipe.lifeSeconds() * (0.7F + this.random.nextFloat() * 0.6F);
		// Staggering ages on the first fill stops every particle from dying on the same frame.
		particle.age = stagger ? this.random.nextFloat() * particle.life : 0.0F;
		particle.seed = this.random.nextFloat() * Mth.TWO_PI;
	}

	private float spread(float amount) {
		return (this.random.nextFloat() - 0.5F) * 2.0F * amount;
	}

	/** Advances and draws the preview at a screen position. */
	public void render(GuiGraphicsExtractor graphics, int x, int y) {
		long now = System.nanoTime() / 1_000_000L;
		float elapsed = Mth.clamp((now - this.lastFrameMillis) / 1000.0F, 0.0F, 0.05F);
		this.lastFrameMillis = now;

		ParticleArchetype recipe = this.archetype;

		graphics.enableScissor(x, y, x + this.boxWidth, y + this.boxHeight);
		for (int index = 0; index < this.activeCount; index++) {
			Particle particle = this.particles[index];

			particle.age += elapsed;
			if (particle.age >= particle.life) {
				this.spawn(particle, false);
			}

			particle.velocityY += recipe.gravity() * elapsed;
			particle.x += particle.velocityX * elapsed;
			particle.y += particle.velocityY * elapsed;

			float progress = Mth.clamp(particle.age / particle.life, 0.0F, 1.0F);

			// Fade in quickly, out slowly: reads as an effect rather than as blinking squares.
			float visibility = progress < 0.15F ? progress / 0.15F : 1.0F - (progress - 0.15F) / 0.85F;
			if (recipe.flickers()) {
				visibility *= 0.65F + 0.35F * Mth.sin(now / 55.0F + particle.seed);
			}
			if (visibility <= 0.02F) {
				continue;
			}

			float size = recipe.expands() ? particle.size * (0.5F + progress * 1.4F) : particle.size;
			int drawSize = Math.max(1, Math.round(size));
			int alpha = Mth.clamp(Math.round(235.0F * visibility), 0, 255);
			int rgb = blendRgb(recipe.colorPrimary(), recipe.colorAccent(), progress);

			int px = x + Math.round(particle.x);
			int py = y + Math.round(particle.y);
			graphics.fill(px, py, px + drawSize, py + drawSize, CoreTheme.opaque(rgb, alpha));
		}
		graphics.disableScissor();
	}

	/** Draws a framed box with the preview inside it. */
	public void renderFramed(GuiGraphicsExtractor graphics, int x, int y, boolean highlighted) {
		CoreDraw.roundedRect(graphics, x, y, this.boxWidth, this.boxHeight, 3, CoreTheme.PANEL_DEEP);
		this.render(graphics, x, y);
		CoreDraw.outline(graphics, x, y, this.boxWidth, this.boxHeight,
				highlighted ? CoreTheme.ACCENT_SOFT : CoreTheme.BORDER_SOFT);
	}

	private static int blendRgb(int from, int to, float amount) {
		float t = Mth.clamp(amount, 0.0F, 1.0F);
		int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return r << 16 | g << 8 | b;
	}
}
