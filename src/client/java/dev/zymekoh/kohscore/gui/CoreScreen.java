package dev.zymekoh.kohscore.gui;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.kohscore.KoHsCoreClient;
import dev.zymekoh.kohscore.api.CoreOption;
import dev.zymekoh.kohscore.api.CoreSettingsPage;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.DomainClaim;
import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import dev.zymekoh.kohscore.gui.preview.ParticleCatalog;
import dev.zymekoh.kohscore.gui.tab.ParticleListPane;
import dev.zymekoh.kohscore.gui.widget.CopyButton;
import dev.zymekoh.kohscore.gui.widget.CoreControl;
import dev.zymekoh.kohscore.gui.widget.CoreSlider;
import dev.zymekoh.kohscore.gui.widget.CoreToggle;
import dev.zymekoh.kohscore.gui.widget.TabButton;
import dev.zymekoh.kohscore.jvm.HardwareProbe;
import dev.zymekoh.kohscore.jvm.JvmAdvisor;
import dev.zymekoh.kohscore.jvm.JvmProfile;
import dev.zymekoh.kohscore.mixin.ScreenInvoker;
import dev.zymekoh.kohscore.optimize.MemoryGovernor;
import dev.zymekoh.kohscore.orchestration.PriorityCompatibility;
import dev.zymekoh.kohscore.profile.GameProfile;
import dev.zymekoh.kohscore.profile.ProfileManager;
import dev.zymekoh.kohscore.gui.widget.ProfileButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Core's settings screen, which replaces vanilla's Video Settings.
 *
 * <p>The frame is derived from the current logical GUI size. The tab rail and the option pane scroll
 * independently, while the description panel shrinks and finally disappears before it can steal
 * usable space from the controls. This keeps the same structure from Auto through 4x GUI scale.
 *
 * <p>Controls that another mod owns are still drawn, dimmed and inert, with a tooltip naming the
 * owner. Removing them would leave the player hunting for a setting that used to be there.
 */
public final class CoreScreen extends Screen {

	private static final int PREFERRED_HEADER_HEIGHT = 32;
	private static final int PREFERRED_RAIL_WIDTH = 104;
	private static final int PREFERRED_DESCRIPTION_HEIGHT = 50;
	private static final int PREFERRED_ROW_HEIGHT = 21;
	private static final int PREFERRED_ROW_GAP = 4;
	private static final int MIN_CONTENT_WIDTH = 84;
	private static final int COPY_BUTTON_WIDTH = 44;
	private static final int COPY_BUTTON_HEIGHT = 13;
	/** Below this content width the copy buttons are dropped rather than crushed. */
	private static final int MIN_WIDTH_FOR_COPY_BUTTON = 150;
	/** Below this window width the rail shows icons only. */
	private static final int COMPACT_RAIL_THRESHOLD = 360;
	private static final int ICON_RAIL_WIDTH = 26;

	private final @Nullable Screen parent;
	private final ParticleField particleField = new ParticleField();
	private final Typewriter typewriter = new Typewriter();
	private final ParticleListPane particlePane = new ParticleListPane();
	private final OptionTree optionTree = new OptionTree();

	private final List<CoreControl> controls = new ArrayList<>();
	private final List<TabButton> tabButtons = new ArrayList<>();
	private final List<AbstractWidget> scrollingWidgets = new ArrayList<>();
	/**
	 * One copy button per JVM profile, in profile order.
	 *
	 * <p>Kept out of {@link #scrollingWidgets} because that list is positioned by row index. These
	 * follow their profile's heading instead, and {@link #renderJvmPane} places them once it knows
	 * where the wrapped text put it.
	 */
	private final List<CopyButton> jvmCopyButtons = new ArrayList<>();
	/**
	 * The advice, built once instead of per frame.
	 *
	 * <p>Each profile allocates lists and formats a dozen strings. Rebuilding that every frame would
	 * make the JVM tab the most allocation-heavy screen in an optimisation mod, so it is recomputed
	 * only when the background hardware probe finally lands.
	 */
	private List<JvmProfile> jvmProfiles = List.of();
	private boolean jvmProfilesFromReadyProbe;

	private CoreTab tab = CoreTab.PERFORMANCE;
	private Component profileStatus = Component.translatable("kohs_core.profile.info");
	private @Nullable CoreSettingsPage siblingPage;

	private double contentScroll;
	private int contentHeight;
	private double railScroll;
	private int railContentHeight;
	private long openedAtMillis = System.nanoTime() / 1_000_000L;

	private int margin;
	private int headerY;
	private int headerHeight;
	private int railX;
	private int railY;
	private int railWidth;
	private int railViewportHeight;
	private int railButtonHeight;
	private boolean railIconOnly;
	private int railGap;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int contentViewportHeight;
	private int descriptionY;
	private int descriptionHeight;
	private int footerY;
	private int footerButtonHeight;
	private int rowHeight;
	private int sliderRowHeight;
	private int rowCursorY;
	private int rowGap;

