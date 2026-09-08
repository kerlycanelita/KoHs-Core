package dev.zymekoh.kohscore.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every persisted setting, as plain public fields so Gson round-trips it without adapters.
 *
 * <p>Defaults are deliberately conservative: a fresh install should behave like vanilla until the
 * player opts into something. The aggressive presets are one click away, not the default.
 */
public final class CoreConfig {

	// --- Particles -----------------------------------------------------------------------
	/** Master kill switch. When true nothing spawns regardless of the per-type map. */
	public boolean particlesDisabledGlobally = false;
	/** Registry ids explicitly switched off, keyed like {@code minecraft:explosion}. */
	public Map<String, Boolean> particleOverrides = new LinkedHashMap<>();
	/** Shared spawn budget across every mod that asks Core, per second. 0 disables the limiter. */
	public int particleAllowancePerSecond = 0;
	/** Multiplier applied to burst sizes, 0.05..1.0. */
	public float particleBurstScale = 1.0F;
	/** Particles further than this many blocks are dropped at spawn time. 0 disables. */
	public int particleCullDistance = 0;
	/** Let the pressure monitor shrink the allowance automatically when frames get expensive. */
	public boolean particleAdaptiveBudget = true;
	/** Cache each particle's light lookup for the rest of the same client tick. */
	public boolean particleStateCache = false;

	// --- Graphics ------------------------------------------------------------------------
	/**
	 * Extra quality tiers below vanilla Fast.
	 *
	 * <p>0 leaves vanilla alone, 1 is Reduced, 2 is Pixelated, 3 is Potato. Tiers 2 and 3 are the
	 * two extra steps beyond anything vanilla exposes.
	 */
	public int extremeQualityTier = 0;
	/** Desired near-detail radius used to choose the safe render-distance approximation for tier 2. */
	public int sharpRadiusChunks = 2;

	// --- Entity culling ------------------------------------------------------------------
	public boolean entityCullingEnabled = false;
	/**
	 * A separate, usually shorter distance for dropped items and experience orbs.
	 *
	 * <p>Worth its own control because these are the entities that actually pile up. A hundred drops
	 * after a mob farm or a death are a hundred rendered entities, and none of them carry information
	 * you need at range — unlike a player, whose position at forty blocks may decide a fight.
	 * 0 makes them follow the general distance.
	 */
	public int itemCullDistance = 0;
	/** Entities beyond this distance are skipped entirely. 0 uses vanilla behaviour. */
	public int entityCullDistance = 0;
	/**
	 * Block entities past this distance build no render state at all. 0 uses vanilla behaviour.
	 *
	 * <p>Broader than {@link #textCullDistance}, which keeps the sign and drops only its text. This
	 * one removes the chest, the banner, the skull and the beacon beam as well, which is where the
	 * frames in a busy hub actually go.
	 */
	public int blockEntityCullDistance = 0;
	/** Let the pressure monitor tighten that distance while frames are expensive. Never loosens it. */
	public boolean blockEntityAdaptiveDistance = false;
	/**
	 * Text on signs and text-display entities is skipped past this distance while their backing
	 * object remains visible. Text extraction is unusually allocation-heavy in 26.1.2, especially
	 * in server hubs. 0 restores vanilla's unlimited text distance.
	 */
	public int textCullDistance = 48;

	// --- Memory --------------------------------------------------------------------------
	public boolean memoryManagementEnabled = true;
	/** Heap ratio above which Core starts preventing transient allocations more aggressively. */
	public float heapPressureThreshold = 0.85F;

	// --- Nametags ------------------------------------------------------------------------
	/** Distance past which entity nametags stop being drawn. 0 keeps vanilla's own limit. */
	public int nametagDistance = 0;
	/** Hide other players' nametags entirely. Off by default: it changes what you can see. */
	public boolean hidePlayerNametags = false;