	public CoreScreen(@Nullable Screen parent) {
		super(Component.translatable("kohs_core.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.controls.clear();
		this.tabButtons.clear();
		this.scrollingWidgets.clear();
		this.jvmCopyButtons.clear();
		this.optionTree.clear();
		this.calculateLayout();
		this.particleField.resize(this.width, this.height);
		this.openedAtMillis = System.nanoTime() / 1_000_000L;

		this.buildRail();
		this.buildContent();
		this.configureDependencies();
		this.buildFooter();
		this.clampScrolls();
	}

	/** Computes every final rectangle from the current logical screen dimensions. */
	private void calculateLayout() {
		int safeWidth = Math.max(1, this.width);
		int safeHeight = Math.max(1, this.height);
		boolean compactWidth = safeWidth < 360;
		boolean compactHeight = safeHeight < 260;

		int preferredMargin = Mth.clamp(Math.min(safeWidth, safeHeight) / 40, 2,
				compactWidth || compactHeight ? 5 : 8);
		this.margin = Math.max(0, Math.min(preferredMargin,
				Math.min(Math.max(0, (safeWidth - 1) / 2), Math.max(0, (safeHeight - 1) / 2))));
		this.headerY = this.margin;
		this.headerHeight = Math.max(1, Math.min(Mth.clamp(safeHeight / 8, 20, PREFERRED_HEADER_HEIGHT),
				safeHeight - this.headerY));
		this.footerButtonHeight = Mth.clamp(safeHeight / 10, 18, 20);
		this.footerButtonHeight = Math.max(1, Math.min(this.footerButtonHeight, safeHeight));
		this.footerY = Math.max(0, safeHeight - this.margin - this.footerButtonHeight);

		int descriptionCandidate = safeHeight >= 300 ? PREFERRED_DESCRIPTION_HEIGHT
				: safeHeight >= 230 ? 34 : 0;
		int contentTop = Mth.clamp(this.headerY + this.headerHeight + this.margin,
				0, Math.max(0, this.footerY - 1));
		int maximumViewportWithoutDescription = Math.max(1, this.footerY - this.margin - contentTop);
		// Four particle controls, the toolbar and one complete 18px preview need roughly this much.
		int minimumUsefulViewport = Math.min(136, maximumViewportWithoutDescription);
		int descriptionRoom = Math.max(0,
				this.footerY - this.margin - contentTop - minimumUsefulViewport - this.margin);
		this.descriptionHeight = Math.min(descriptionCandidate, descriptionRoom);
		this.descriptionY = this.descriptionHeight > 0
				? this.footerY - this.margin - this.descriptionHeight
				: this.footerY;

		int contentBottom = Math.max(contentTop + 1,
				(this.descriptionHeight > 0 ? this.descriptionY - this.margin : this.footerY - this.margin));
		this.railY = contentTop;
		this.contentY = contentTop;
		this.railViewportHeight = Math.max(1, contentBottom - contentTop);
		this.contentViewportHeight = this.railViewportHeight;

		this.railX = this.margin;
		int innerWidth = Math.max(1, safeWidth - this.margin * 2);
		int columnGap = compactWidth ? 3 : this.margin;
		int scrollbarRoom = 4;
		// The longest tab label plus its icon needs about this much before the text starts turning
		// into a stub. Below it, the rail collapses to icons: more legible than three clipped
		// letters, and it hands the width back to the content pane, which needs it more.
		this.railIconOnly = compactWidth && safeWidth < COMPACT_RAIL_THRESHOLD;
		int desiredRailWidth = this.railIconOnly ? ICON_RAIL_WIDTH : PREFERRED_RAIL_WIDTH;
		int reservedContent = Math.min(MIN_CONTENT_WIDTH, Math.max(44, innerWidth / 2));
		int maxRailWidth = Math.max(1, innerWidth - columnGap - scrollbarRoom - reservedContent);
		this.railWidth = Math.max(1, Math.min(desiredRailWidth, maxRailWidth));
		this.contentX = this.railX + this.railWidth + columnGap;
		this.contentWidth = Math.max(1, safeWidth - this.margin - scrollbarRoom - this.contentX);

		this.rowHeight = Mth.clamp(safeHeight / 8, 18, PREFERRED_ROW_HEIGHT);
		// A slider stacks a label over a track; giving it the toggle's height is what made the
		// handle overlap the letters. Keep both bands comfortably separated even at GUI scale 4.
		this.sliderRowHeight = Math.max(32, this.rowHeight + 12);
		this.rowGap = Mth.clamp((safeHeight - 190) / 24, 0, PREFERRED_ROW_GAP);
	}

	// --- Rail --------------------------------------------------------------------------------

	private void buildRail() {
		List<CoreSettingsPage> siblingPages = KoHsCoreClient.settingsPages();
		int total = CoreTab.values().length + siblingPages.size();
		// Try progressively tighter spacing before giving up and scrolling. With eleven tabs a rail
		// that scrolls is a rail where half the navigation is invisible, so it is worth losing a few
		// pixels of padding first.
		this.railGap = this.railViewportHeight < 150 ? 0 : this.railViewportHeight < 180 ? 1 : 3;
		int fittedHeight = (this.railViewportHeight - this.railGap * Math.max(0, total - 1))
				/ Math.max(1, total);
		this.railButtonHeight = Mth.clamp(fittedHeight, 12, 22);
		this.railContentHeight = total * this.railButtonHeight + Math.max(0, total - 1) * this.railGap;
		boolean scrollable = this.railContentHeight > this.railViewportHeight;
		int buttonWidth = Math.max(1, this.railWidth - (scrollable ? 5 : 0));

		int y = this.railY;
		for (CoreTab entry : CoreTab.values()) {
			TabButton button = new TabButton(this.railX, y, buttonWidth, this.railButtonHeight, entry.title(),
					entry.description(), () -> this.tab == entry && this.siblingPage == null,
					pressed -> this.selectTab(entry), 0);
			this.tabButtons.add(button);
			button.setIcon(entry.icon());
			button.setCompact(this.railIconOnly);
			this.addControl(button);
			y += this.railButtonHeight + this.railGap;
		}

		for (CoreSettingsPage page : siblingPages) {
			TabButton button = new TabButton(this.railX, y, buttonWidth, this.railButtonHeight, page.title(),
					page.description(), () -> this.siblingPage == page, pressed -> this.selectSiblingPage(page),
					page.accentColor());
			button.setCompact(this.railIconOnly);
			this.tabButtons.add(button);
			this.addControl(button);
			y += this.railButtonHeight + this.railGap;
		}

		this.clampRailScroll();
		int selectedIndex = this.siblingPage == null ? this.tab.ordinal()
				: CoreTab.values().length + Math.max(0, siblingPages.indexOf(this.siblingPage));
		this.ensureRailIndexVisible(selectedIndex);
		this.layoutRailButtons();
	}

	private void layoutRailButtons() {
		int offset = (int) Math.round(this.railScroll);
		int bottom = this.railY + this.railViewportHeight;
		for (int index = 0; index < this.tabButtons.size(); index++) {
			TabButton button = this.tabButtons.get(index);
			int targetY = this.railY + index * (this.railButtonHeight + this.railGap) - offset;
			button.setY(targetY);
			// Whole-button visibility prevents a partially clipped tab from drawing over header/footer.
			button.visible = targetY >= this.railY && targetY + this.railButtonHeight <= bottom;
		}
	}

	private void ensureRailIndexVisible(int index) {
		if (index < 0 || index >= this.tabButtons.size()) {
			return;
		}
		int top = index * (this.railButtonHeight + this.railGap);
		int bottom = top + this.railButtonHeight;
		if (top < this.railScroll) {
			this.railScroll = top;
		} else if (bottom > this.railScroll + this.railViewportHeight) {
			this.railScroll = bottom - this.railViewportHeight;
		}
		this.clampRailScroll();
	}

	/** Dims a control when another mod owns the domain it writes to. */
	private void applyDomainLock(CoreControl control, @Nullable Domain domain) {
		if (domain == null) {
			return;
		}
		DomainClaim claim = KoHsCoreClient.domainOwner(domain);
		if (claim != null) {
			control.lockTo(Component.literal(claim.ownerName()), Component.translatable(claim.reason()));
		}
	}

	/** Locks a preset that writes several domains as soon as any one of them is reserved. */
	private void applyDomainLocks(CoreControl control, Domain... writtenDomains) {
		for (Domain domain : writtenDomains) {
			DomainClaim claim = KoHsCoreClient.domainOwner(domain);
			if (claim != null) {
				control.lockTo(Component.literal(claim.ownerName()), Component.translatable(claim.reason()));
				return;
			}
		}
	}

	private void selectTab(CoreTab next) {
		this.tab = next;
		this.siblingPage = null;
		this.contentScroll = 0.0;
		this.rebuildWidgets();
	}

	private void selectSiblingPage(CoreSettingsPage page) {
		Screen screen = page.createScreen(this);
		if (screen != null) {
			this.minecraft.setScreen(screen);
			return;
		}
		this.siblingPage = page;
		this.contentScroll = 0.0;
		this.rebuildWidgets();
	}

	// --- Content -----------------------------------------------------------------------------

	private void buildContent() {
		if (this.siblingPage != null) {
			this.buildSiblingOptions(this.siblingPage);
			return;
		}
		switch (this.tab) {
			case PERFORMANCE -> this.buildPerformanceTab();
			case PROFILES -> this.buildProfilesTab();
			case GRAPHICS -> this.buildGraphicsTab();
			case PARTICLES -> this.buildParticlesTab();
			case ANIMATIONS -> this.buildAnimationsTab();
			case SUITE -> this.buildSuiteTab();
			case STREAMING -> this.buildStreamingTab();
			case ENTITIES -> this.buildEntitiesTab();
			case INPUT -> this.buildInputTab();
			case MEMORY -> this.buildMemoryTab();
			case JVM -> this.buildJvmTab();
			// INTEGRATION is a read-only pane drawn directly, with no widgets.
			case INTEGRATION -> this.contentHeight = 0;
		}
	}

	private void configureDependencies() {
		CoreConfig config = ConfigStore.get();
		Minecraft minecraft = Minecraft.getInstance();
		for (AbstractWidget widget : this.scrollingWidgets) {
			if (!(widget.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)) continue;
			String key = text.getKey();
			java.util.function.BooleanSupplier condition = switch (key) {
				case "kohs_core.option.perf_hud_corner", "kohs_core.option.perf_hud_detail" -> () -> config.performanceHudEnabled;
				case "kohs_core.option.gui_density" -> () -> config.guiParticles;
				case "kohs_core.option.typewriter_speed" -> () -> config.typewriterDescriptions;
				case "kohs_core.option.sharp_radius" -> () -> config.extremeQualityTier == 2;
				case "options.renderCloudsDistance" -> () -> minecraft.options.cloudStatus().get() != net.minecraft.client.CloudStatus.OFF;
				case "options.maxAnisotropy" -> () -> minecraft.options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC;
				case "options.exclusiveFullscreen", "options.fullscreen.resolution" -> () -> minecraft.getWindow().isFullscreen();
				case "kohs_core.option.entity_distance", "kohs_core.option.item_distance" -> () -> config.entityCullingEnabled;
				case "kohs_core.option.heap_threshold" -> () -> config.memoryManagementEnabled && config.particleAdaptiveBudget;
				case "kohs_core.option.adaptive_budget", "kohs_core.option.particle_budget" ->
						this.tab == CoreTab.MEMORY ? () -> config.memoryManagementEnabled : null;
				default -> null;
			};
			int depth = 1;
			if (key.startsWith("kohs_core.animation.") && !key.equals("kohs_core.animation.master")) {
				condition = () -> config.animationsEnabled;
				String suffix = key.substring("kohs_core.animation.".length());
				if (List.of("fire", "water", "lava", "portal", "vegetation", "redstone", "other_textures", "interval").contains(suffix)) {
					condition = () -> config.animationsEnabled && !Boolean.FALSE.equals(config.animationCategories.get("textures"));
					depth = 2;
				}
			}
			if (key.startsWith("kohs_core.streaming.") && !key.endsWith(".master")) condition = () -> config.streamerMode;
			if (condition != null) this.optionTree.depend(widget, condition, depth);
		}
	}

	private void buildProfilesTab() {
		this.rowCursorY = this.contentY;
		int cardHeight = Math.max(22, Math.min(52, this.contentViewportHeight / 2));
		for (GameProfile profile : GameProfile.values()) {
			this.addRow(new ProfileButton(this.contentX, this.nextRowY(cardHeight), this.contentWidth,
					cardHeight, profile.title(), profile.description(), () -> this.applyProfile(profile)));
		}
		ProfileButton undo = new ProfileButton(this.contentX, this.nextRowY(cardHeight), this.contentWidth,
				cardHeight, Component.translatable("kohs_core.profile.undo"),
				Component.translatable("kohs_core.profile.undo.description"), () -> this.applyProfile(null));
		undo.active = ProfileManager.canUndo();
		this.addRow(undo);
		var status = new dev.zymekoh.kohscore.gui.widget.InfoRow(this.contentX, this.nextRowY(66),
				this.contentWidth, this.profileStatus);
		status.setDescription(this.profileStatus);
		status.setTooltip(net.minecraft.client.gui.components.Tooltip.create(this.profileStatus));
		this.addRow(status);
		this.finishRows();
	}

	private void applyProfile(@Nullable GameProfile profile) {
		try {
			var result = profile == null ? ProfileManager.undo() : ProfileManager.apply(profile);
			this.profileStatus = Component.translatable(profile == null ? "kohs_core.profile.restored"
					: "kohs_core.profile.applied", result.changed(), result.skipped());
		} catch (RuntimeException failure) {
			dev.zymekoh.kohscore.KoHsCore.LOGGER.error("Could not apply/undo the graphics profile", failure);
			this.profileStatus = Component.translatable("kohs_core.profile.failed");
		}
		this.rebuildWidgets();
	}

	private void buildSuiteTab() {
		this.rowCursorY = this.contentY;
		for (var entry : dev.zymekoh.kohscore.orchestration.SuiteCatalog.discover()) {
			this.addRow(new dev.zymekoh.kohscore.gui.widget.SuiteCard(this.contentX, this.nextRowY(76),
					this.contentWidth, entry, this));
		}
		this.finishRows();
	}

	private void buildAnimationsTab() {
		this.rowCursorY = this.contentY;
		var config = ConfigStore.get();
		this.toggle(Component.translatable("kohs_core.animation.master"),
				Component.translatable("kohs_core.animation.master.description"),
				() -> config.animationsEnabled, value -> config.animationsEnabled = value, null);
		this.animationToggle("textures", Domain.ANIMATED_TEXTURES);
		for (String category : List.of("fire", "water", "lava", "portal", "vegetation", "redstone", "other_textures"))
			this.animationToggle(category, Domain.ANIMATED_TEXTURES);
		this.slider(Component.translatable("kohs_core.animation.interval"),
				Component.translatable("kohs_core.animation.interval.description"), 1, 20, 1,
				() -> config.animatedTextureInterval, value -> config.animatedTextureInterval = (int) Math.round(value),
				value -> Component.literal("1 / " + Math.round(value) + " ticks"), Domain.ANIMATED_TEXTURES);
		this.animationToggle("entities", Domain.ENTITY_ANIMATIONS);
		this.animationToggle("items", Domain.ENTITY_ANIMATIONS);
		this.animationToggle("blocks", Domain.BLOCK_ANIMATIONS);
		this.animationToggle("clouds", Domain.CLOUDS);
		this.animationToggle("hands", Domain.HAND_ANIMATIONS);
		this.addRow(new dev.zymekoh.kohscore.gui.widget.AnimationPreviewRow(this.contentX,
				this.nextRowY(66), this.contentWidth));
		this.finishRows();
	}

	private void animationToggle(String category, Domain domain) {
		var config = ConfigStore.get();
		this.toggle(Component.translatable("kohs_core.animation." + category),
				Component.translatable("kohs_core.animation." + category + ".description"),
				() -> !Boolean.FALSE.equals(config.animationCategories.get(category)),
				value -> { if (value) config.animationCategories.remove(category); else config.animationCategories.put(category, false); }, domain);
	}

	private void buildStreamingTab() {
		this.rowCursorY = this.contentY;
		// The existing streaming module supplies its own UI when installed; do not expose unwired switches.
		this.addRow(new dev.zymekoh.kohscore.gui.widget.InfoRow(this.contentX, this.nextRowY(66), this.contentWidth,
				Component.translatable("kohs_core.streaming.info")));
		this.finishRows();
	}

	/**
	 * The JVM pane is drawn immediately rather than as rows, but its copy buttons are still real
	 * widgets so they can be focused, narrated and hovered like everything else. They are created
	 * here and positioned later, once {@link #renderJvmPane} has laid the wrapped text out.
	 */
	private void buildJvmTab() {
		this.contentHeight = 0;
		this.refreshJvmProfiles();
		for (JvmProfile profile : this.jvmProfiles) {
			CopyButton button = new CopyButton(this.contentX, this.contentY, COPY_BUTTON_WIDTH,
					COPY_BUTTON_HEIGHT,
					Component.translatable("kohs_core.jvm.copy.description",
							Component.translatable(profile.goal().translationKey())),
					profile::commandLine);
			button.visible = false;
			this.jvmCopyButtons.add(button);
			this.addControl(button);
		}
	}

	/**
	 * Rebuilds the advice when the probe result changes from the fallback to the real reading.
	 *
	 * @return {@code true} when the profile list was replaced
	 */
	private boolean refreshJvmProfiles() {
		boolean ready = HardwareProbe.isReady();
		if (!this.jvmProfiles.isEmpty() && this.jvmProfilesFromReadyProbe == ready) {
			return false;
		}
		this.jvmProfiles = JvmAdvisor.allProfiles(HardwareProbe.get());
		this.jvmProfilesFromReadyProbe = ready;
		return true;
	}

	/** Adds a control at the next row position and tracks it for scrolling and descriptions. */
	private int rowCursor;

	private <T extends CoreControl> T addRow(T control) {
		this.controls.add(control);
		this.scrollingWidgets.add(control);
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(control);
		return control;
	}

	private void addControl(CoreControl control) {
		this.controls.add(control);
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(control);
	}

	/**
	 * Reserves vertical space for one row and returns its top edge.
	 *
	 * <p>Rows are no longer a uniform height. A toggle needs one line of text and a switch beside it;
	 * a slider needs the label and the track on separate bands, or the handle lands on top of the
	 * letters. The cursor therefore accumulates real heights instead of multiplying by a constant.
	 */
	private int nextRowY(int height) {
		int y = this.rowCursorY;
		this.rowCursorY += height + this.rowGap;
		this.rowCursor++;
		return y;
	}

	/** Kept for callers that lay out a plain button at the standard row height. */
	private int nextRowY() {
		return this.nextRowY(this.rowHeight);
	}

	private CoreToggle toggle(Component label, Component description, java.util.function.BooleanSupplier getter,
			java.util.function.Consumer<Boolean> setter, @Nullable Domain domain) {
		CoreToggle control = new CoreToggle(this.contentX, this.nextRowY(), this.contentWidth, this.rowHeight, label,
				description, getter, value -> {
					setter.accept(value);
					ConfigStore.save();
				});
		this.applyDomainLock(control, domain);
		return this.addRow(control);
	}

	private CoreSlider slider(Component label, Component description, double min, double max, double step,
			java.util.function.DoubleSupplier getter, java.util.function.Consumer<Double> setter,
			java.util.function.DoubleFunction<Component> formatter, @Nullable Domain domain) {
		CoreSlider control = new CoreSlider(this.contentX, this.nextRowY(this.sliderRowHeight), this.contentWidth,
				this.sliderRowHeight, label,
				description, min, max, step, getter, value -> {
					setter.accept(value);
					ConfigStore.save();
				}, formatter);
		this.applyDomainLock(control, domain);
		return this.addRow(control);
	}

	/**
	 * A slider whose bounds come from the vanilla option itself rather than from a guess.
	 *
	 * <p>This matters more than it looks. {@code OptionInstance.set} does not clamp an out-of-range
	 * value — it rejects it, logs an error and substitutes the option's <em>default</em>. So a
	 * hardcoded maximum that is too high does not merely fail to apply; it silently throws away
	 * whatever the player had. View distance is the live example: vanilla caps it at 16 instead of
	 * 32 whenever the heap is under a gigabyte, which is exactly the machine this mod exists for.
	 *
	 * @param fallbackMin used only when the option does not expose an integer range
	 */
	private CoreSlider vanillaIntSlider(Component label, Component description,
			net.minecraft.client.OptionInstance<Integer> option, int fallbackMin, int fallbackMax,
			java.util.function.DoubleFunction<Component> formatter, @Nullable Domain domain) {
		int min = fallbackMin;
		int max = fallbackMax;
		if (option.values() instanceof net.minecraft.client.OptionInstance.IntRange range) {
			min = range.minInclusive();
			max = range.maxInclusive();
		} else if (option.values()
				instanceof net.minecraft.client.OptionInstance.ClampingLazyMaxIntRange range) {
			min = range.minInclusive();
			max = range.maxInclusive();
		}
		return this.slider(label, description, min, max, 1, () -> option.get(),
				value -> option.set((int) Math.round(value)), formatter, domain);
	}

	private void finishRows() {
		this.contentHeight = Math.max(0, this.rowCursorY - this.contentY);
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;

		// Only the final row of the column skips its separator.
		CoreControl last = null;
		for (AbstractWidget widget : this.scrollingWidgets) {
			if (widget instanceof CoreControl control) {
				control.setLastRow(false);
				last = control;
			}
		}
		if (last != null) {
			last.setLastRow(true);
		}
	}

	private void buildPerformanceTab() {
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
		CoreConfig config = ConfigStore.get();
		Minecraft minecraft = Minecraft.getInstance();

		this.toggle(Component.translatable("kohs_core.option.adaptive_budget"),
				Component.translatable("kohs_core.option.adaptive_budget.description"),
				() -> config.particleAdaptiveBudget, value -> config.particleAdaptiveBudget = value,
				Domain.MEMORY);

		this.slider(Component.translatable("options.framerateLimit"),
				Component.translatable("kohs_core.option.framerate.description"),
				10, 260, 10, () -> minecraft.options.framerateLimit().get(),
				value -> minecraft.options.framerateLimit().set((int) Math.round(value)),
				value -> value >= 260 ? Component.translatable("options.framerateLimit.max")
						: Component.literal(Math.round(value) + " FPS"),
				Domain.FRAME_PACING);

		this.toggle(Component.translatable("options.vsync"),
				Component.translatable("kohs_core.option.vsync.description"),
				() -> minecraft.options.enableVsync().get(),
				value -> minecraft.options.enableVsync().set(value), Domain.FRAME_PACING);

		this.slider(Component.translatable("options.inactivityFpsLimit"),
				Component.translatable("kohs_core.option.inactivity_fps.description"),
				0, InactivityFpsLimit.values().length - 1, 1,
				() -> minecraft.options.inactivityFpsLimit().get().ordinal(),
				value -> minecraft.options.inactivityFpsLimit()
						.set(InactivityFpsLimit.values()[(int) Math.round(value)]),
				value -> InactivityFpsLimit.values()[(int) Math.round(value)].caption(),
				Domain.FRAME_PACING);

		this.slider(Component.translatable("options.prioritizeChunkUpdates"),
				Component.translatable("kohs_core.option.chunk_priority.description"),
				0, PrioritizeChunkUpdates.values().length - 1, 1,
				() -> minecraft.options.prioritizeChunkUpdates().get().ordinal(),
				value -> minecraft.options.prioritizeChunkUpdates()
						.set(PrioritizeChunkUpdates.values()[(int) Math.round(value)]),
				value -> PrioritizeChunkUpdates.values()[(int) Math.round(value)].caption(),
				Domain.VIEW_DISTANCE);

		this.slider(Component.translatable("options.chunkFade"),
				Component.translatable("kohs_core.option.chunk_fade.description"),
				0.0, 2.0, 0.05, () -> minecraft.options.chunkSectionFadeInTime().get(),
				value -> minecraft.options.chunkSectionFadeInTime().set(value),
				value -> value <= 0.0 ? Component.translatable("options.off")
						: Component.literal(String.format(Locale.ROOT, "%.2fs", value)),
				Domain.VIEW_DISTANCE);

		this.toggle(Component.translatable("options.autosaveIndicator"),
				Component.translatable("kohs_core.option.autosave_indicator.description"),
				() -> minecraft.options.showAutosaveIndicator().get(),
				value -> minecraft.options.showAutosaveIndicator().set(value), null);

		this.slider(Component.translatable("options.accessibility.menu_background_blurriness"),
				Component.translatable("kohs_core.option.menu_blur.description"),
				0, 10, 1, () -> minecraft.options.menuBackgroundBlurriness().get(),
				value -> minecraft.options.menuBackgroundBlurriness().set((int) Math.round(value)),
				value -> value <= 0 ? Component.translatable("options.off")
						: Component.literal(String.valueOf(Math.round(value))), Domain.GRAPHICS_QUALITY);

		this.toggle(Component.translatable("kohs_core.option.perf_hud"),
				Component.translatable("kohs_core.option.perf_hud.description"),
				() -> config.performanceHudEnabled, value -> config.performanceHudEnabled = value, null);

		this.slider(Component.translatable("kohs_core.option.perf_hud_corner"),
				Component.translatable("kohs_core.option.perf_hud_corner.description"),
				0, 3, 1, () -> config.performanceHudCorner,
				value -> config.performanceHudCorner = (int) Math.round(value),
				value -> Component.translatable("kohs_core.corner." + (int) Math.round(value)), null);

		this.slider(Component.translatable("kohs_core.option.perf_hud_detail"),
				Component.translatable("kohs_core.option.perf_hud_detail.description"),
				0, 2, 1, () -> config.performanceHudDetail,
				value -> config.performanceHudDetail = (int) Math.round(value),
				value -> Component.translatable("kohs_core.detail." + (int) Math.round(value)), null);

		this.toggle(Component.translatable("kohs_core.option.gui_particles"),
				Component.translatable("kohs_core.option.gui_particles.description"),
				() -> config.guiParticles, value -> {
					config.guiParticles = value;
					this.particleField.resize(this.width, this.height);
				}, null);

		this.slider(Component.translatable("kohs_core.option.gui_density"),
				Component.translatable("kohs_core.option.gui_density.description"),
				0.1, 2.0, 0.1, () -> config.guiParticleDensity, value -> {
					config.guiParticleDensity = value.floatValue();
					this.particleField.resize(this.width, this.height);
				}, value -> Component.literal(String.format(Locale.ROOT, "%.1fx", value)), null);

		this.toggle(Component.translatable("kohs_core.option.typewriter"),
				Component.translatable("kohs_core.option.typewriter.description"),
				() -> config.typewriterDescriptions, value -> config.typewriterDescriptions = value, null);

		this.slider(Component.translatable("kohs_core.option.typewriter_speed"),
				Component.translatable("kohs_core.option.typewriter_speed.description"),
				0.1, 40.0, 0.1, () -> config.typewriterMillisPerChar,
				value -> config.typewriterMillisPerChar = value.floatValue(),
				value -> Component.literal(String.format(Locale.ROOT, "%.1f ms", value)), null);


		this.toggle(Component.translatable("kohs_core.option.lightmap_throttle"),
				Component.translatable("kohs_core.option.lightmap_throttle.description"),
				() -> config.lightmapThrottle, value -> config.lightmapThrottle = value, Domain.LIGHTMAP);

		this.toggle(Component.translatable("kohs_core.option.background_cpu"),
				Component.translatable("kohs_core.option.background_cpu.description"),
				() -> config.backgroundCpuThrottle, value -> config.backgroundCpuThrottle = value,
				Domain.BACKGROUND_CPU);

		this.toggle(Component.translatable("kohs_core.option.skip_empty_passes"),
				Component.translatable("kohs_core.option.skip_empty_passes.description"),
				() -> config.skipEmptyRenderPasses, value -> config.skipEmptyRenderPasses = value, null);

		this.finishRows();
	}

	private void buildGraphicsTab() {
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
		CoreConfig config = ConfigStore.get();
		Minecraft minecraft = Minecraft.getInstance();

		CoreSlider qualityTier = this.slider(Component.translatable("kohs_core.option.quality_tier"),
				Component.translatable("kohs_core.option.quality_tier.description"),
				0, 3, 1, () -> config.extremeQualityTier, value -> {
					config.extremeQualityTier = (int) Math.round(value);
					KoHsCoreClient.graphicsGovernor().apply();
				}, value -> Component.translatable("kohs_core.tier." + (int) Math.round(value)),
				null);
		this.applyDomainLocks(qualityTier, Domain.GRAPHICS_QUALITY, Domain.VIEW_DISTANCE, Domain.CLOUDS,
				Domain.WEATHER, Domain.ENTITY_CULLING, Domain.PARTICLES);

		this.slider(Component.translatable("kohs_core.option.sharp_radius"),
				Component.translatable("kohs_core.option.sharp_radius.description"),
				2, 6, 1, () -> config.sharpRadiusChunks, value -> {
					config.sharpRadiusChunks = (int) Math.round(value);
					if (config.extremeQualityTier == 2) {
						KoHsCoreClient.graphicsGovernor().apply();
					}
				}, value -> Component.literal(Math.round(value) + " chunks"), Domain.VIEW_DISTANCE);

		CoreSlider vanillaPreset = this.slider(Component.translatable("options.graphics"),
				Component.translatable("kohs_core.option.vanilla_preset.description"),
				0, GraphicsPreset.values().length - 1, 1,
				() -> minecraft.options.graphicsPreset().get().ordinal(), value -> {
					if (config.extremeQualityTier > 0) {
						config.extremeQualityTier = 0;
						KoHsCoreClient.graphicsGovernor().apply();
					}
					minecraft.options.graphicsPreset().set(GraphicsPreset.values()[(int) Math.round(value)]);
					KoHsCoreClient.graphicsGovernor().markTextureReloadRequired();
				}, value -> Component.translatable(GraphicsPreset.values()[(int) Math.round(value)].getKey()), null);
		this.applyDomainLocks(vanillaPreset, Domain.GRAPHICS_QUALITY, Domain.VIEW_DISTANCE, Domain.CLOUDS,
				Domain.WEATHER, Domain.ENTITY_CULLING, Domain.PARTICLES);

		this.vanillaIntSlider(Component.translatable("options.renderDistance"),
				Component.translatable("kohs_core.option.render_distance.description"),
				minecraft.options.renderDistance(), 2, 32,
				value -> Component.literal(Math.round(value) + " chunks"), Domain.VIEW_DISTANCE);

		this.vanillaIntSlider(Component.translatable("options.simulationDistance"),
				Component.translatable("kohs_core.option.simulation_distance.description"),
				minecraft.options.simulationDistance(), 5, 32,
				value -> Component.literal(Math.round(value) + " chunks"), Domain.VIEW_DISTANCE);

		this.slider(Component.translatable("options.clouds"),
				Component.translatable("kohs_core.option.clouds.description"),
				0, 2, 1, () -> minecraft.options.cloudStatus().get().ordinal(),
				value -> minecraft.options.cloudStatus()
						.set(net.minecraft.client.CloudStatus.values()[(int) Math.round(value)]),
				value -> net.minecraft.client.CloudStatus.values()[(int) Math.round(value)].caption(), Domain.CLOUDS);

		this.vanillaIntSlider(Component.translatable("options.renderCloudsDistance"),
				Component.translatable("kohs_core.option.cloud_range.description"),
				minecraft.options.cloudRange(), 2, 128,
				value -> Component.literal(Math.round(value) + " chunks"), Domain.CLOUDS);

		this.slider(Component.translatable("options.weatherRadius"),
				Component.translatable("kohs_core.option.weather_radius.description"),
				3, 10, 1, () -> minecraft.options.weatherRadius().get(),
				value -> minecraft.options.weatherRadius().set((int) Math.round(value)),
				value -> Component.literal(Math.round(value) + " blocks"), Domain.WEATHER);

		this.slider(Component.translatable("options.biomeBlendRadius"),
				Component.translatable("kohs_core.option.biome_blend.description"),
				0, 7, 1, () -> minecraft.options.biomeBlendRadius().get(),
				value -> minecraft.options.biomeBlendRadius().set((int) Math.round(value)),
				value -> Component.literal((Math.round(value) * 2 + 1) + "x" + (Math.round(value) * 2 + 1)),
				Domain.GRAPHICS_QUALITY);

		this.slider(Component.translatable("options.mipmapLevels"),
				Component.translatable("kohs_core.option.mipmap.description"),
				0, 4, 1, () -> minecraft.options.mipmapLevels().get(), value -> {
					minecraft.options.mipmapLevels().set((int) Math.round(value));
					KoHsCoreClient.graphicsGovernor().markTextureReloadRequired();
				}, value -> value <= 0 ? Component.translatable("options.off")
						: Component.literal(String.valueOf(Math.round(value))), Domain.GRAPHICS_QUALITY);

		this.slider(Component.translatable("options.textureFiltering"),
				Component.translatable("kohs_core.option.texture_filtering.description"),
				0, TextureFilteringMethod.values().length - 1, 1,
				() -> minecraft.options.textureFiltering().get().ordinal(), value -> {
					minecraft.options.textureFiltering()
							.set(TextureFilteringMethod.values()[(int) Math.round(value)]);
					KoHsCoreClient.graphicsGovernor().markTextureReloadRequired();
				}, value -> TextureFilteringMethod.values()[(int) Math.round(value)].caption(),
				Domain.GRAPHICS_QUALITY);

		this.slider(Component.translatable("options.maxAnisotropy"),
				Component.translatable("kohs_core.option.anisotropy.description"),
				1, 3, 1, () -> minecraft.options.maxAnisotropyBit().get(), value -> {
					minecraft.options.maxAnisotropyBit().set((int) Math.round(value));
					KoHsCoreClient.graphicsGovernor().markTextureReloadRequired();
				}, value -> Component.literal((1 << (int) Math.round(value)) + "x"),
				Domain.GRAPHICS_QUALITY);

		this.toggle(Component.translatable("options.cutoutLeaves"),
				Component.translatable("kohs_core.option.cutout_leaves.description"),
				() -> minecraft.options.cutoutLeaves().get(),
				value -> minecraft.options.cutoutLeaves().set(value), Domain.GRAPHICS_QUALITY);

		this.toggle(Component.translatable("options.improvedTransparency"),
				Component.translatable("kohs_core.option.transparency.description"),
				() -> minecraft.options.improvedTransparency().get(),
				value -> minecraft.options.improvedTransparency().set(value), Domain.GRAPHICS_QUALITY);

		this.toggle(Component.translatable("options.entityShadows"),
				Component.translatable("kohs_core.option.entity_shadows.description"),
				() -> minecraft.options.entityShadows().get(),
				value -> minecraft.options.entityShadows().set(value), Domain.GRAPHICS_QUALITY);

		this.toggle(Component.translatable("options.ao"),
				Component.translatable("kohs_core.option.ambient_occlusion.description"),
				() -> minecraft.options.ambientOcclusion().get(),
				value -> minecraft.options.ambientOcclusion().set(value), Domain.GRAPHICS_QUALITY);

		this.toggle(Component.translatable("options.vignette"),
				Component.translatable("kohs_core.option.vignette.description"),
				() -> minecraft.options.vignette().get(),
				value -> minecraft.options.vignette().set(value), Domain.SCREEN_EFFECTS);

		this.toggle(Component.translatable("options.viewBobbing"),
				Component.translatable("kohs_core.option.view_bobbing.description"),
				() -> minecraft.options.bobView().get(),
				value -> minecraft.options.bobView().set(value), Domain.SCREEN_EFFECTS);

		this.slider(Component.translatable("options.gamma"),
				Component.translatable("kohs_core.option.gamma.description"),
				0.0, 1.0, 0.05, () -> minecraft.options.gamma().get(),
				value -> minecraft.options.gamma().set(value),
				value -> Component.literal(Math.round(value * 100.0) + "%"), Domain.SCREEN_EFFECTS);

		this.toggle(Component.translatable("options.fullscreen"),
				Component.translatable("kohs_core.option.fullscreen.description"),
				() -> minecraft.options.fullscreen().get(),
				value -> minecraft.options.fullscreen().set(value), null);

		this.toggle(Component.translatable("options.exclusiveFullscreen"),
				Component.translatable("kohs_core.option.exclusive_fullscreen.description"),
				() -> minecraft.options.exclusiveFullscreen().get(),
				value -> minecraft.options.exclusiveFullscreen().set(value), null);

		int maxGuiScale = Math.max(1, minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode()));
		this.slider(Component.translatable("options.guiScale"),
				Component.translatable("kohs_core.option.gui_scale.description"),
				0, maxGuiScale, 1, () -> minecraft.options.guiScale().get(),
				value -> minecraft.options.guiScale().set((int) Math.round(value)),
				value -> value <= 0 ? Component.translatable("options.guiScale.auto")
						: Component.literal(String.valueOf(Math.round(value))), null);

		this.slider(Component.translatable("options.attackIndicator"),
				Component.translatable("kohs_core.option.attack_indicator.description"),
				0, AttackIndicatorStatus.values().length - 1, 1,
				() -> minecraft.options.attackIndicator().get().ordinal(),
				value -> minecraft.options.attackIndicator()
						.set(AttackIndicatorStatus.values()[(int) Math.round(value)]),
				value -> AttackIndicatorStatus.values()[(int) Math.round(value)].caption(),
				Domain.HOTBAR_EFFECTS);

		this.buildFullscreenResolutionControl(minecraft);

		this.finishRows();
	}

	private void buildFullscreenResolutionControl(Minecraft minecraft) {
		Window window = minecraft.getWindow();
		Monitor monitor = window.findBestMonitor();
		if (monitor == null) {
			return;
		}
		this.slider(Component.translatable("options.fullscreen.resolution"),
				Component.translatable("kohs_core.option.fullscreen_resolution.description"),
				-1, monitor.getModeCount() - 1, 1,
				() -> window.getPreferredFullscreenVideoMode().map(monitor::getVideoModeIndex).orElse(-1),
				value -> {
					int index = (int) Math.round(value);
					window.setPreferredFullscreenVideoMode(index < 0
							? Optional.empty() : Optional.of(monitor.getMode(index)));
				}, value -> {
					int index = (int) Math.round(value);
					if (index < 0) {
						return Component.translatable("options.fullscreen.current");
					}
					VideoMode mode = monitor.getMode(index);
					return Component.literal(mode.getWidth() + "x" + mode.getHeight() + " @ "
							+ mode.getRefreshRate() + "Hz");
				}, null);
	}

	private void buildParticlesTab() {
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
		CoreConfig config = ConfigStore.get();
		boolean locked = !KoHsCoreClient.ownsDomain(Domain.PARTICLES);
		this.particlePane.setLocked(locked);

		this.toggle(Component.translatable("kohs_core.option.particles_off"),
				Component.translatable("kohs_core.option.particles_off.description"),
				() -> config.particlesDisabledGlobally, value -> {
					config.particlesDisabledGlobally = value;
					if (value && !PriorityCompatibility.hasReservedParticles()) {
						Minecraft.getInstance().particleEngine.clearParticles();
					}
				},
				Domain.PARTICLES);

		this.slider(Component.translatable("options.particles"),
				Component.translatable("kohs_core.option.vanilla_particles.description"),
				0, ParticleStatus.values().length - 1, 1,
				() -> Minecraft.getInstance().options.particles().get().ordinal(),
				value -> Minecraft.getInstance().options.particles()
						.set(ParticleStatus.values()[(int) Math.round(value)]),
				value -> ParticleStatus.values()[(int) Math.round(value)].caption(), Domain.PARTICLES);

		this.slider(Component.translatable("kohs_core.option.particle_budget"),
				Component.translatable("kohs_core.option.particle_budget.description"),
				0, 4000, 50, () -> config.particleAllowancePerSecond,
				value -> config.particleAllowancePerSecond = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.unlimited")
						: Component.literal(Math.round(value) + "/s"),
				Domain.PARTICLES);

		this.slider(Component.translatable("kohs_core.option.particle_distance"),
				Component.translatable("kohs_core.option.particle_distance.description"),
				0, 256, 8, () -> config.particleCullDistance,
				value -> config.particleCullDistance = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.unlimited")
						: Component.literal(Math.round(value) + " blocks"),
				Domain.PARTICLES);

		// A preset row rather than a third toolbar button: the toolbar's widths are tuned for two,
		// and this belongs with the settings above it anyway.
		Button ambientPreset = Button
				.builder(Component.translatable("kohs_core.particles.ambient_off"),
						pressed -> this.particlePane.applyAmbientPreset())
				.bounds(this.contentX, this.nextRowY(), this.contentWidth, this.rowHeight)
				.build();
		ambientPreset.setTooltip(net.minecraft.client.gui.components.Tooltip
				.create(Component.translatable("kohs_core.particles.ambient_off.tooltip")));
		ambientPreset.active = KoHsCoreClient.ownsDomain(Domain.PARTICLES);
		this.scrollingWidgets.add(ambientPreset);
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(ambientPreset);

		// Where the accumulating cursor actually landed, not an assumed uniform stride: the sliders
		// above are taller than a toggle, so multiplying would put the toolbar on top of them.
		int searchY = this.rowCursorY;

		// Two buttons that act on whatever the search box is currently showing, which makes
		// "disable every dripping particle" a two-step job instead of a scroll marathon.
		int toolbarWidth = Math.max(1, this.contentWidth);
		int toolbarGap = toolbarWidth < 150 ? 2 : 4;
		int buttonHeight = Math.min(18, this.rowHeight);
		boolean stackToolbar = toolbarWidth < 96;
		int searchX = this.contentX;
		int searchWidth;
		int buttonY;
		int buttonWidth;
		int enableX;
		int disableX;
		int toolbarSpan;
		if (stackToolbar) {
			searchWidth = toolbarWidth;
			buttonY = searchY + 18;
			buttonWidth = Math.max(1, (toolbarWidth - toolbarGap) / 2);
			enableX = this.contentX;
			disableX = this.contentX + buttonWidth + toolbarGap;
			toolbarSpan = 18 + buttonHeight + 2;
		} else {
			int minimumSearchWidth = Math.min(52, Math.max(28, toolbarWidth / 4));
			buttonWidth = Math.max(1,
					Math.min(58, (toolbarWidth - minimumSearchWidth - toolbarGap * 2) / 2));
			searchWidth = Math.max(1, toolbarWidth - buttonWidth * 2 - toolbarGap * 2);
			buttonY = searchY;
			enableX = searchX + searchWidth + toolbarGap;
			disableX = enableX + buttonWidth + toolbarGap;
			toolbarSpan = this.height < 230 ? 19 : 24;
		}

		Button enableAll = Button
				.builder(Component.translatable("kohs_core.particles.all_on"),
						pressed -> this.particlePane.setAllVisible(true))
				.bounds(enableX, buttonY, buttonWidth, buttonHeight)
				.build();
		Button disableAll = Button
				.builder(Component.translatable("kohs_core.particles.all_off"),
						pressed -> this.particlePane.setAllVisible(false))
				.bounds(disableX, buttonY, buttonWidth, buttonHeight)
				.build();
		enableAll.active = !locked;
		disableAll.active = !locked;
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(enableAll);
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(disableAll);

		EditBox search = this.particlePane.createSearchBox(this.font, searchX, searchY + 1, searchWidth);
		search.setEditable(!locked);
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(search);

		int listY = searchY + toolbarSpan;
		int listInset = this.contentWidth >= 80 ? 2 : 0;
		this.particlePane.setBounds(this.contentX + listInset, listY,
				Math.max(1, this.contentWidth - listInset * 2),
				Math.max(1, this.contentY + this.contentViewportHeight - listY));

		// The pane scrolls itself, so the outer column has nothing left to scroll.
		this.contentHeight = 0;
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
	}

	private void buildEntitiesTab() {
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
		CoreConfig config = ConfigStore.get();

		this.toggle(Component.translatable("kohs_core.option.entity_culling"),
				Component.translatable("kohs_core.option.entity_culling.description"),
				() -> config.entityCullingEnabled, value -> config.entityCullingEnabled = value,
				Domain.ENTITY_CULLING);

		this.slider(Component.translatable("kohs_core.option.entity_distance"),
				Component.translatable("kohs_core.option.entity_distance.description"),
				0, 256, 8, () -> config.entityCullDistance,
				value -> config.entityCullDistance = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.vanilla")
						: Component.literal(Math.round(value) + " blocks"),
				Domain.ENTITY_CULLING);

		Minecraft minecraft = Minecraft.getInstance();
		this.slider(Component.translatable("kohs_core.option.item_distance"),
				Component.translatable("kohs_core.option.item_distance.description"),
				0, 256, 8, () -> config.itemCullDistance,
				value -> config.itemCullDistance = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.same_as_entities")
						: Component.literal(Math.round(value) + " blocks"),
				Domain.ENTITY_CULLING);

		this.slider(Component.translatable("kohs_core.option.text_distance"),
				Component.translatable("kohs_core.option.text_distance.description"),
				0, 256, 8, () -> config.textCullDistance,
				value -> config.textCullDistance = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.vanilla")
						: Component.literal(Math.round(value) + " blocks"),
				Domain.ENTITY_CULLING);

		this.slider(Component.translatable("kohs_core.option.block_entity_distance"),
				Component.translatable("kohs_core.option.block_entity_distance.description"),
				0, 256, 8, () -> config.blockEntityCullDistance,
				value -> config.blockEntityCullDistance = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.vanilla")
						: Component.literal(Math.round(value) + " blocks"),
				Domain.ENTITY_CULLING);

		this.toggle(Component.translatable("kohs_core.option.block_entity_adaptive"),
				Component.translatable("kohs_core.option.block_entity_adaptive.description"),
				() -> config.blockEntityAdaptiveDistance,
				value -> config.blockEntityAdaptiveDistance = value, Domain.ENTITY_CULLING);

		this.slider(Component.translatable("options.entityDistanceScaling"),
				Component.translatable("kohs_core.option.entity_scaling.description"),
				0.5, 5.0, 0.25, () -> minecraft.options.entityDistanceScaling().get(),
				value -> minecraft.options.entityDistanceScaling().set(value),
				value -> Component.literal(String.format(Locale.ROOT, "%.0f%%", value * 100.0)),
				Domain.ENTITY_CULLING);


		this.slider(Component.translatable("kohs_core.option.nametag_distance"),
				Component.translatable("kohs_core.option.nametag_distance.description"),
				0, 128, 4, () -> config.nametagDistance,
				value -> config.nametagDistance = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.vanilla")
						: Component.literal(Math.round(value) + " blocks"),
				Domain.ENTITY_CULLING);

		this.toggle(Component.translatable("kohs_core.option.hide_player_nametags"),
				Component.translatable("kohs_core.option.hide_player_nametags.description"),
				() -> config.hidePlayerNametags, value -> config.hidePlayerNametags = value,
				Domain.ENTITY_CULLING);


		this.toggle(Component.translatable("kohs_core.option.particle_state_cache"),
				Component.translatable("kohs_core.option.particle_state_cache.description"),
				() -> config.particleStateCache, value -> config.particleStateCache = value, Domain.PARTICLES);

		this.finishRows();
	}

	private void buildInputTab() {
		CoreConfig config = ConfigStore.get();
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
		Minecraft minecraft = Minecraft.getInstance();

		this.toggle(Component.translatable("kohs_core.option.raw_input"),
				Component.translatable("kohs_core.option.raw_input.description"),
				() -> minecraft.options.rawMouseInput().get(),
				value -> minecraft.options.rawMouseInput().set(value), Domain.INPUT_LATENCY);

		this.toggle(Component.translatable("kohs_core.option.smooth_scroll"),
				Component.translatable("kohs_core.option.smooth_scroll.description"),
				() -> !minecraft.options.discreteMouseScroll().get(),
				value -> minecraft.options.discreteMouseScroll().set(!value), Domain.INPUT_LATENCY);

		this.slider(Component.translatable("options.sensitivity"),
				Component.translatable("kohs_core.option.mouse_sensitivity.description"),
				0.0, 1.0, 0.01, () -> minecraft.options.sensitivity().get(),
				value -> minecraft.options.sensitivity().set(value),
				value -> Component.literal(Math.round(value * 200.0) + "%"), Domain.INPUT_LATENCY);

		this.slider(Component.translatable("options.mouseWheelSensitivity"),
				Component.translatable("kohs_core.option.wheel_sensitivity.description"),
				// Vanilla stores this logarithmically: the real domain is the integer exponent
				// -200..100, mapped through 10^(n/100) to give 0.01x..10x. Sliding linearly over the
				// mapped result crams nine tenths of the useful settings into the first tenth of the
				// track, and rounds most of the upper half onto the same handful of values. Driving
				// the exponent instead gives one slider step per value the option can actually hold.
				-200, 100, 1,
				() -> Math.round(Math.log10(Math.max(0.0001, minecraft.options.mouseWheelSensitivity().get()))
						* 100.0),
				value -> minecraft.options.mouseWheelSensitivity().set(Math.pow(10.0, value / 100.0)),
				value -> Component.literal(String.format(Locale.ROOT, "%.2fx", Math.pow(10.0, value / 100.0))),
				Domain.INPUT_LATENCY);


		this.finishRows();
	}

	private void buildMemoryTab() {
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
		CoreConfig config = ConfigStore.get();

		this.toggle(Component.translatable("kohs_core.option.memory_enabled"),
				Component.translatable("kohs_core.option.memory_enabled.description"),
				() -> config.memoryManagementEnabled, value -> config.memoryManagementEnabled = value,
				Domain.MEMORY);

		this.toggle(Component.translatable("kohs_core.option.adaptive_budget"),
				Component.translatable("kohs_core.option.adaptive_memory.description"),
				() -> config.particleAdaptiveBudget, value -> config.particleAdaptiveBudget = value,
				Domain.MEMORY);

		this.slider(Component.translatable("kohs_core.option.heap_threshold"),
				Component.translatable("kohs_core.option.heap_threshold.description"),
				0.5, 0.99, 0.01, () -> config.heapPressureThreshold,
				value -> config.heapPressureThreshold = value.floatValue(),
				value -> Component.literal(String.format(Locale.ROOT, "%.0f%%", value * 100.0)), Domain.MEMORY);

		this.slider(Component.translatable("kohs_core.option.particle_budget"),
				Component.translatable("kohs_core.option.memory_particle_ceiling.description"),
				0, 10_000, 100, () -> config.particleAllowancePerSecond,
				value -> config.particleAllowancePerSecond = (int) Math.round(value),
				value -> value <= 0 ? Component.translatable("kohs_core.value.adaptive_unlimited")
						: Component.literal(Math.round(value) + "/s"), Domain.PARTICLES);

		this.finishRows();
	}

	private void buildSiblingOptions(CoreSettingsPage page) {
		this.rowCursor = 0;
		this.rowCursorY = this.contentY;
		for (CoreOption option : page.options()) {
			switch (option) {
			case CoreOption.Toggle toggle -> this.addRow(new CoreToggle(this.contentX, this.nextRowY(),
						this.contentWidth, this.rowHeight, toggle.title(), toggle.description(), toggle.getter(),
						toggle.setter()));
			case CoreOption.Slider slider -> this.addRow(new CoreSlider(this.contentX,
						this.nextRowY(this.sliderRowHeight), this.contentWidth, this.sliderRowHeight,
						slider.title(), slider.description(), slider.min(),
						slider.max(), slider.step(), slider.getter(), slider.setter(), slider.formatter()));
			case CoreOption.Choice choice -> this.addRow(new CoreSlider(this.contentX,
						this.nextRowY(this.sliderRowHeight), this.contentWidth, this.sliderRowHeight,
						choice.title(), choice.description(), 0,
						Math.max(0, choice.labels().size() - 1), 1, () -> choice.getter().getAsInt(),
						value -> choice.setter().accept((int) Math.round(value)),
						value -> {
							int index = Mth.clamp((int) Math.round(value), 0, choice.labels().size() - 1);
							return choice.labels().isEmpty() ? Component.empty() : choice.labels().get(index);
						}));
			case CoreOption.Action action -> {
				Button button = Button.builder(action.title(), pressed -> action.onPress().run())
							.bounds(this.contentX, this.nextRowY(), this.contentWidth, this.rowHeight).build();
				this.scrollingWidgets.add(button);
				((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(button);
				}
			}
		}
		this.finishRows();
	}

	private void buildFooter() {
		int innerWidth = Math.max(1, this.width - this.margin * 2);
		int buttonGap = Math.min(4, Math.max(1, innerWidth / 48));
		int availableForButtons = Math.max(2, innerWidth - buttonGap);
		int doneMinimum = Math.min(56, availableForButtons / 2);
		int doneWidth = Math.min(100, Math.max(doneMinimum, availableForButtons / 3));
		int vanillaWidth = Math.max(1, Math.min(118, availableForButtons - doneWidth));

		Button vanilla = Button
				.builder(Component.translatable("kohs_core.button.vanilla"),
						pressed -> KoHsCoreClient.openVanillaVideoSettings(this))
				.bounds(this.margin, this.footerY, vanillaWidth, this.footerButtonHeight).build();
		vanilla.setTooltip(net.minecraft.client.gui.components.Tooltip
				.create(Component.translatable("kohs_core.button.vanilla.tooltip")));
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(vanilla);

		Button done = Button.builder(net.minecraft.network.chat.CommonComponents.GUI_DONE,
				pressed -> this.onClose())
				.bounds(Math.max(this.margin, this.width - this.margin - doneWidth), this.footerY,
						doneWidth, this.footerButtonHeight)
				.build();
		((ScreenInvoker) (Object) this).kohsCore$addRenderableWidget(done);
	}

	@Override
	protected void rebuildWidgets() {
		this.clearWidgets();
		this.init();
	}

	// --- Rendering ---------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Vanilla's own blur, so the world behind is softened without any custom shader work.
		graphics.blurBeforeThisStratum();
		graphics.fillGradient(0, 0, this.width, this.height, CoreTheme.BACKDROP_TOP, CoreTheme.BACKDROP_BOTTOM);
		if (ConfigStore.get().guiParticles) {
			this.particleField.render(graphics, this.width, this.height, mouseX, mouseY);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float entrance = CoreTheme.cubicOut(
				CoreTheme.ease(System.nanoTime() / 1_000_000L - this.openedAtMillis, 280L));

		this.renderHeader(graphics);
		this.renderRailPanel(graphics);
		this.renderContentPanel(graphics, mouseX, mouseY);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		this.renderRailOverlay(graphics);
		this.renderDescriptionPanel(graphics, mouseX, mouseY);

		if (entrance < 1.0F) {
			// A brief veil on open, so the screen arrives rather than snapping into place.
			graphics.fill(0, 0, this.width, this.height,
					CoreTheme.withAlpha(0x120824, Math.round((1.0F - entrance) * 110.0F)));
		}
	}

	private void renderHeader(GuiGraphicsExtractor graphics) {
		int panelWidth = Math.max(1, this.width - this.margin * 2);
		CoreDraw.roundedRect(graphics, this.margin, this.headerY, panelWidth, this.headerHeight, 5,
				CoreTheme.PANEL);

		// Live numbers in the corner, so the effect of a change is visible without leaving.
		int fps = KoHsCoreClient.framesPerSecond();
		long usedMib = MemoryGovernor.usedHeapMib();
		long maxMib = MemoryGovernor.maxHeapMib();
		Component stats = Component.literal(fps + " FPS   " + usedMib + " / " + maxMib + " MiB");
		int statsWidth = this.font.width(stats);
		int titleX = this.margin + Math.min(10, Math.max(4, panelWidth / 20));
		int textY = this.headerY + Math.max(1, (this.headerHeight - this.font.lineHeight) / 2);
		int right = this.margin + panelWidth - Math.min(10, Math.max(4, panelWidth / 20));
		int titleWidth = this.font.width(this.title);
		if (titleWidth + statsWidth + 16 > right - titleX) {
			stats = Component.literal(fps + " FPS");
			statsWidth = this.font.width(stats);
		}
		boolean showStats = titleWidth + statsWidth + 10 <= right - titleX;
		int titleMaxWidth = showStats ? Math.max(1, right - statsWidth - 8 - titleX)
				: Math.max(1, right - titleX);
		CoreDraw.clippedText(graphics, this.font, this.title, titleX, textY, titleMaxWidth, CoreTheme.TEXT);

		int statsColor = switch (KoHsCoreClient.pressure()) {
			case RELAXED -> CoreTheme.GOOD;
			case ELEVATED -> CoreTheme.TEXT_MUTED;
			case HIGH -> CoreTheme.WARN;
			case CRITICAL -> CoreTheme.BAD;
		};
		if (showStats) {
			graphics.text(this.font, stats, right - statsWidth, textY, statsColor);
		}
	}

	private void renderRailPanel(GuiGraphicsExtractor graphics) {
		this.layoutRailButtons();
		CoreDraw.roundedRect(graphics, Math.max(0, this.railX - 2), Math.max(0, this.railY - 2),
				Math.max(1, this.railWidth + 4), Math.max(1, this.railViewportHeight + 4), 5,
				CoreTheme.withAlpha(CoreTheme.PANEL_DEEP, 150));
	}

	private void renderRailOverlay(GuiGraphicsExtractor graphics) {
		if (this.railContentHeight <= this.railViewportHeight) {
			return;
		}
		int buttonAreaWidth = Math.max(1, this.railWidth - 5);
		CoreDraw.scrollFade(graphics, this.railX, this.railY, buttonAreaWidth, this.railViewportHeight,
				this.railScroll > 1.0,
				this.railScroll < this.railContentHeight - this.railViewportHeight - 1);
		CoreDraw.scrollbar(graphics, this.railX + this.railWidth - 3, this.railY,
				this.railViewportHeight, this.railContentHeight, this.railViewportHeight, this.railScroll);
	}

	private void renderContentPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.siblingPage == null && this.tab == CoreTab.PARTICLES) {
			this.particlePane.render(graphics, mouseX, mouseY);
			return;
		}
		if (this.siblingPage == null && this.tab == CoreTab.JVM) {
			this.renderJvmPane(graphics);
			return;
		}
		if (this.siblingPage == null && this.tab == CoreTab.INTEGRATION) {
			this.renderIntegrationPane(graphics);
			return;
		}

		// Scrolling column: reposition the rows and hide the ones outside the viewport, so widgets
		// never take clicks from outside the visible area.
		this.clampContentScroll();
		int offset = (int) Math.round(this.contentScroll);
		int top = this.contentY;
		int bottom = this.contentY + this.contentViewportHeight;
		this.contentHeight = this.optionTree.layout(graphics, this.scrollingWidgets, this.contentX,
				top, this.contentWidth, this.contentViewportHeight, this.rowGap, this.contentScroll);
		this.clampContentScroll();

		CoreDraw.scrollFade(graphics, this.contentX, top, this.contentWidth, this.contentViewportHeight,
				this.contentScroll > 1.0, this.contentScroll < this.contentHeight - this.contentViewportHeight - 1);
		CoreDraw.scrollbar(graphics, this.contentX + this.contentWidth + 2, top, this.contentViewportHeight,
				this.contentHeight, this.contentViewportHeight, this.contentScroll);
	}

	private void renderJvmPane(GuiGraphicsExtractor graphics) {
		HardwareProbe probe = HardwareProbe.get();
		int x = this.contentX;
		int y = this.contentY - (int) Math.round(this.contentScroll);
		int lineHeight = this.font.lineHeight + 2;

		graphics.enableScissor(this.contentX - 4, this.contentY, this.contentX + this.contentWidth + 4,
				this.contentY + this.contentViewportHeight);

		// A probe landing mid-session changes every number below, so rebuild before measuring.
		this.refreshJvmProfiles();

		if (!HardwareProbe.isReady()) {
			CoreDraw.clippedText(graphics, this.font, Component.translatable("kohs_core.jvm.probing"), x, y,
					this.contentWidth, CoreTheme.TEXT_DIM);
			y += lineHeight;
		}

		CoreDraw.clippedText(graphics, this.font, Component.translatable("kohs_core.jvm.machine"), x, y,
				this.contentWidth, CoreTheme.ACCENT_BRIGHT);
		y += lineHeight + 2;

		y = this.line(graphics, x, y, "CPU", probe.cpuName() + "  ("
				+ probe.physicalCores() + "c / " + probe.logicalCores() + "t)");
		y = this.line(graphics, x, y, "GPU", probe.gpuName()
				+ (probe.gpuVramGib() > 0.0 ? String.format(Locale.ROOT, "  (%.0f GiB)", probe.gpuVramGib()) : ""));
		y = this.line(graphics, x, y, "RAM", String.format(Locale.ROOT, "%.1f GiB installed, %.1f GiB free",
				probe.totalMemoryGib(), probe.availableMemoryGib()));
		y = this.line(graphics, x, y, "Display", probe.refreshRateHz() + " Hz");
		y = this.line(graphics, x, y, "Java", probe.javaVersion() + "  " + probe.javaVendor());
		y = this.line(graphics, x, y, "Now", String.format(Locale.ROOT, "%.1f GiB heap, %s collector",
				probe.currentMaxHeapGib(), probe.currentCollector()));

		y += 6;
		CoreDraw.clippedText(graphics, this.font, Component.translatable("kohs_core.jvm.pacing"), x, y,
				this.contentWidth, CoreTheme.ACCENT_BRIGHT);
		y += lineHeight;
		y = this.wrapped(graphics, x, y, JvmAdvisor.framePacingAdvice(probe), CoreTheme.TEXT_MUTED);

		int viewportTop = this.contentY;
		int viewportBottom = this.contentY + this.contentViewportHeight;
		boolean roomForCopyButton = this.contentWidth >= MIN_WIDTH_FOR_COPY_BUTTON;

		for (int index = 0; index < this.jvmProfiles.size(); index++) {
			JvmProfile profile = this.jvmProfiles.get(index);
			y += 10;

			// The heading reserves the button's column so the two can never overlap, and the button
			// is dropped entirely rather than crushed when the pane is too narrow to hold both.
			int headingWidth = roomForCopyButton
					? Math.max(1, this.contentWidth - COPY_BUTTON_WIDTH - 10)
					: this.contentWidth;
			CoreDraw.clippedText(graphics, this.font,
					Component.translatable(profile.goal().translationKey())
							.append(Component.literal("   " + profile.heapGib() + " GiB, " + profile.collector())),
					x, y, headingWidth, CoreTheme.ACCENT_BRIGHT);

			if (index < this.jvmCopyButtons.size()) {
				CopyButton button = this.jvmCopyButtons.get(index);
				int buttonY = y - 3;
				button.setX(this.contentX + this.contentWidth - COPY_BUTTON_WIDTH - 2);
				button.setY(buttonY);
				// Hide rather than clip: a half-scrolled button would still take clicks.
				button.visible = roomForCopyButton && buttonY >= viewportTop
						&& buttonY + COPY_BUTTON_HEIGHT <= viewportBottom;
			}
			y += lineHeight + 2;

			// A panel behind the flags marks them as the thing to take, and separates them from the
			// prose underneath.
			int flagsWidth = Math.max(1, this.contentWidth - 8);
			int flagLines = Math.max(1,
					this.font.split(Component.literal(profile.commandLine()), flagsWidth - 8).size());
			int flagsHeight = flagLines * (this.font.lineHeight + 1) + 7;
			CoreDraw.roundedRect(graphics, x, y - 3, flagsWidth, flagsHeight, 3,
					CoreTheme.withAlpha(CoreTheme.PANEL_DEEP, 190));
			int afterFlags = this.wrappedWithin(graphics, x + 4, y + 1, flagsWidth - 8, profile.commandLine(),
					CoreTheme.TEXT);
			y = Math.max(afterFlags, y - 3 + flagsHeight) + 3;

			for (String reason : profile.rationale()) {
				y = this.wrapped(graphics, x + 6, y, reason, CoreTheme.TEXT_DIM);
			}
			for (String warning : profile.warnings()) {
				y = this.wrapped(graphics, x + 6, y, "! " + warning, CoreTheme.WARN);
			}
		}

		y += 8;
		y = this.wrapped(graphics, x, y,
				Component.translatable("kohs_core.jvm.disclaimer").getString(), CoreTheme.TEXT_DIM);

		graphics.disableScissor();

		this.contentHeight = y + (int) Math.round(this.contentScroll) - this.contentY;
		this.clampContentScroll();
		CoreDraw.scrollbar(graphics, this.contentX + this.contentWidth + 2, this.contentY,
				this.contentViewportHeight, this.contentHeight, this.contentViewportHeight, this.contentScroll);
	}

	private void renderIntegrationPane(GuiGraphicsExtractor graphics) {
		int x = this.contentX;
		int y = this.contentY - (int) Math.round(this.contentScroll);
		int lineHeight = this.font.lineHeight + 2;

		graphics.enableScissor(this.contentX - 4, this.contentY, this.contentX + this.contentWidth + 4,
				this.contentY + this.contentViewportHeight);

		CoreDraw.clippedText(graphics, this.font, Component.translatable("kohs_core.integration.header"), x, y,
				this.contentWidth, CoreTheme.ACCENT_BRIGHT);
		y += lineHeight + 3;

		for (Domain domain : Domain.values()) {
			List<DomainClaim> owners = KoHsCoreClient.domainOwners(domain);
			Component name = Component.translatable(domain.translationKey());
			Component owner;
			int color;
			if (owners.isEmpty()) {
				owner = Component.translatable("kohs_core.integration.core");
				color = CoreTheme.GOOD;
			} else {
				owner = Component.literal(String.join(" + ", owners.stream()
						.map(DomainClaim::ownerName).distinct().toList()));
				color = CoreTheme.WARN;
			}
			if (this.contentWidth < 250) {
				CoreDraw.clippedText(graphics, this.font, name, x, y, this.contentWidth, CoreTheme.TEXT);
				y += lineHeight;
				CoreDraw.clippedText(graphics, this.font, owner, x + 6, y,
						Math.max(1, this.contentWidth - 6), color);
				y += lineHeight + 2;
			} else {
				int ownerWidth = Math.min(this.font.width(owner), Math.max(1, this.contentWidth / 2));
				CoreDraw.clippedText(graphics, this.font, name, x, y,
						Math.max(1, this.contentWidth - ownerWidth - 12), CoreTheme.TEXT);
				CoreDraw.clippedText(graphics, this.font, owner,
						x + this.contentWidth - ownerWidth - 6, y, ownerWidth, color);
				y += lineHeight;
			}
		}

		y += 8;
		y = this.wrapped(graphics, x, y, Component.translatable("kohs_core.integration.explain").getString(),
				CoreTheme.TEXT_DIM);

		graphics.disableScissor();
		this.contentHeight = y + (int) Math.round(this.contentScroll) - this.contentY;
		this.clampContentScroll();
	}

	private int line(GuiGraphicsExtractor graphics, int x, int y, String label, String value) {
		int labelColumn = Mth.clamp(this.contentWidth / 4, 34, 58);
		CoreDraw.clippedText(graphics, this.font, Component.literal(label), x, y,
				Math.max(1, labelColumn - 4), CoreTheme.TEXT_DIM);
		CoreDraw.clippedText(graphics, this.font, Component.literal(value), x + labelColumn, y,
				Math.max(1, this.contentWidth - labelColumn - 4), CoreTheme.TEXT);
		return y + this.font.lineHeight + 2;
	}

	private int wrapped(GuiGraphicsExtractor graphics, int x, int y, String text, int color) {
		return this.wrappedWithin(graphics, x, y,
				Math.max(1, this.contentWidth - (x - this.contentX) - 8), text, color);
	}

	/** Word-wraps into an explicit width, for text inside a box narrower than the pane. */
	private int wrappedWithin(GuiGraphicsExtractor graphics, int x, int y, int wrapWidth, String text, int color) {
		int cursor = y;
		for (var line : this.font.split(Component.literal(text), Math.max(1, wrapWidth))) {
			graphics.text(this.font, line, x, cursor, color);
			cursor += this.font.lineHeight + 1;
		}
		return cursor;
	}

	private void renderDescriptionPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.descriptionHeight <= 0) {
			this.typewriter.clear();
			return;
		}
		int panelWidth = Math.max(1, this.width - this.margin * 2);
		// A reading surface, not a framed box: a soft fill over the blur plus one hairline to
		// separate it from the content above. The border and drop shadow this used to have made a
		// panel of text look like a cell in a table.
		CoreDraw.roundedRect(graphics, this.margin, this.descriptionY, panelWidth, this.descriptionHeight, 4,
				CoreTheme.withAlpha(CoreTheme.PANEL_DEEP, 170));
		CoreDraw.divider(graphics, this.margin + 6, this.descriptionY, panelWidth - 12, CoreTheme.HAIRLINE);
		int padding = Mth.clamp(this.descriptionHeight / 6, 4, 8);
		int textX = this.margin + padding;
		int textY = this.descriptionY + padding;
		int textWidth = Math.max(1, panelWidth - padding * 2);
		graphics.enableScissor(this.margin + 1, this.descriptionY + 1,
				this.margin + panelWidth - 1, this.descriptionY + this.descriptionHeight - 1);

		Component description = this.currentDescription(mouseX, mouseY);
		if (description == null) {
			this.typewriter.clear();
			CoreDraw.clippedText(graphics, this.font, Component.translatable("kohs_core.description.idle"),
					textX, textY, textWidth, CoreTheme.TEXT_DIM);
			graphics.disableScissor();
			return;
		}

		this.typewriter.setText(description);
		int textHeight = Math.max(this.font.lineHeight, this.descriptionHeight - padding * 2);
		this.typewriter.render(graphics, this.font, textX, textY, textWidth, textHeight,
				CoreTheme.TEXT_MUTED);
		graphics.disableScissor();
	}

	private void clampRailScroll() {
		int maxScroll = Math.max(0, this.railContentHeight - this.railViewportHeight);
		this.railScroll = Mth.clamp(this.railScroll, 0.0, maxScroll);
	}

	private void clampContentScroll() {
		int maxScroll = Math.max(0, this.contentHeight - this.contentViewportHeight);
		this.contentScroll = Mth.clamp(this.contentScroll, 0.0, maxScroll);
	}

	private void clampScrolls() {
		this.clampRailScroll();
		this.clampContentScroll();
	}

	/** The description for whatever the cursor is currently over. */
	private @Nullable Component currentDescription(int mouseX, int mouseY) {
		if (this.siblingPage == null && this.tab == CoreTab.PARTICLES) {
			ParticleCatalog.Entry hovered = this.particlePane.hoveredEntry();
			if (hovered != null) {
				var reservation = this.particlePane.hoveredReservation();
				if (reservation != null) {
					return Component.translatable("kohs_core.locked.description",
							Component.literal(reservation.ownerName())).append(" ")
							.append(Component.translatable(reservation.reasonKey()));
				}
				return Component.translatable("kohs_core.particles.hover", hovered.displayName(),
						Component.literal(hovered.id()));
			}
		}
		for (CoreControl control : this.controls) {
			if (control.visible && control.isMouseOver(mouseX, mouseY)) {
				if (control.isLocked()) {
					return Component.translatable("kohs_core.locked.description", control.lockOwner());
				}
				Component description = control.description();
				return description.getString().isEmpty() ? null : description;
			}
		}
		return null;
	}

	// --- Input -------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		KoHsCoreClient.noteInput();
		if (this.siblingPage == null && this.tab == CoreTab.PARTICLES
				&& this.particlePane.mouseClicked(event.x(), event.y())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		KoHsCoreClient.noteInput();
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= this.railX - 2 && mouseX <= this.railX + this.railWidth + 2
				&& mouseY >= this.railY && mouseY <= this.railY + this.railViewportHeight
				&& this.railContentHeight > this.railViewportHeight) {
			this.railScroll -= scrollY * Math.max(12, this.railButtonHeight + this.railGap);
			this.clampRailScroll();
			this.layoutRailButtons();
			return true;
		}
		if (this.siblingPage == null && this.tab == CoreTab.PARTICLES
				&& this.particlePane.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		int maxScroll = Math.max(0, this.contentHeight - this.contentViewportHeight);
		if (maxScroll > 0 && mouseX >= this.contentX - 4
				&& mouseX <= this.contentX + this.contentWidth + 4
				&& mouseY >= this.contentY && mouseY <= this.contentY + this.contentViewportHeight) {
			this.contentScroll = Mth.clamp(this.contentScroll - scrollY * 22.0, 0.0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		this.particleField.resize(width, height);
		this.clampScrolls();
	}

	@Override
	public void onClose() {
		ConfigStore.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		KoHsCoreClient.graphicsGovernor().flushTextureChanges();
		Minecraft.getInstance().getWindow().changeFullscreenVideoMode();
		Minecraft.getInstance().options.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Opens the real vanilla screen, for anything Core does not surface. */
	public static Screen vanillaScreen(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		return new VideoSettingsScreen(parent, minecraft, minecraft.options);
	}
}