	// --- Animated textures ---------------------------------------------------------------
	/**
	 * Advance animated sprites once every N client ticks instead of every tick.
	 *
	 * <p>1 is vanilla. Higher values make water, lava and fire animate in coarser steps and cost
	 * proportionally less. This is frame work, not input work, so it cannot affect latency.
	 */
	public int animatedTextureInterval = 1;
	/** Master visual animation switch. Gameplay ticks, movement and packets always remain live. */
	public boolean animationsEnabled = true;
	/** Category overrides; absence means animated. Mod-owned domains bypass these overrides. */
	public Map<String, Boolean> animationCategories = new LinkedHashMap<>();

	// --- Lightmap ------------------------------------------------------------------------
	/**
	 * Skip the lightmap recompute on ticks where only the torch flicker changed.
	 *
	 * <p>Vanilla marks the lightmap dirty every single tick because the flicker value always moves,
	 * so the whole environment lookup runs twenty times a second even underground with nothing
	 * changing. The cost is the attribute probe calls, not the flicker itself.
	 */
	public boolean lightmapThrottle = false;

	// --- Safe empty-work skips -------------------------------------------------------------
	/** Skip render extractors whose backing collections are provably empty. */
	public boolean skipEmptyRenderPasses = true;
	/** Replace vanilla's 0.1 ms idle park with 0.5 ms, but only while unfocused. */
	public boolean backgroundCpuThrottle = false;

	// --- Streaming -----------------------------------------------------------------------
	/** Master switch for the privacy features below. */
	public boolean streamerMode = false;
	/** Suppress the local chat overlay while streaming; messages remain in chat history. */
	public boolean streamerHideChat = false;
	/** Replace coordinates and the local server address in the F3 overlay. */
	public boolean streamerHideCoordinates = true;
	/** Mask server addresses in the server list and the direct-connect screen. */
	public boolean streamerHideServerAddress = true;
	/** Suppress the Now Playing toast, which names the track and artist on screen. */
	public boolean streamerHideNowPlaying = true;
	/** Remove only the local player's row from the rendered tab list. */
	public boolean streamerHideOwnTabEntry = false;
	/** Frames per second your stream outputs, used by the cadence advisor. 0 disables it. */
	public int streamOutputFps = 0;

	// --- Performance overlay -------------------------------------------------------------
	/** Draw the frame-rate overlay in game. */
	public boolean performanceHudEnabled = false;
	/** Which corner: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
	public int performanceHudCorner = 0;
	/**
	 * How much the overlay shows: 0 frame rate only, 1 adds frame time, 2 adds the worst frame of
	 * the last second and heap usage.
	 *
	 * <p>The worst frame is the number that matters for stutter. An average of 120 FPS with one
	 * 40 ms frame per second feels far worse than a steady 90, and only the second figure shows it.
	 */
	public int performanceHudDetail = 1;

	// --- Interface -----------------------------------------------------------------------
	/** Draw the purple particle field behind Core's own screen. */
	public boolean guiParticles = true;
	/** Particle count multiplier for the field, 0.1..2.0. */
	public float guiParticleDensity = 1.0F;
	/** Type descriptions out letter by letter instead of showing them all at once. */
	public boolean typewriterDescriptions = true;
	/**
	 * Milliseconds per character.
	 *
	 * <p>The slider bottoms out at 0.1 ms, which finishes any sentence inside a single frame. The
	 * default of 6 ms is the fastest speed at which the effect is still visible at 60 Hz.
	 */
	public float typewriterMillisPerChar = 6.0F;
	/** Replace vanilla's Video Settings button with Core's screen. */
	public boolean takeOverVideoSettings = true;

	/** Deep copy, used for cancel-without-saving in the settings screen. */
	public CoreConfig copy() {
		CoreConfig clone = new CoreConfig();
		clone.particlesDisabledGlobally = this.particlesDisabledGlobally;
		clone.particleOverrides = new LinkedHashMap<>(this.particleOverrides);
		clone.particleAllowancePerSecond = this.particleAllowancePerSecond;
		clone.particleBurstScale = this.particleBurstScale;
		clone.particleCullDistance = this.particleCullDistance;
		clone.particleAdaptiveBudget = this.particleAdaptiveBudget;
		clone.particleStateCache = this.particleStateCache;
		clone.extremeQualityTier = this.extremeQualityTier;
		clone.sharpRadiusChunks = this.sharpRadiusChunks;
		clone.entityCullingEnabled = this.entityCullingEnabled;
		clone.entityCullDistance = this.entityCullDistance;
		clone.itemCullDistance = this.itemCullDistance;
		clone.textCullDistance = this.textCullDistance;
		clone.blockEntityCullDistance = this.blockEntityCullDistance;
		clone.blockEntityAdaptiveDistance = this.blockEntityAdaptiveDistance;
		clone.memoryManagementEnabled = this.memoryManagementEnabled;
		clone.heapPressureThreshold = this.heapPressureThreshold;
		clone.guiParticles = this.guiParticles;
		clone.guiParticleDensity = this.guiParticleDensity;
		clone.typewriterDescriptions = this.typewriterDescriptions;
		clone.typewriterMillisPerChar = this.typewriterMillisPerChar;
		clone.takeOverVideoSettings = this.takeOverVideoSettings;
		clone.performanceHudEnabled = this.performanceHudEnabled;
		clone.performanceHudCorner = this.performanceHudCorner;
		clone.performanceHudDetail = this.performanceHudDetail;
		clone.nametagDistance = this.nametagDistance;
		clone.hidePlayerNametags = this.hidePlayerNametags;
		clone.animatedTextureInterval = this.animatedTextureInterval;
		clone.animationsEnabled = this.animationsEnabled;
		clone.animationCategories = new LinkedHashMap<>(this.animationCategories);
		clone.lightmapThrottle = this.lightmapThrottle;
		clone.skipEmptyRenderPasses = this.skipEmptyRenderPasses;
		clone.backgroundCpuThrottle = this.backgroundCpuThrottle;
		clone.streamerMode = this.streamerMode;
		clone.streamerHideChat = this.streamerHideChat;
		clone.streamerHideCoordinates = this.streamerHideCoordinates;
		clone.streamerHideServerAddress = this.streamerHideServerAddress;
		clone.streamerHideNowPlaying = this.streamerHideNowPlaying;
		clone.streamerHideOwnTabEntry = this.streamerHideOwnTabEntry;
		clone.streamOutputFps = this.streamOutputFps;
		return clone;
	}

	/** Clamps everything into range and repairs anything an older config file left null. */
	public void sanitize() {
		if (this.animationCategories == null) this.animationCategories = new LinkedHashMap<>();
		if (this.particleOverrides == null) {
			this.particleOverrides = new LinkedHashMap<>();
		}
		this.particleAllowancePerSecond = clamp(this.particleAllowancePerSecond, 0, 20_000);
		this.particleBurstScale = clamp(this.particleBurstScale, 0.05F, 1.0F);
		this.particleCullDistance = clamp(this.particleCullDistance, 0, 512);
		this.extremeQualityTier = clamp(this.extremeQualityTier, 0, 3);
		this.sharpRadiusChunks = clamp(this.sharpRadiusChunks, 1, 32);
		this.entityCullDistance = clamp(this.entityCullDistance, 0, 512);
		this.itemCullDistance = clamp(this.itemCullDistance, 0, 512);
		this.textCullDistance = clamp(this.textCullDistance, 0, 512);
		this.blockEntityCullDistance = clamp(this.blockEntityCullDistance, 0, 512);
		this.performanceHudCorner = clamp(this.performanceHudCorner, 0, 3);
		this.performanceHudDetail = clamp(this.performanceHudDetail, 0, 2);
		this.nametagDistance = clamp(this.nametagDistance, 0, 256);
		this.animatedTextureInterval = clamp(this.animatedTextureInterval, 1, 20);
		this.streamOutputFps = this.streamOutputFps == 0 ? 0 : clamp(this.streamOutputFps, 15, 240);
		this.heapPressureThreshold = clamp(this.heapPressureThreshold, 0.50F, 0.99F);
		this.guiParticleDensity = clamp(this.guiParticleDensity, 0.1F, 2.0F);
		this.typewriterMillisPerChar = clamp(this.typewriterMillisPerChar, 0.1F, 80.0F);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
